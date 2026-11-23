import {readFile, readdir, mkdir, writeFile} from 'node:fs/promises';
import {kstDate, digest, validateDay, summarize} from './protocol.mjs';
const root=new URL('../../',import.meta.url);
const read=async path=>JSON.parse(await readFile(new URL(path,root),'utf8'));
const plan=await read('experiments/week13/plan.json');
const ids=(await read('experiments/week13/personas.json')).personas.map(p=>p.id);
const manifest=await read('reports/week13/runtime/manifest.json');
const date=kstDate();
if(manifest.date!==date || date<plan.start || date>=plan.endExclusive) throw Error('Run outside its evaluation date/window');
const rows=await Promise.all(ids.map(id=>read(`reports/week13/runtime/${id}.json`)));
const day={schema:2,date,runId:manifest.runId,assessmentHash:manifest.assessmentHash,rows};
const raw=await readFile(new URL(`experiments/week13/assessments/${date}.json`,root),'utf8');
validateDay(day,JSON.parse(raw),digest(raw),ids);
const daily=new URL('reports/week13/evaluated-days/',root);
await mkdir(daily,{recursive:true});
await writeFile(new URL(date+'.json',daily),JSON.stringify(day,null,2)+'\n');
const dates=[],history=[],excluded=[];
for(const name of (await readdir(daily)).filter(n=>/^\d{4}-\d{2}-\d{2}\.json$/.test(n)).sort()) {
  const d=name.slice(0,10);
  try {
    if(d<plan.start || d>=plan.endExclusive || d>date) throw Error('Outside window');
    const record=JSON.parse(await readFile(new URL(name,daily),'utf8'));
    const assessment=await readFile(new URL(`experiments/week13/assessments/${d}.json`,root),'utf8');
    if(record.date!==d) throw Error('Filename/date mismatch');
    validateDay(record,JSON.parse(assessment),digest(assessment),ids);
    dates.push(d);
    history.push(...summarize(record.rows).map(arm=>({date:d,...arm})));
  } catch(error) {excluded.push({date:d,reason:error.message});}
}
const complete=dates.length>=plan.minimumDays;
const metrics={schema:2,source:plan.source,observedDates:dates,complete,history,excluded,
  limitation:'Unblinded synthetic LLM decisions replayed in a mocked browser. Not independent humans or causal conversion evidence. Legacy scripted days excluded.'};
await writeFile(new URL('reports/week13/metrics.json',root),JSON.stringify(metrics,null,2)+'\n');
const table=history.map(r=>`| ${r.date} | ${r.variant} | ${r.exposures} | ${r.clicks} | ${r.ctr===null?'N/A':(r.ctr*100).toFixed(1)+'%'} | ${r.deferred} | ${r.left} | ${r.failures} |`).join('\n');
await writeFile(new URL('reports/week13/REPORT.md',root),`# Week 13 experiment report\n\nUpdated: ${date} KST. Valid observed dates: ${dates.length}/${plan.minimumDays}. ${complete?'Collection complete; interpretation pending':'IN PROGRESS — NOT COMPLETE'}.\n\n${metrics.limitation}\n\n| Date | Variant | Exposed | Clicks | Synthetic CTR | Defer | Leave | Test failures |\n|---|---|---:|---:|---:|---:|---:|---:|\n${table}\n\nExcluded invalid days: ${excluded.length}. Failures remain in guardrails. Incomplete batches cannot count as a full day.\n\nDecision: provisional copy PIVOT from exploratory feedback; final product effect undetermined.\n`);
console.log(JSON.stringify(metrics,null,2));
