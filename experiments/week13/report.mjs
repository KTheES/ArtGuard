import {readFile, readdir, mkdir, writeFile, cp} from 'node:fs/promises';
const root = new URL('../../',import.meta.url);
const plan = JSON.parse(await readFile(new URL('experiments/week13/plan.json',root)));
const date = new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Seoul'}).format(new Date());
const latest = new URL('reports/week13/latest/',root);
const names = (await readdir(latest)).filter(n=>/^P\d{2}\.json$/.test(n));
const rows = await Promise.all(names.map(n=>readFile(new URL(n,latest),'utf8').then(JSON.parse)));
if(rows.length!==10 || new Set(rows.map(r=>r.persona)).size!==10) throw Error('Expected ten distinct persona results');
if(rows.some(r=>r.source!=='synthetic-playwright-panel'||!r.scenarioPassed)) throw Error('Invalid scenario result');
const daily = new URL('reports/week13/daily/',root);
await mkdir(daily,{recursive:true});
if(date>=plan.start && date<plan.endExclusive) {
  const dest=new URL(date+'/',daily); await mkdir(dest,{recursive:true});
  await cp(latest,dest,{recursive:true});
}
const dates=(await readdir(daily)).filter(d=>/^\d{4}-\d{2}-\d{2}$/.test(d)&&d>=plan.start&&d<plan.endExclusive).sort();
const history=[];
for(const day of dates){
 const dir=new URL(day+'/',daily);
 const records=await Promise.all((await readdir(dir)).filter(n=>n.endsWith('.json')).map(n=>readFile(new URL(n,dir),'utf8').then(JSON.parse)));
 for(const variant of ['control','treatment']){
  const arm=records.filter(r=>r.variant===variant);
  history.push({date:day,variant,users:arm.length,clicks:arm.filter(r=>r.click).length,ctr:arm.length?arm.filter(r=>r.click).length/arm.length:null});
 }
}
const complete=dates.length>=14;
const metrics={source:plan.source,observedDates:dates,complete,history,limitation:'Repeated scripted panel. No causal conversion or human engagement inference.'};
await writeFile(new URL('reports/week13/metrics.json',root),JSON.stringify(metrics,null,2)+'\n');
const table=history.map(r=>`| ${r.date} | ${r.variant} | ${r.users} | ${r.clicks} | ${r.ctr===null?'N/A':(r.ctr*100).toFixed(1)+'%'} |`).join('\n');
await writeFile(new URL('reports/week13/REPORT.md',root),`# Week 13 experiment report\n\nUpdated: ${date} KST\n\nObserved dates: ${dates.length}/14. Status: ${complete?'14 dated observations collected':'IN PROGRESS'}\n\nSource: synthetic LLM-persona panel + Playwright. Clicks are scripted, not measured human preference.\n\n| Date | Variant | Exposed personas | Clicks | Scripted CTR |\n|---|---|---:|---:|---:|\n${table}\n\nDecision: provisional PIVOT of misleading protection copy based on qualitative persona feedback. Final product decision deferred; this panel cannot establish conversion uplift.\n`);
console.log(JSON.stringify(metrics,null,2));
