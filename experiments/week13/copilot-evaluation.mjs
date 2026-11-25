import {assignVariant,UPLOAD_CTA_EXPERIMENT} from '../../frontend/feature-flags.js';
import {validateAssessment} from './protocol.mjs';

export function createPrompt(personas) {
  const assignments=personas.map(({id,role,scenario})=>{
    const variant=assignVariant(UPLOAD_CTA_EXPERIMENT,id.toLowerCase()+'@example.invalid');
    return {persona:id,role,scenario,variant,cta:UPLOAD_CTA_EXPERIMENT.variants.find(v=>v.key===variant).label};
  });
  return [
    'You are evaluating a prototype as ten synthetic personas, NOT real human interviewees.',
    'Use only the supplied screen facts. Do not use tools, prior sessions, memories, or outside sources.',
    'Screen: 내 작품. 작품을 등록하고 분석 진행 상태를 확인하세요. 아직 등록한 작품이 없습니다. 첫 작품을 업로드하세요.',
    'The assigned CTA is below. Clicking opens 새 작품 등록 with title, description, PNG/JPEG file input (max 20 MiB).',
    'This screen has no visible privacy/deletion policy or legal guarantee explanation. Backend is mocked.',
    'For each persona choose click, defer (needs information), or leave (abandon session) based on its own goal.',
    'Do not optimize arm totals, force variation, claim completed uploads, or infer legal protection.',
    'Report understanding as integer 1..5 and a specific Korean reason. Distinguish understanding from willingness.',
    'Return ONLY JSON: {"responses":[{"persona":"P01","variant":"treatment","action":"click|defer|leave","understanding":1,"reason":"..."}]}.',
    'Include every persona exactly once, preserve the assigned variant. These are hypothetical judgments.',
    JSON.stringify(assignments),
  ].join('\n');
}

export function extractResponse(jsonl) {
  const events=jsonl.trim().split(/\r?\n/).filter(Boolean).map(line=>JSON.parse(line));
  if(events.some(e=>e.type==='error'||e.type==='session.error')) throw Error('Copilot returned an error event');
  const messages=events.filter(e=>e.type==='assistant.message' && typeof e.data?.content==='string');
  if(messages.length) return messages.at(-1).data.content;
  const text=events.filter(e=>e.type==='text' && typeof e.content==='string').map(e=>e.content).join('');
  if(text.trim()) return text;
  throw Error('No final assistant text in Copilot structured output');
}

export function parseEvaluation(raw,{date,prompt,personas,generatedAt,cliVersion}) {
  const parsed=JSON.parse(raw.trim().replace(/^```(?:json)?\s*/,'').replace(/\s*```$/,''));
  const batch={schema:2,evaluatedDate:date,generatedAt,evaluator:'GitHub Copilot CLI / auto',
    method:'Fresh single-session synthetic panel. Prior feedback and preferredVariant excluded from prompt. Ten roles in one model response, not ten independent people. CLI tools and memory disabled.',
    cliVersion,modelSelection:'auto (underlying model may vary)',prompt,responses:parsed.responses};
  validateAssessment(batch,date,personas.map(p=>p.id));
  return batch;
}

export function requireAuthentication(env) {
  if(!env.COPILOT_GITHUB_TOKEN) throw Error('Missing COPILOT_GITHUB_TOKEN: no model call made. Add a user-owned fine-grained PAT with Copilot Requests permission.');
  if(Object.keys(env).some(k=>/^(COPILOT_PROVIDER_|OPENAI_API_KEY$|ANTHROPIC_API_KEY$|AZURE_OPENAI_API_KEY$)/.test(k)&&env[k])) {
    throw Error('External paid-provider credentials are not allowed for this experiment.');
  }
}
