const DEFAULT_DATA_PATH = "../reports/dora/latest.json";

function object(value, name) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`${name} must be an object`);
  }
  return value;
}

function nonEmptyString(value, name) {
  if (typeof value !== "string" || value.trim() === "") {
    throw new TypeError(`${name} must be a non-empty string`);
  }
  return value;
}

function finiteNumber(value, name, { nullable = false, min = 0, max = Number.POSITIVE_INFINITY, integer = false } = {}) {
  if (nullable && value === null) return value;
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new TypeError(`${name} must be a finite number${nullable ? " or null" : ""}`);
  }
  if (value < min || value > max || (integer && !Number.isInteger(value))) {
    throw new RangeError(`${name} is outside its allowed range`);
  }
  return value;
}

function isoDate(value, name, dateOnly = false) {
  nonEmptyString(value, name);
  if (dateOnly && !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new TypeError(`${name} must use YYYY-MM-DD`);
  }
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp) || (dateOnly && new Date(timestamp).toISOString().slice(0, 10) !== value)) {
    throw new TypeError(`${name} must be a valid date`);
  }
  return value;
}

export function formatValue(value, suffix = "") {
  if (value === null || value === undefined || (typeof value === "string" && value.trim() === "") || !Number.isFinite(Number(value))) return "N/A";
  return `${Number(value).toLocaleString("ko-KR", { maximumFractionDigits: 2 })}${suffix}`;
}

export function resolveDataUrl(search = "", baseHref = "http://localhost/dashboard/") {
  const requested = new URLSearchParams(search).get("data") || DEFAULT_DATA_PATH;
  const base = new URL(baseHref);
  const resolved = new URL(requested, base);
  if (!/^https?:$/.test(resolved.protocol) || resolved.origin !== base.origin || resolved.username || resolved.password) {
    throw new TypeError("data must be a same-origin HTTP(S) URL");
  }
  if (!resolved.pathname.toLowerCase().endsWith(".json")) {
    throw new TypeError("data must point to a JSON file");
  }
  return resolved.href;
}

export function validateDoraPayload(payload) {
  const data = object(payload, "payload");
  isoDate(data.generated_at, "generated_at");
  nonEmptyString(data.repository, "repository");
  const window = object(data.window, "window");
  isoDate(window.start, "window.start");
  isoDate(window.end, "window.end");
  if (Date.parse(window.start) > Date.parse(window.end)) {
    throw new RangeError("window.start must not be after window.end");
  }
  const source = object(data.source, "source");
  nonEmptyString(source.mode, "source.mode");

  const metrics = object(data.metrics, "metrics");
  const lead = object(metrics.lead_time_for_changes, "metrics.lead_time_for_changes");
  finiteNumber(lead.median_hours, "lead.median_hours", { nullable: true });
  finiteNumber(lead.p90_hours, "lead.p90_hours", { nullable: true });
  finiteNumber(lead.sample_size, "lead.sample_size", { integer: true });

  const frequency = object(metrics.deployment_frequency, "metrics.deployment_frequency");
  finiteNumber(frequency.successful_deployments, "frequency.successful_deployments", { integer: true });
  finiteNumber(frequency.per_week, "frequency.per_week");

  const mttr = object(metrics.mean_time_to_restore, "metrics.mean_time_to_restore");
  finiteNumber(mttr.mean_hours, "mttr.mean_hours", { nullable: true });
  finiteNumber(mttr.median_hours, "mttr.median_hours", { nullable: true });
  finiteNumber(mttr.incident_count, "mttr.incident_count", { integer: true });

  const cfr = object(metrics.change_failure_rate, "metrics.change_failure_rate");
  finiteNumber(cfr.percent, "cfr.percent", { nullable: true, max: 100 });
  finiteNumber(cfr.failed_deployments, "cfr.failed_deployments", { integer: true });
  finiteNumber(cfr.terminal_deployments, "cfr.terminal_deployments", { integer: true });
  if (cfr.failed_deployments > cfr.terminal_deployments) {
    throw new RangeError("failed deployments must not exceed terminal deployments");
  }

  if (!Array.isArray(data.series) || data.series.length > 520) {
    throw new TypeError("series must be an array with at most 520 entries");
  }
  let previousWeek = "";
  data.series.forEach((entry, index) => {
    const item = object(entry, `series[${index}]`);
    isoDate(item.week_start, `series[${index}].week_start`, true);
    if (previousWeek && item.week_start <= previousWeek) {
      throw new RangeError("series weeks must be unique and strictly increasing");
    }
    previousWeek = item.week_start;
    finiteNumber(item.successful_deployments, `series[${index}].successful_deployments`, { integer: true });
    finiteNumber(item.failed_deployments, `series[${index}].failed_deployments`, { integer: true });
    finiteNumber(item.lead_time_median_hours, `series[${index}].lead_time_median_hours`, { nullable: true });
    finiteNumber(item.mttr_mean_hours, `series[${index}].mttr_mean_hours`, { nullable: true });
    finiteNumber(item.change_failure_rate_percent, `series[${index}].change_failure_rate_percent`, { nullable: true, max: 100 });
  });
  return data;
}

export function buildChartModels(series) {
  return {
    labels: series.map((item) => item.week_start.slice(5)),
    successfulDeployments: series.map((item) => item.successful_deployments),
    failedDeployments: series.map((item) => item.failed_deployments),
    leadTimeHours: series.map((item) => item.lead_time_median_hours),
    mttrHours: series.map((item) => item.mttr_mean_hours),
  };
}
