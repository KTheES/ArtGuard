"""Compare validation thresholds without modifying production configuration."""
import argparse
from datetime import datetime, timezone
import json
import math
from pathlib import Path
from .dataset import load, digest, REQUIRED
from .metrics import metrics


def tune(manifest, score_path, target_precision=.98):
    if not math.isfinite(target_precision) or not 0 < target_precision <= 1:
        raise ValueError("Target precision must be in (0,1]")
    data = load(manifest)
    report = json.loads(Path(score_path).read_text(encoding="utf-8"))
    if report.get("schema") != "artworkguard-evaluation-report-v1":
        raise ValueError("Unsupported score report")
    if report.get("split") != "validation":
        raise ValueError("Only validation scores may be tuned; keep test data locked")
    if report.get("dataset_sha256") != digest(manifest) or report.get("synthetic") is not data["synthetic"]:
        raise ValueError("Dataset identity mismatch")
    metadata = ("model", "model_revision", "preprocessing", "hash_version")
    if any(not isinstance(report.get(k), str) or not report[k] for k in metadata):
        raise ValueError("Model metadata required")
    if report.get("mode") not in {"basic", "advanced"}:
        raise ValueError("Invalid scoring mode")
    if report["mode"] == "advanced" and not report.get("region_scheme"):
        raise ValueError("Advanced region scheme required")
    expected = {r["id"]: r for r in data["pairs"] if r["split"] == "validation"}
    rows = report.get("pairs", [])
    if not expected or not isinstance(rows, list) or len(rows) != len(expected):
        raise ValueError("Incomplete validation scores")
    seen = set()
    for row in rows:
        key = row.get("id")
        if key not in expected or key in seen:
            raise ValueError("Unexpected or duplicate score ID")
        seen.add(key)
        if row.get("label") is not expected[key]["label"] or row.get("category") != expected[key]["category"]:
            raise ValueError("Score label/category mismatch")
        score = row.get("score")
        if type(score) not in (int, float) or not math.isfinite(score) or not -1 <= score <= 1:
            raise ValueError("Invalid score")
    labels, scores = [r["label"] for r in rows], [r["score"] for r in rows]
    # Fixed hundredth grid is explicit and reproducible; comparison is inclusive.
    candidates = [dict(threshold=i/100, **metrics(labels, scores, i/100)) for i in range(101)]
    eligible = [c for c in candidates if c["tp"] > 0 and c["precision"] >= target_precision]
    best = max(eligible, key=lambda c: (c["recall"], c["precision"], c["threshold"]), default=None)
    missing = sorted(REQUIRED - {r["category"] for r in rows})
    blockers = []
    if data["synthetic"]:
        blockers.append("synthetic_dataset")
    if sum(labels) < 30 or len(labels)-sum(labels) < 30:
        blockers.append("fewer_than_30_pairs_per_class")
    if missing:
        blockers.append("missing_required_categories")
    if best is None:
        blockers.append("no_threshold_meets_precision_target")
    return {
        "schema": "artworkguard-threshold-tuning-v1",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "dataset_sha256": digest(manifest), "scores_sha256": digest(score_path),
        "split": "validation", "synthetic": data["synthetic"],
        "scoring": {k: report.get(k) for k in (*metadata, "mode", "region_scheme")},
        "target_precision": target_precision, "grid_step": .01,
        "selection": "precision constraint, then maximum recall, precision, threshold",
        "missing_categories": missing, "blockers": blockers,
        "diagnostic_candidate": best,
        "review_candidate": best if not blockers else None,
        "production_ready": False,
        "limitations": ["Minimum counts are a screening rule, not statistical confidence.",
                        "Pair-level precision depends on sample composition and is not marketplace precision.",
                        "Freeze the candidate and evaluate independent test data before human deployment review.",
                        "Hashes bind inputs but do not authenticate score provenance.",
                        "This tunes the match cutoff only, not severity bands or legal judgments."],
        "candidates": candidates,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("scores", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--target-precision", type=float, default=.98)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Output already exists")
    try:
        result = tune(args.dataset, args.scores, args.target_precision)
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.error(str(error))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as file:
        json.dump(result, file, indent=2, allow_nan=False)
    print(json.dumps({"blockers": result["blockers"], "review_candidate": result["review_candidate"]}))


if __name__ == "__main__":
    main()
