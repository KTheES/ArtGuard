import test from 'node:test';
import assert from 'node:assert/strict';
import {
  assignWeightedVariant,
  clampRolloutPercentage,
  decideCanaryTransition,
  normalizeIdentity,
  parseCsvTargets,
} from './delivery-policy.js';

test('normalizeIdentity trims and lowercases a user identifier', () => {
  assert.equal(normalizeIdentity('  Pilot@Example.COM '), 'pilot@example.com');
  assert.equal(normalizeIdentity(null), '');
});

test('parseCsvTargets normalizes, removes duplicates, and ignores empty entries', () => {
  assert.deepEqual(parseCsvTargets(' Alice@example.com, ,BOB@example.com,alice@example.com '), [
    'alice@example.com',
    'bob@example.com',
  ]);
});

test('clampRolloutPercentage constrains invalid and out-of-range configuration', () => {
  assert.equal(clampRolloutPercentage('-5'), 0);
  assert.equal(clampRolloutPercentage('25.5'), 25.5);
  assert.equal(clampRolloutPercentage('150'), 100);
  assert.equal(clampRolloutPercentage('invalid', 10), 10);
});

test('assignWeightedVariant is stable and honors the boundary weights', () => {
  const variants = [{key: 'control', weight: 100}, {key: 'treatment', weight: 0}];
  assert.equal(assignWeightedVariant('cta-v2', 'same@example.com', variants), 'control');
  assert.equal(
    assignWeightedVariant('cta-v2', 'same@example.com', [{key: 'control', weight: 0}, {key: 'treatment', weight: 100}]),
    'treatment',
  );
  assert.equal(assignWeightedVariant('cta-v2', 'same@example.com', variants), assignWeightedVariant('cta-v2', 'same@example.com', variants));
});

test('decideCanaryTransition promotes healthy stages and rolls back unhealthy stages', () => {
  assert.deepEqual(decideCanaryTransition({candidate: 10, stable: 1, healthy: true}), {
    action: 'PROMOTE',
    percentage: 10,
    stablePercentage: 10,
  });
  assert.deepEqual(decideCanaryTransition({candidate: 50, stable: 10, healthy: false}), {
    action: 'ROLLBACK',
    percentage: 10,
    stablePercentage: 10,
  });
});
