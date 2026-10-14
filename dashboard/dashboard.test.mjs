import test from "node:test";
import assert from "node:assert/strict";
import { buildChartModels, formatValue, resolveDataUrl, validateDoraPayload } from "./lib.mjs";

function baseData() {
  return {
    generated_at: "2026-10-06T00:00:00Z",
    repository: "KTheES/ArtGuard",
    window: { start: "2026-09-01T00:00:00Z", end: "2026-10-01T00:00:00Z" },
    source: { mode: "sample" },
    metrics: {
      lead_time_for_changes: { median_hours: 2.5, p90_hours: 5, sample_size: 4 },
      deployment_frequency: { successful_deployments: 4, per_week: 1 },
      mean_time_to_restore: { mean_hours: 3, median_hours: 3, incident_count: 2 },
      change_failure_rate: { percent: 25, failed_deployments: 1, terminal_deployments: 4 },
    },
    series: [
      { week_start: "2026-09-01", successful_deployments: 1, failed_deployments: 0, lead_time_median_hours: 2, mttr_mean_hours: null, change_failure_rate_percent: 0 },
      { week_start: "2026-09-08", successful_deployments: 1, failed_deployments: 1, lead_time_median_hours: 3, mttr_mean_hours: 4, change_failure_rate_percent: 50 },
    ],
  };
}

const formatCases = [
  ["null", null, "", "N/A"], ["undefined", undefined, "", "N/A"], ["NaN", Number.NaN, "", "N/A"],
  ["positive infinity", Number.POSITIVE_INFINITY, "", "N/A"], ["negative infinity", Number.NEGATIVE_INFINITY, "", "N/A"],
  ["zero", 0, "", "0"], ["one", 1, "", "1"], ["negative one", -1, "", "-1"], ["decimal", 1.25, "", "1.25"],
  ["numeric string", "2.5", "", "2.5"], ["zero hours", 0, "h", "0h"], ["hours", 2.5, "h", "2.5h"],
  ["percentage", 33.33, "%", "33.33%"], ["weekly", 0.31, "/week", "0.31/week"], ["count suffix", 4, "건", "4건"],
  ["thousand", 1000, "", "1,000"], ["ten thousand", 10000, "", "10,000"], ["million", 1000000, "", "1,000,000"],
  ["negative decimal", -2.5, "", "-2.5"], ["one decimal", 9.5, "", "9.5"], ["two decimals", 9.99, "", "9.99"],
  ["empty numeric string", "", "", "N/A"], ["whitespace numeric string", " 3 ", "", "3"], ["non numeric string", "three", "", "N/A"],
];

for (const [name, input, suffix, expected] of formatCases) {
  test(`formatValue: ${name}`, () => assert.equal(formatValue(input, suffix), expected));
}

const baseHref = "http://localhost:8765/dashboard/";
const acceptedPaths = [
  "", "?unused=1", "?data=../reports/dora/latest.json", "?data=./fixture.json", "?data=/reports/dora/latest.json",
  "?data=metrics.json", "?data=metrics.JSON", "?data=./nested/metrics.json", "?data=../a.json", "?data=../../root.json",
  "?data=%2Freports%2Flatest.json", "?data=.%2Ffixture.json", "?data=metrics.json%3Fv%3D1", "?data=metrics.json%23chart",
  "?data=http%3A%2F%2Flocalhost%3A8765%2Freports%2Flatest.json", "?data=./한글.json", "?data=./space%2520name.json", "?foo=bar%26data%3Devil",
];

