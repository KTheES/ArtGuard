import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createPrompt,parseEvaluation,requireAuthentication} from './copilot-evaluation.mjs';
const personas=JSON.parse(readFileSync(new URL('personas.json',import.meta.url))).personas;
const fixture=JSON.parse(readFileSync(new URL('assessments/2026-11-23.json',import.meta.url)));
const context={date:'2026-11-25',prompt:createPrompt(personas),personas,generatedAt:'2026-11-25T01:00:00Z',cliVersion:'test-only'};
test('prompt excludes previous preferences and feedback',()=>{
  for(const p of personas){assert.ok(!context.prompt.includes(p.feedback));assert.ok(context.prompt.includes(p.scenario));}
  assert.ok(!context.prompt.includes('preferredVariant'));
});
test('missing token and external API credentials stop before inference',()=>{
  assert.throws(()=>requireAuthentication({}));
  assert.throws(()=>requireAuthentication({COPILOT_GITHUB_TOKEN:'test',OPENAI_API_KEY:'test'}));
  assert.doesNotThrow(()=>requireAuthentication({COPILOT_GITHUB_TOKEN:'test'}));
});
test('valid structured response preserves provenance',()=>{
  const result=parseEvaluation(JSON.stringify({responses:fixture.responses}),context);
  assert.equal(result.evaluatedDate,context.date);assert.equal(result.responses.length,10);
  assert.equal(result.evaluator,'GitHub Copilot CLI / auto');
});
test('refusal, malformed JSON, incomplete panels and altered assignments fail closed',()=>{
  for(const raw of ['I cannot respond','{}',JSON.stringify({responses:fixture.responses.slice(1)}),
    JSON.stringify({responses:fixture.responses.map(r=>({...r,variant:'invalid'}))})]) {
    assert.throws(()=>parseEvaluation(raw,context));
  }
});
