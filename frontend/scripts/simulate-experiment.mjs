import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {assignVariant, createExperimentTracker, UPLOAD_CTA_EXPERIMENT} from '../feature-flags.js';

const outputArgument = process.argv.find((argument) => argument.startsWith('--output='));
const output = outputArgument?.slice('--output='.length);
const events = [];
const tracker = createExperimentTracker({logger: {info: (_label, entry) => events.push(entry)}, now: (() => {
  let second = 0;
  return () => `2026-10-31T11:00:${String(second++).padStart(2, '0')}.000Z`;
})()});

for (const userId of ['pilot-00@example.com', 'pilot-01@example.com', 'pilot-07@example.com', 'pilot-08@example.com']) {
  const variant = assignVariant(UPLOAD_CTA_EXPERIMENT, userId);
  tracker({experiment: UPLOAD_CTA_EXPERIMENT.key, variant, event: 'exposure', userId});
  tracker({experiment: UPLOAD_CTA_EXPERIMENT.key, variant, event: 'upload_cta_click', userId});
}

const report = {experiment: UPLOAD_CTA_EXPERIMENT.key, assignment: 'deterministic FNV-1a hash', events};
if (output) {
  await mkdir(path.dirname(output), {recursive: true});
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
}
console.log(JSON.stringify(report, null, 2));