for (const [index, search] of acceptedPaths.entries()) {
  test(`resolveDataUrl accepts same-origin JSON case ${index + 1}`, () => {
    assert.match(resolveDataUrl(search, baseHref), /^http:\/\/localhost:8765\/.+\.json(?:[?#].*)?$/i);
  });
}

const rejectedPaths = [
  "?data=https://example.com/a.json", "?data=http://example.com/a.json", "?data=//example.com/a.json",
  "?data=https://localhost:8765/a.json", "?data=http://localhost:9999/a.json", "?data=javascript:alert(1)",
  "?data=data:application/json,{}", "?data=file:///tmp/a.json", "?data=ftp://localhost/a.json", "?data=ws://localhost:8765/a.json",
  "?data=blob:http://localhost:8765/id", "?data=metrics", "?data=metrics.txt", "?data=metrics.html", "?data=metrics.json.js",
  "?data=metrics.json/child", "?data=.json/", "?data=./", "?data=../reports/dora/latest.csv", "?data=%2Fetc%2Fpasswd",
  "?data=http://user:pass@localhost:8765/a.json", "?data=http://user@localhost:8765/a.json", "?data=https%3A%2F%2Fevil.test%2Fa.json",
  "?data=%2F%2Fevil.test%2Fa.json",
];

for (const [index, search] of rejectedPaths.entries()) {
  test(`resolveDataUrl rejects unsafe or non-JSON case ${index + 1}`, () => {
    assert.throws(() => resolveDataUrl(search, baseHref));
  });
}

const invalidPayloads = [
  ["null root", () => null], ["array root", () => []], ["string root", () => "invalid"],
  ["missing generated time", d => { delete d.generated_at; return d; }], ["invalid generated time", d => { d.generated_at = "not-a-date"; return d; }],
  ["missing repository", d => { delete d.repository; return d; }], ["empty repository", d => { d.repository = ""; return d; }],
  ["numeric repository", d => { d.repository = 7; return d; }], ["missing window", d => { delete d.window; return d; }],
  ["array window", d => { d.window = []; return d; }], ["missing window start", d => { delete d.window.start; return d; }],
  ["empty window start", d => { d.window.start = ""; return d; }], ["invalid window start", d => { d.window.start = "not-a-date"; return d; }],
  ["missing window end", d => { delete d.window.end; return d; }], ["invalid window end", d => { d.window.end = "tomorrowish"; return d; }],
  ["reversed window", d => { d.window.start = "2026-10-02T00:00:00Z"; return d; }], ["missing source", d => { delete d.source; return d; }],
  ["array source", d => { d.source = []; return d; }], ["missing source mode", d => { delete d.source.mode; return d; }],
  ["empty source mode", d => { d.source.mode = " "; return d; }], ["missing metrics", d => { delete d.metrics; return d; }],
  ["array metrics", d => { d.metrics = []; return d; }], ["missing lead metrics", d => { delete d.metrics.lead_time_for_changes; return d; }],
  ["array lead metrics", d => { d.metrics.lead_time_for_changes = []; return d; }], ["string lead median", d => { d.metrics.lead_time_for_changes.median_hours = "2"; return d; }],
  ["negative lead median", d => { d.metrics.lead_time_for_changes.median_hours = -1; return d; }], ["NaN lead p90", d => { d.metrics.lead_time_for_changes.p90_hours = Number.NaN; return d; }],
  ["negative lead sample", d => { d.metrics.lead_time_for_changes.sample_size = -1; return d; }], ["decimal lead sample", d => { d.metrics.lead_time_for_changes.sample_size = 1.5; return d; }],
  ["missing frequency", d => { delete d.metrics.deployment_frequency; return d; }], ["string successful deployments", d => { d.metrics.deployment_frequency.successful_deployments = "4"; return d; }],
  ["negative successful deployments", d => { d.metrics.deployment_frequency.successful_deployments = -1; return d; }], ["decimal successful deployments", d => { d.metrics.deployment_frequency.successful_deployments = 1.2; return d; }],
  ["negative frequency", d => { d.metrics.deployment_frequency.per_week = -0.1; return d; }], ["infinite frequency", d => { d.metrics.deployment_frequency.per_week = Infinity; return d; }],
  ["missing MTTR", d => { delete d.metrics.mean_time_to_restore; return d; }], ["string MTTR mean", d => { d.metrics.mean_time_to_restore.mean_hours = "3"; return d; }],
  ["negative MTTR mean", d => { d.metrics.mean_time_to_restore.mean_hours = -3; return d; }], ["infinite MTTR median", d => { d.metrics.mean_time_to_restore.median_hours = Infinity; return d; }],
  ["negative incident count", d => { d.metrics.mean_time_to_restore.incident_count = -1; return d; }], ["decimal incident count", d => { d.metrics.mean_time_to_restore.incident_count = 1.1; return d; }],
  ["missing CFR", d => { delete d.metrics.change_failure_rate; return d; }], ["negative CFR percent", d => { d.metrics.change_failure_rate.percent = -1; return d; }],
  ["CFR over 100", d => { d.metrics.change_failure_rate.percent = 101; return d; }], ["negative failed deployments", d => { d.metrics.change_failure_rate.failed_deployments = -1; return d; }],
  ["decimal failed deployments", d => { d.metrics.change_failure_rate.failed_deployments = 1.2; return d; }], ["negative terminal deployments", d => { d.metrics.change_failure_rate.terminal_deployments = -1; return d; }],
  ["failures exceed terminal", d => { d.metrics.change_failure_rate.failed_deployments = 5; return d; }], ["missing series", d => { delete d.series; return d; }],
  ["object series", d => { d.series = {}; return d; }], ["oversized series", d => { d.series = Array.from({ length: 521 }, (_, i) => ({ ...d.series[0], week_start: `2026-01-${String((i % 28) + 1).padStart(2,"0")}` })); return d; }],
  ["null series item", d => { d.series = [null]; return d; }], ["invalid week format", d => { d.series[0].week_start = "2026/09/01"; return d; }],
  ["impossible week date", d => { d.series[0].week_start = "2026-02-30"; return d; }], ["duplicate weeks", d => { d.series[1].week_start = d.series[0].week_start; return d; }],
  ["descending weeks", d => { d.series[1].week_start = "2026-08-25"; return d; }], ["negative series successes", d => { d.series[0].successful_deployments = -1; return d; }],
  ["decimal series successes", d => { d.series[0].successful_deployments = 0.5; return d; }], ["negative series failures", d => { d.series[0].failed_deployments = -1; return d; }],
  ["string series lead", d => { d.series[0].lead_time_median_hours = "2"; return d; }], ["negative series lead", d => { d.series[0].lead_time_median_hours = -2; return d; }],
  ["negative series MTTR", d => { d.series[0].mttr_mean_hours = -2; return d; }], ["series CFR over 100", d => { d.series[0].change_failure_rate_percent = 100.1; return d; }],
  ["negative series CFR", d => { d.series[0].change_failure_rate_percent = -0.1; return d; }],
];

for (const [name, mutate] of invalidPayloads) {
  test(`validateDoraPayload rejects ${name}`, () => assert.throws(() => validateDoraPayload(mutate(baseData()))));
}

test("validateDoraPayload accepts the baseline payload", () => assert.equal(validateDoraPayload(baseData()).repository, "KTheES/ArtGuard"));
test("validateDoraPayload accepts nullable lead metrics", () => { const d = baseData(); d.metrics.lead_time_for_changes.median_hours = null; d.metrics.lead_time_for_changes.p90_hours = null; assert.doesNotThrow(() => validateDoraPayload(d)); });
test("validateDoraPayload accepts nullable MTTR metrics", () => { const d = baseData(); d.metrics.mean_time_to_restore.mean_hours = null; d.metrics.mean_time_to_restore.median_hours = null; assert.doesNotThrow(() => validateDoraPayload(d)); });
test("validateDoraPayload accepts nullable CFR percent", () => { const d = baseData(); d.metrics.change_failure_rate.percent = null; assert.doesNotThrow(() => validateDoraPayload(d)); });

for (let index = 0; index < 20; index += 1) {
  test(`buildChartModels maps weekly series case ${index + 1}`, () => {
    const item = { week_start: `2026-10-${String(index + 1).padStart(2, "0")}`, successful_deployments: index, failed_deployments: index % 3, lead_time_median_hours: index / 2, mttr_mean_hours: index % 2 ? index : null };
    const model = buildChartModels([item]);
    assert.deepEqual(model, { labels: [item.week_start.slice(5)], successfulDeployments: [index], failedDeployments: [index % 3], leadTimeHours: [index / 2], mttrHours: [item.mttr_mean_hours] });
  });
}
