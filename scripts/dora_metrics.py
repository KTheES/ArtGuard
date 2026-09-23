#!/usr/bin/env python3
"""Collect DORA metrics from GitHub and render JSON, Markdown, and SVG outputs.

Data conventions:
- Deployments: terminal GitHub Deployment statuses in production-like environments.
- Lead time: PR merged_at -> first successful deployment with the same merge SHA.
- MTTR: average created_at -> closed_at for closed issues carrying the incident label.
- Change failure rate: failed/error deployments / terminal deployments.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import statistics
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from html import escape
from pathlib import Path
from typing import Any, Iterable


UTC = timezone.utc
TERMINAL_STATES = {"success", "failure", "error"}


def parse_time(value: str | None) -> datetime | None:
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(UTC)


def iso(value: datetime) -> str:
    return value.astimezone(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def percentile(values: list[float], percentile_value: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * percentile_value
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def rounded(value: float | None, digits: int = 2) -> float | None:
    return None if value is None else round(value, digits)


class GitHubClient:
    def __init__(self, token: str, api_url: str, api_version: str) -> None:
        self.token = token
        self.api_url = api_url.rstrip("/")
        self.api_version = api_version

    def get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        query = urllib.parse.urlencode(params or {})
        url = f"{self.api_url}{path}" + (f"?{query}" if query else "")
        request = urllib.request.Request(
            url,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": self.api_version,
                "User-Agent": "artworkguard-dora-collector",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"GitHub API {error.code} for {url}: {detail}") from error

    def paginate(
        self,
        path: str,
        params: dict[str, Any] | None = None,
        max_pages: int = 20,
    ) -> list[dict[str, Any]]:
        items: list[dict[str, Any]] = []
        base = dict(params or {})
        base["per_page"] = 100
        for page in range(1, max_pages + 1):
            payload = self.get(path, {**base, "page": page})
            if not isinstance(payload, list):
                raise RuntimeError(f"Expected a list from {path}")
            items.extend(payload)
            if len(payload) < 100:
                break
        return items


def collect_github_data(
    client: GitHubClient,
    repository: str,
    start: datetime,
    incident_label: str,
) -> dict[str, Any]:
    deployments = client.paginate(f"/repos/{repository}/deployments")
    enriched: list[dict[str, Any]] = []
    for deployment in deployments:
        created_at = parse_time(deployment.get("created_at"))
        if created_at and created_at < start - timedelta(days=7):
            continue
        statuses = client.paginate(
            f"/repos/{repository}/deployments/{deployment['id']}/statuses",
            max_pages=3,
        )
        terminal = next(
            (status for status in statuses if status.get("state") in TERMINAL_STATES),
            None,
        )
        enriched.append(
            {
                **deployment,
                "final_state": terminal.get("state") if terminal else None,
                "completed_at": terminal.get("created_at") if terminal else None,
            }
        )

    pulls = client.paginate(
        f"/repos/{repository}/pulls",
        {"state": "closed", "sort": "updated", "direction": "desc"},
    )
    pulls = [pull for pull in pulls if pull.get("merged_at")]

    issues = client.paginate(
        f"/repos/{repository}/issues",
        {"state": "closed", "labels": incident_label, "since": iso(start)},
    )
    issues = [issue for issue in issues if "pull_request" not in issue]
    return {"deployments": enriched, "pulls": pulls, "issues": issues}


def week_start(value: datetime) -> datetime:
    base = value - timedelta(days=value.weekday())
    return base.replace(hour=0, minute=0, second=0, microsecond=0)


def build_metrics(
    raw: dict[str, Any],
    repository: str,
    start: datetime,
    end: datetime,
    environment_pattern: str,
    incident_label: str,
    mode: str,
) -> dict[str, Any]:
    environment_re = re.compile(environment_pattern, re.IGNORECASE)
    deployments: list[dict[str, Any]] = []
    for deployment in raw.get("deployments", []):
        completed = parse_time(deployment.get("completed_at"))
        environment = str(deployment.get("environment") or "")
        state = deployment.get("final_state")
        if (
            completed
            and start <= completed <= end
            and environment_re.search(environment)
            and state in TERMINAL_STATES
        ):
            deployments.append({**deployment, "_completed": completed})

    successful = [item for item in deployments if item.get("final_state") == "success"]
    failed = [item for item in deployments if item.get("final_state") in {"failure", "error"}]

    pull_by_sha: dict[str, dict[str, Any]] = {}
    for pull in raw.get("pulls", []):
        merged = parse_time(pull.get("merged_at"))
        sha = pull.get("merge_commit_sha")
        if merged and sha:
            pull_by_sha[str(sha)] = {**pull, "_merged": merged}

    first_deployment_by_pr: dict[int, tuple[dict[str, Any], dict[str, Any]]] = {}
    for deployment in sorted(successful, key=lambda item: item["_completed"]):
        pull = pull_by_sha.get(str(deployment.get("sha") or ""))
        if not pull or pull["_merged"] > deployment["_completed"]:
            continue
        number = int(pull.get("number", 0))
        first_deployment_by_pr.setdefault(number, (pull, deployment))

    lead_samples: list[dict[str, Any]] = []
    for number, (pull, deployment) in first_deployment_by_pr.items():
        hours = (deployment["_completed"] - pull["_merged"]).total_seconds() / 3600
        lead_samples.append(
            {
                "pull_request": number,
                "merge_sha": pull.get("merge_commit_sha"),
                "merged_at": iso(pull["_merged"]),
                "deployed_at": iso(deployment["_completed"]),
                "hours": rounded(hours),
            }
        )
    lead_hours = [float(item["hours"]) for item in lead_samples]

    incidents: list[dict[str, Any]] = []
    for issue in raw.get("issues", []):
        opened = parse_time(issue.get("created_at"))
        closed = parse_time(issue.get("closed_at"))
        if not opened or not closed or not (start <= closed <= end):
            continue
        hours = (closed - opened).total_seconds() / 3600
        incidents.append(
            {
                "issue": issue.get("number"),
                "title": issue.get("title"),
                "opened_at": iso(opened),
                "restored_at": iso(closed),
                "hours": rounded(hours),
            }
        )
    recovery_hours = [float(item["hours"]) for item in incidents]

    observation_days = max((end - start).total_seconds() / 86400, 1)
    terminal_count = len(successful) + len(failed)
    series_buckets: dict[str, dict[str, Any]] = defaultdict(
        lambda: {
            "successful_deployments": 0,
            "failed_deployments": 0,
            "lead_time_hours": [],
            "incident_recovery_hours": [],
        }
    )
    cursor = week_start(start)
    while cursor <= end:
        series_buckets[cursor.date().isoformat()]
        cursor += timedelta(days=7)

    for deployment in successful:
        key = week_start(deployment["_completed"]).date().isoformat()
        series_buckets[key]["successful_deployments"] += 1
    for deployment in failed:
        key = week_start(deployment["_completed"]).date().isoformat()
        series_buckets[key]["failed_deployments"] += 1
    for sample in lead_samples:
        key = week_start(parse_time(sample["deployed_at"])).date().isoformat()
        series_buckets[key]["lead_time_hours"].append(sample["hours"])
    for incident in incidents:
        key = week_start(parse_time(incident["restored_at"])).date().isoformat()
        series_buckets[key]["incident_recovery_hours"].append(incident["hours"])

    series = []
    for key in sorted(series_buckets):
        bucket = series_buckets[key]
        lead_values = bucket.pop("lead_time_hours")
        recovery_values = bucket.pop("incident_recovery_hours")
        attempts = bucket["successful_deployments"] + bucket["failed_deployments"]
        series.append(
            {
                "week_start": key,
                **bucket,
                "lead_time_median_hours": rounded(statistics.median(lead_values)) if lead_values else None,
                "mttr_mean_hours": rounded(statistics.mean(recovery_values)) if recovery_values else None,
                "change_failure_rate_percent": rounded(bucket["failed_deployments"] / attempts * 100)
                if attempts
                else None,
            }
        )

    return {
        "schema_version": 1,
        "generated_at": iso(end),
        "repository": repository,
        "window": {
            "start": iso(start),
            "end": iso(end),
            "days": round(observation_days, 2),
        },
        "configuration": {
            "environment_regex": environment_pattern,
            "incident_label": incident_label,
            "lead_time_matching": "pull_request.merge_commit_sha == deployment.sha",
        },
        "source": {
            "mode": mode,
            "deployment_provider": "github_deployments",
            "incident_provider": "github_issues",
        },
        "metrics": {
            "lead_time_for_changes": {
                "median_hours": rounded(statistics.median(lead_hours)) if lead_hours else None,
                "p90_hours": rounded(percentile(lead_hours, 0.9)),
                "sample_size": len(lead_hours),
            },
            "deployment_frequency": {
                "successful_deployments": len(successful),
                "per_week": rounded(len(successful) / observation_days * 7),
            },
            "mean_time_to_restore": {
                "mean_hours": rounded(statistics.mean(recovery_hours)) if recovery_hours else None,
                "median_hours": rounded(statistics.median(recovery_hours)) if recovery_hours else None,
                "incident_count": len(recovery_hours),
            },
            "change_failure_rate": {
                "percent": rounded(len(failed) / terminal_count * 100) if terminal_count else None,
                "failed_deployments": len(failed),
                "terminal_deployments": terminal_count,
            },
        },
        "quality": {
            "unmatched_successful_deployments": len(successful) - len(lead_samples),
            "notes": [
                "Lead Time은 deployment.sha와 Pull Request merge_commit_sha가 일치해야 계산됩니다.",
                "MTTR은 설정된 incident 라벨의 종료 이슈를 복구 기록으로 사용합니다.",
                "Change Failure Rate는 failure/error 상태의 운영 배포를 변경 실패의 대리값으로 사용합니다.",
            ],
        },
        "samples": {
            "lead_time": sorted(lead_samples, key=lambda item: item["deployed_at"]),
            "incidents": sorted(incidents, key=lambda item: item["restored_at"]),
        },
        "series": series,
    }


def display(value: float | int | None, suffix: str = "") -> str:
    return "N/A" if value is None else f"{value:g}{suffix}"


def render_report(metrics: dict[str, Any]) -> str:
    values = metrics["metrics"]
    sample = metrics["source"]["mode"] == "sample"
    title_suffix = " — SAMPLE DATA" if sample else ""
    lead = values["lead_time_for_changes"]
    frequency = values["deployment_frequency"]
    mttr = values["mean_time_to_restore"]
    cfr = values["change_failure_rate"]
    rows = [
        ("Lead Time for Changes", display(lead["median_hours"], "h"), f"n={lead['sample_size']}, p90={display(lead['p90_hours'], 'h')}", "낮을수록 좋음"),
        ("Deployment Frequency", display(frequency["per_week"], "/week"), f"성공 배포 {frequency['successful_deployments']}건", "높을수록 좋음"),
        ("Mean Time to Restore", display(mttr["mean_hours"], "h"), f"incident {mttr['incident_count']}건", "낮을수록 좋음"),
        ("Change Failure Rate", display(cfr["percent"], "%"), f"실패 {cfr['failed_deployments']}/{cfr['terminal_deployments']}", "낮을수록 좋음"),
    ]
    lines = [
        f"# DORA 주간 보고서{title_suffix}",
        "",
        f"- 저장소: `{metrics['repository']}`",
        f"- 생성 시각: `{metrics['generated_at']}`",
        f"- 관측 구간: `{metrics['window']['start']}` ~ `{metrics['window']['end']}`",
        f"- 환경 정규식: `{metrics['configuration']['environment_regex']}`",
        f"- Incident 라벨: `{metrics['configuration']['incident_label']}`",
        "",
    ]
    if sample:
        lines.extend([
            "> [!WARNING]",
            "> 이 보고서는 대시보드 검증용 샘플 데이터입니다. GitHub Actions가 실행되면 실측 결과로 교체됩니다.",
            "",
        ])
    lines.extend([
        "## DORA 4대 지표",
        "",
        "| 지표 | 값 | 표본 | 해석 |",
        "| --- | ---: | --- | --- |",
    ])
    lines.extend(f"| {name} | {value} | {sample_text} | {direction} |" for name, value, sample_text, direction in rows)
    lines.extend([
        "",
        "## 주간 추이",
        "",
        "| 주 시작 | 성공 배포 | 실패 배포 | Lead Time 중앙값(h) | MTTR 평균(h) | CFR(%) |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ])
    for item in metrics["series"]:
        lines.append(
            "| {week_start} | {successful_deployments} | {failed_deployments} | {lead} | {mttr} | {cfr} |".format(
                **item,
                lead=display(item["lead_time_median_hours"]),
                mttr=display(item["mttr_mean_hours"]),
                cfr=display(item["change_failure_rate_percent"]),
            )
        )
    lines.extend([
        "",
        "## 데이터 품질",
        "",
        f"- Lead Time과 연결되지 않은 성공 배포: **{metrics['quality']['unmatched_successful_deployments']}건**",
        f"- Lead Time 연결 기준: `{metrics['configuration']['lead_time_matching']}`",
    ])
    lines.extend(f"- {note}" for note in metrics["quality"]["notes"])
    lines.extend([
        "",
        "## 운영 규칙",
        "",
        "- 운영 배포 워크플로우는 GitHub Deployment와 terminal deployment status를 생성해야 합니다.",
        "- 장애 이슈에는 설정된 incident 라벨을 붙이고 복구 완료 시 이슈를 닫습니다.",
        "- 배포 롤백·핫픽스가 실패 배포와 연결되지 않으면 CFR이 실제보다 낮게 보일 수 있습니다.",
        "- 표본이 없을 때는 0이 아니라 `N/A`로 표시합니다.",
        "",
    ])
    return "\n".join(lines)


def render_svg(metrics: dict[str, Any]) -> str:
    values = metrics["metrics"]
    sample = metrics["source"]["mode"] == "sample"
    cards = [
        ("Lead Time", display(values["lead_time_for_changes"]["median_hours"], "h"), "PR merge → production"),
        ("Deploy Frequency", display(values["deployment_frequency"]["per_week"], "/week"), "successful production deploys"),
        ("MTTR", display(values["mean_time_to_restore"]["mean_hours"], "h"), "incident open → close"),
        ("Change Failure Rate", display(values["change_failure_rate"]["percent"], "%"), "failed / terminal deploys"),
    ]
    card_markup = []
    for index, (label, value, detail) in enumerate(cards):
        x = 48 + index * 286
        card_markup.append(
            f'<rect x="{x}" y="138" width="262" height="150" rx="18" fill="#121d33" stroke="#29405f"/>'
            f'<text x="{x + 22}" y="176" class="label">{escape(label)}</text>'
            f'<text x="{x + 22}" y="230" class="value">{escape(value)}</text>'
            f'<text x="{x + 22}" y="263" class="detail">{escape(detail)}</text>'
        )

    series = metrics.get("series", [])[-8:]
    chart = []
    max_deploy = max([item["successful_deployments"] + item["failed_deployments"] for item in series] + [1])
    for index, item in enumerate(series):
        x = 92 + index * 138
        success_height = 150 * item["successful_deployments"] / max_deploy
        failed_height = 150 * item["failed_deployments"] / max_deploy
        chart.append(f'<rect x="{x}" y="{540 - success_height:.1f}" width="38" height="{success_height:.1f}" rx="5" fill="#39d98a"/>')
        chart.append(f'<rect x="{x + 42}" y="{540 - failed_height:.1f}" width="20" height="{failed_height:.1f}" rx="4" fill="#ff6b7a"/>')
        chart.append(f'<text x="{x + 30}" y="570" text-anchor="middle" class="axis">{escape(item["week_start"][5:])}</text>')

    badge = '<g><rect x="1080" y="46" width="108" height="34" rx="17" fill="#ffb84d"/><text x="1134" y="69" text-anchor="middle" class="badge">SAMPLE</text></g>' if sample else ""
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1240" height="680" viewBox="0 0 1240 680" role="img" aria-labelledby="title desc">
  <title id="title">ArtworkGuard DORA Metrics Dashboard</title>
  <desc id="desc">Four DORA metric cards and weekly deployment trend.</desc>
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#08111f"/><stop offset="1" stop-color="#101b31"/></linearGradient>
    <style>
      .title{{font:700 30px system-ui,sans-serif;fill:#f4f7fb}} .subtitle{{font:400 14px system-ui,sans-serif;fill:#8fa5c1}}
      .label{{font:600 14px system-ui,sans-serif;fill:#8fa5c1}} .value{{font:700 36px system-ui,sans-serif;fill:#f4f7fb}}
      .detail{{font:400 12px system-ui,sans-serif;fill:#6f86a5}} .axis{{font:400 12px system-ui,sans-serif;fill:#7185a1}}
      .section{{font:600 18px system-ui,sans-serif;fill:#dce6f2}} .badge{{font:700 12px system-ui,sans-serif;fill:#30200a}}
    </style>
  </defs>
  <rect width="1240" height="680" rx="24" fill="url(#bg)"/>
  <circle cx="1190" cy="20" r="180" fill="#177ddc" opacity=".10"/>
  <text x="48" y="66" class="title">ArtworkGuard · DORA Metrics</text>
  <text x="48" y="94" class="subtitle">{escape(metrics['repository'])} · {escape(metrics['window']['start'][:10])} — {escape(metrics['window']['end'][:10])}</text>
  {badge}
  {''.join(card_markup)}
  <text x="48" y="352" class="section">Weekly production deployments</text>
  <line x1="70" y1="540" x2="1185" y2="540" stroke="#29405f"/>
  <line x1="70" y1="390" x2="70" y2="540" stroke="#29405f"/>
  {''.join(chart)}
  <rect x="930" y="350" width="12" height="12" rx="3" fill="#39d98a"/><text x="950" y="361" class="axis">success</text>
  <rect x="1020" y="350" width="12" height="12" rx="3" fill="#ff6b7a"/><text x="1040" y="361" class="axis">failure/error</text>
  <text x="48" y="634" class="subtitle">Generated {escape(metrics['generated_at'])} · Data: GitHub Deployments, Pull Requests, Issues</text>
</svg>'''


def write_text(path: str, content: str) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8", newline="\n")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect and render DORA metrics")
    parser.add_argument("--repo", default=os.getenv("GITHUB_REPOSITORY", "KTheES/ArtGuard"))
    parser.add_argument("--lookback-days", type=int, default=90)
    parser.add_argument("--environment-regex", default=r"^prod(uction)?$")
    parser.add_argument("--incident-label", default="incident")
    parser.add_argument("--fixture", help="Use local sample/raw JSON instead of GitHub API")
    parser.add_argument("--output", default="reports/dora/latest.json")
    parser.add_argument("--report", default="reports/dora/WEEKLY_REPORT.md")
    parser.add_argument("--svg", default="docs/dora-dashboard-preview.svg")
    parser.add_argument("--now", help="Override end time with an ISO-8601 timestamp")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if args.lookback_days < 1 or args.lookback_days > 3650:
        raise SystemExit("--lookback-days must be between 1 and 3650")
    end = parse_time(args.now) if args.now else datetime.now(UTC)
    if end is None:
        raise SystemExit("Invalid --now value")
    start = end - timedelta(days=args.lookback_days)

    if args.fixture:
        raw = json.loads(Path(args.fixture).read_text(encoding="utf-8"))
        mode = "sample"
    else:
        token = os.getenv("GITHUB_TOKEN", "")
        if not token:
            raise SystemExit("GITHUB_TOKEN is required when --fixture is not used")
        client = GitHubClient(
            token,
            os.getenv("GITHUB_API_URL", "https://api.github.com"),
            os.getenv("GITHUB_API_VERSION", "2026-03-10"),
        )
        raw = collect_github_data(client, args.repo, start, args.incident_label)
        mode = "github"

    metrics = build_metrics(
        raw,
        args.repo,
        start,
        end,
        args.environment_regex,
        args.incident_label,
        mode,
    )
    write_text(args.output, json.dumps(metrics, ensure_ascii=False, indent=2) + "\n")
    write_text(args.report, render_report(metrics))
    write_text(args.svg, render_svg(metrics))
    print(
        f"DORA metrics written: {args.output}, {args.report}, {args.svg} "
        f"(mode={mode}, deployments={metrics['metrics']['change_failure_rate']['terminal_deployments']})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
