import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {validateAssessment,validateDay,summarize,digest} from './protocol.mjs';
const raw=readFileSync(new URL('assessments/2026-11-23.json',import.meta.url),'utf8');
const batch=JSON.parse(raw), ids=batch.responses.map(r=>r.persona);
const fixture=()=>({schema:2,date:batch.evaluatedDate,runId:'test',assessmentHash:digest(raw),
  rows:batch.responses.map(r=>({persona:r.persona,variant:r.variant,action:r.action,runId:'test',
    observedAt:'2026-11-23T01:00:00Z',exposure:true,click:r.action==='click',scenarioPassed:true}))});
test('accept fresh complete evaluations and their replay',()=>validateDay(fixture(),batch,digest(raw),ids));
test('reject a stale assessment rather than relabel yesterday',()=>assert.throws(()=>validateAssessment(batch,'2026-11-24',ids)));
test('reject duplicate, missing and mismatched persona observations',()=>{
  for(const change of [d=>d.rows.pop(),d=>d.rows[0]=d.rows[1],d=>d.rows[0].variant='control']) {
    const d=fixture();change(d);assert.throws(()=>validateDay(d,batch,digest(raw),ids));
  }
});
test('reject stale timestamps, different run IDs, and changed input',()=>{
  for(const change of [d=>d.rows[0].observedAt='2026-11-22T01:00:00Z',d=>d.rows[0].runId='old',d=>d.assessmentHash='old']) {
    const d=fixture();change(d);assert.throws(()=>validateDay(d,batch,digest(raw),ids));
  }
});
test('failures remain visible and unexposed users do not enter CTR denominator',()=>{
  const d=fixture();Object.assign(d.rows[0],{scenarioPassed:false,exposure:false,click:false});
  validateDay(d,batch,digest(raw),ids);
  const arm=summarize(d.rows).find(r=>r.variant==='treatment');
  assert.equal(arm.personas,6);assert.equal(arm.exposures,5);assert.equal(arm.clicks,3);assert.equal(arm.failures,1);
});
test('defer and leave never produce click evidence',()=>{
  const d=fixture();d.rows.find(r=>r.action==='leave').click=true;
  assert.throws(()=>validateDay(d,batch,digest(raw),ids));
});
