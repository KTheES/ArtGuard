import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'canary-rollout.mjs');

test('canary rolls back to 10 percent when 50 percent health check fails', () => {
  const result = spawnSync(process.execPath, [script, '--simulate-failure=50'], {encoding: 'utf8'});
  assert.equal(result.status, 0);
  const report = JSON.parse(result.stdout);
  assert.equal(report.status, 'ROLLED_BACK');
  assert.equal(report.stablePercentage, 10);
  assert.deepEqual(report.strategy, [1, 10, 50, 100]);
  assert.equal(report.history.at(-1).action, 'ROLLBACK');
});
