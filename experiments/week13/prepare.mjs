import {readFile, mkdir, writeFile, rm} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {kstDate, digest, validateAssessment} from './protocol.mjs';
const root=new URL('../../',import.meta.url), date=kstDate();
const runtime=new URL('reports/week13/runtime/',root);
// Remove only disposable per-run results, never historical observations.
await rm(runtime,{recursive:true,force:true});
const raw=await readFile(new URL(`experiments/week13/assessments/${date}.json`,root),'utf8');
const assessment=JSON.parse(raw);
const panel=JSON.parse(await readFile(new URL('experiments/week13/personas.json',root)));
validateAssessment(assessment,date,panel.personas.map(p=>p.id));
await mkdir(runtime,{recursive:true});
await writeFile(new URL('manifest.json',runtime),JSON.stringify({schema:2,date,runId:randomUUID(),assessmentHash:digest(raw),assessment},null,2));
console.log(`Fresh assessment ready: ${date}. ${assessment.responses.length} personas.`);
