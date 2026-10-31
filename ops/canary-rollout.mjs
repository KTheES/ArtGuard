import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';

const stages = [1, 10, 50, 100];
const argument = (name) => process.argv.find((value) => value.startsWith(`--${name}=`))?.split('=').slice(1).join('=');
const failAt = Number(argument('simulate-failure') ?? -1);
const healthUrl = argument('health-url');
const output = argument('output');
const history = [];
let stable = 0;
let status = 'COMPLETED';

async function healthy(stage) {
  if (stage === failAt) return false;
  if (!healthUrl) return true;
  const response = await fetch(healthUrl, {headers: {'x-canary-percent': String(stage)}, signal: AbortSignal.timeout(5000)});
  return response.ok;
}

for (const percentage of stages) {
  const passed = await healthy(percentage);
  history.push({percentage, action: 'PROMOTE', health: passed ? 'PASS' : 'FAIL'});
  if (!passed) {
    history.push({percentage: stable, action: 'ROLLBACK', health: 'PASS'});
    status = 'ROLLED_BACK';
    break;
  }
  stable = percentage;
}

const report = {strategy: stages, status, stablePercentage: stable, history};
if (output) {
  await mkdir(path.dirname(output), {recursive: true});
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
}
console.log(JSON.stringify(report, null, 2));
if (failAt > 0 && status !== 'ROLLED_BACK') process.exitCode = 1;
