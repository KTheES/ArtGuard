import {readFile,mkdir,writeFile,mkdtemp} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {kstDate,validateAssessment} from './protocol.mjs';
import {createPrompt,parseEvaluation,requireAuthentication} from './copilot-evaluation.mjs';
const root=new URL('../../',import.meta.url),date=kstDate();
const plan=JSON.parse(await readFile(new URL('experiments/week13/plan.json',root)));
if(date<plan.start || date>=plan.endExclusive) throw Error('Outside collection window; no inference requested.');
const personas=JSON.parse(await readFile(new URL('experiments/week13/personas.json',root))).personas;
const target=new URL(`experiments/week13/assessments/${date}.json`,root);
let existing;
try {existing=JSON.parse(await readFile(target,'utf8'));} catch(error) {if(error.code!=='ENOENT')throw error;}
if(existing) {
  validateAssessment(existing,date,personas.map(p=>p.id));
  if(existing.evaluator!=='GitHub Copilot CLI / auto') throw Error('Existing manual evaluation will not be silently replaced or counted as automatic.');
  console.log('Today already evaluated; reusing same-day evidence without another model call.');
  process.exit(0);
}
requireAuthentication(process.env);
// Empty workspace and isolated configuration prevent repository instructions/plugins from entering the evaluation.
const isolated=await mkdtemp(join(tmpdir(),'artguard-week13-'));
const env={PATH:process.env.PATH,HOME:isolated,USERPROFILE:isolated,
  SystemRoot:process.env.SystemRoot,TEMP:isolated,TMP:isolated,
  COPILOT_HOME:join(isolated,'config'),COPILOT_GITHUB_TOKEN:process.env.COPILOT_GITHUB_TOKEN,
  COPILOT_AUTO_UPDATE:'false'};
const cli=process.env.COPILOT_EXECUTABLE || 'copilot';
const exec=promisify(execFile);
const {stdout:version}=await exec(cli,['--version'],{env,cwd:isolated,timeout:30000});
const prompt=createPrompt(personas);
let output;
try {
  output=await exec(cli,['-p',prompt,'--model','auto','--silent','--stream','off',
    '--available-tools','--disable-builtin-mcps','--no-custom-instructions','--no-ask-user',
    '--no-auto-update','--sync-session=off','--disable-client-telemetry'],
    {env,cwd:isolated,timeout:180000,maxBuffer:1024*1024});
} catch {
  // Never print child-process errors: they may embed prompts or credential-bearing diagnostics.
  throw Error('Copilot inference failed or timed out. No retry, paid-provider fallback, or fabricated result. Check authentication and included-credit availability.');
}
if(kstDate()!==date) throw Error('Crossed KST midnight; response will not be backdated.');
const batch=parseEvaluation(output.stdout,{date,prompt,personas,generatedAt:new Date().toISOString(),cliVersion:version.trim()});
await mkdir(new URL('experiments/week13/assessments/',root),{recursive:true});
await writeFile(target,JSON.stringify(batch,null,2)+'\n',{flag:'wx'});
console.log(`Validated fresh Copilot assessment: ${date}; ${batch.responses.length} synthetic personas.`);
