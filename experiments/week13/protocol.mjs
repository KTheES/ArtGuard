import {createHash} from 'node:crypto';
import {assignVariant, UPLOAD_CTA_EXPERIMENT} from '../../frontend/feature-flags.js';
export const kstDate = (time = new Date()) => new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Seoul'}).format(new Date(time));
export const digest = text => createHash('sha256').update(text).digest('hex');
export function validateAssessment(batch, date, ids) {
  if (batch.schema !== 2 || batch.evaluatedDate !== date || !batch.evaluator || !batch.method || !batch.prompt) throw Error('Missing provenance or fresh evaluation');
  if (batch.responses.length !== ids.length || new Set(batch.responses.map(r=>r.persona)).size !== ids.length) throw Error('Expected unique complete panel');
  for (const r of batch.responses) {
    if (!ids.includes(r.persona) || r.variant !== assignVariant(UPLOAD_CTA_EXPERIMENT, r.persona.toLowerCase()+'@example.invalid')) throw Error('Wrong persona/assignment');
    if (!['click','defer','leave'].includes(r.action) || !Number.isInteger(r.understanding) || r.understanding < 1 || r.understanding > 5 || typeof r.reason !== 'string' || !r.reason.trim()) throw Error('Invalid judgment');
  }
}
export function validateDay(day, batch, hash, ids) {
  validateAssessment(batch, day.date, ids);
  if (day.schema !== 2 || day.assessmentHash !== hash || !day.runId || day.rows.length !== ids.length || new Set(day.rows.map(r=>r.persona)).size !== ids.length) throw Error('Incomplete or mismatched run');
  for (const r of day.rows) {
    const judgment = batch.responses.find(a=>a.persona === r.persona);
    if (!judgment || r.variant !== judgment.variant || r.action !== judgment.action || r.runId !== day.runId || kstDate(r.observedAt) !== day.date) throw Error('Stale/mismatched observation');
    if (['exposure','click','scenarioPassed'].some(key=>typeof r[key] !== 'boolean') || (r.click && (!r.exposure || r.action !== 'click'))) throw Error('Invalid event evidence');
    if (r.scenarioPassed && (!r.exposure || r.click !== (r.action === 'click'))) throw Error('Missing event evidence');
  }
}
export function summarize(rows) {
  return ['control','treatment'].map(variant=>{
    const arm=rows.filter(r=>r.variant===variant), exposed=arm.filter(r=>r.exposure);
    const clicks=exposed.filter(r=>r.click).length;
    return {variant,personas:arm.length,exposures:exposed.length,clicks,ctr:exposed.length?clicks/exposed.length:null,
      deferred:arm.filter(r=>r.action==='defer').length,left:arm.filter(r=>r.action==='leave').length,
      failures:arm.filter(r=>!r.scenarioPassed).length};
  });
}
