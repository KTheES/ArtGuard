import test from 'node:test';
import assert from 'node:assert/strict';
import {assignVariant, createExperimentTracker, evaluateFlag, stableBucket, UPLOAD_CTA_EXPERIMENT} from './feature-flags.js';
import {runtimeConfig} from './runtime-config.mjs';

test('three flags support environment, target user, and percentage controls', () => {
  const config = runtimeConfig({
    FF_ENHANCED_UPLOAD: 'false',
    FF_ENHANCED_UPLOAD_USERS: 'pilot@example.com',
    FF_CONFIDENCE_BADGES: 'true',
    FF_COMPACT_NAV_PERCENT: '100',
  }).flags;
  assert.equal(evaluateFlag('enhancedUpload', {userId: 'pilot@example.com'}, config), true);
  assert.equal(evaluateFlag('enhancedUpload', {userId: 'other@example.com'}, config), false);
  assert.equal(evaluateFlag('confidenceBadges', {userId: 'other@example.com'}, config), true);
  assert.equal(evaluateFlag('compactNavigation', {userId: 'other@example.com'}, config), true);
});

test('A/B assignment is stable and returns both variants across users', () => {
  const users = Array.from({length: 100}, (_, index) => `user-${index}@example.com`);
  const first = users.map((user) => assignVariant(UPLOAD_CTA_EXPERIMENT, user));
  const second = users.map((user) => assignVariant(UPLOAD_CTA_EXPERIMENT, user));
  assert.deepEqual(first, second);
  assert.deepEqual(new Set(first), new Set(['control', 'treatment']));
  assert.equal(stableBucket('same-user'), stableBucket('same-user'));
});

test('experiment tracker stores anonymized events without raw email', () => {
  const values = new Map();
  const storage = {getItem: (key) => values.get(key), setItem: (key, value) => values.set(key, value)};
  const entries = [];
  const entry = createExperimentTracker({storage, logger: {info: (...args) => entries.push(args)}, now: () => '2026-10-31T11:00:00.000Z'})({
    experiment: 'upload-cta-v1', variant: 'treatment', event: 'exposure', userId: 'person@example.com',
  });
  assert.match(entry.user, /^user-\d{2}$/);
  assert.doesNotMatch(JSON.stringify(entry), /person@example\.com/);
  assert.equal(entries.length, 1);
});
