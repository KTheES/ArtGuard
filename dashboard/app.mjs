import { buildChartModels, formatValue, resolveDataUrl, validateDoraPayload } from "./lib.mjs";

const colors = { grid:"rgba(143,165,193,.15)", text:"#91a6c0", green:"#39d98a", red:"#ff6b7a", blue:"#4da3ff", amber:"#ffb84d" };

function element(id) {
  const found = document.getElementById(id);
  if (!found) throw new Error(`Missing dashboard element: ${id}`);
  return found;
}

function chartOptions(yTitle) {
  return { responsive:true, maintainAspectRatio:false, interaction:{mode:"index",intersect:false}, plugins:{legend:{labels:{color:colors.text,usePointStyle:true,boxWidth:9}}}, scales:{x:{ticks:{color:colors.text,maxRotation:0},grid:{display:false}},y:{beginAtZero:true,title:{display:true,text:yTitle,color:colors.text},ticks:{color:colors.text},grid:{color:colors.grid}}} };
}

function renderCharts(series) {
  if (typeof globalThis.Chart !== "function") throw new Error("Chart.js를 불러오지 못했습니다");
  const model = buildChartModels(series);
  new globalThis.Chart(element("deploymentsChart"), { type:"bar", data:{labels:model.labels,datasets:[{label:"성공",data:model.successfulDeployments,backgroundColor:colors.green,borderRadius:5},{label:"실패/오류",data:model.failedDeployments,backgroundColor:colors.red,borderRadius:5}]}, options:chartOptions("deployments") });
  new globalThis.Chart(element("timeChart"), { type:"line", data:{labels:model.labels,datasets:[{label:"Lead Time",data:model.leadTimeHours,borderColor:colors.blue,backgroundColor:colors.blue,spanGaps:true,tension:.35},{label:"MTTR",data:model.mttrHours,borderColor:colors.amber,backgroundColor:colors.amber,spanGaps:true,tension:.35}]}, options:chartOptions("hours") });
}

async function loadDashboard() {
  try {
    const response = await fetch(resolveDataUrl(location.search, location.href), { cache: "no-store" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = validateDoraPayload(await response.json());
    const metrics = data.metrics;
    element("repository").textContent = `${data.repository} · ${data.window.start.slice(0,10)} — ${data.window.end.slice(0,10)}`;
    element("lead").textContent = formatValue(metrics.lead_time_for_changes.median_hours, "h");
    element("leadDetail").textContent = `n=${metrics.lead_time_for_changes.sample_size} · p90 ${formatValue(metrics.lead_time_for_changes.p90_hours,"h")}`;
    element("frequency").textContent = formatValue(metrics.deployment_frequency.per_week, "/week");
    element("frequencyDetail").textContent = `성공 배포 ${metrics.deployment_frequency.successful_deployments}건`;
    element("mttr").textContent = formatValue(metrics.mean_time_to_restore.mean_hours, "h");
    element("mttrDetail").textContent = `incident ${metrics.mean_time_to_restore.incident_count}건`;
    element("cfr").textContent = formatValue(metrics.change_failure_rate.percent, "%");
    element("cfrDetail").textContent = `실패 ${metrics.change_failure_rate.failed_deployments}/${metrics.change_failure_rate.terminal_deployments}`;
    element("generated").textContent = `Generated ${data.generated_at}`;
    if (data.source.mode === "sample") {
      element("sampleBadge").classList.add("show");
      element("notice").textContent = "현재 화면은 구조 검증용 샘플 데이터입니다. GitHub Actions 최초 실행 후 실측 데이터로 교체됩니다.";
      element("notice").classList.add("show");
    }
    renderCharts(data.series);
  } catch (error) {
    element("notice").textContent = `DORA JSON을 불러오지 못했습니다: ${error.message}. 저장소 루트에서 HTTP 서버를 실행했는지 확인하세요.`;
    element("notice").classList.add("show");
  }
}

loadDashboard();
