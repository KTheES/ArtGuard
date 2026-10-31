const DEFAULT_FLAGS = Object.freeze({
  enhancedUpload: {enabled: false, targetedUsers: [], rolloutPercentage: 0},
  confidenceBadges: {enabled: true, targetedUsers: [], rolloutPercentage: 100},
  compactNavigation: {enabled: false, targetedUsers: [], rolloutPercentage: 0},
});

export function stableBucket(value) {
  let hash = 2166136261;
  for (const character of String(value)) {
    hash ^= character.codePointAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0) % 100;
}

export function anonymizeUser(userId) {
  return `user-${stableBucket(`privacy:${String(userId).toLowerCase()}`).toString().padStart(2, '0')}`;
}

export function evaluateFlag(name, context = {}, configuredFlags = {}) {
  const definition = {...DEFAULT_FLAGS[name], ...configuredFlags[name]};
  if (!definition || !Object.hasOwn(DEFAULT_FLAGS, name)) return false;
  const userId = String(context.userId ?? '').trim().toLowerCase();
  const targeted = (definition.targetedUsers ?? []).map(String).map((value) => value.toLowerCase()).includes(userId);
  const percentage = Math.min(100, Math.max(0, Number(definition.rolloutPercentage ?? 0)));
  return Boolean(definition.enabled) || targeted || (userId !== '' && stableBucket(`${name}:${userId}`) < percentage);
}

export function assignVariant(experiment, userId) {
  if (!experiment?.key || !Array.isArray(experiment.variants) || experiment.variants.length !== 2) {
    throw new Error('An A/B experiment requires a key and exactly two variants.');
  }
  const bucket = stableBucket(`${experiment.key}:${String(userId).toLowerCase()}`);
  const firstWeight = Math.min(100, Math.max(0, Number(experiment.variants[0].weight ?? 50)));
  return bucket < firstWeight ? experiment.variants[0].key : experiment.variants[1].key;
}

export function createExperimentTracker({storage, logger = console, now = () => new Date().toISOString()} = {}) {
  const key = 'artguard-experiment-events';
  return function track({experiment, variant, event, userId, metadata = {}}) {
    const entry = {timestamp: now(), experiment, variant, event, user: anonymizeUser(userId), metadata};
    if (storage) {
      const current = JSON.parse(storage.getItem(key) || '[]');
      storage.setItem(key, JSON.stringify([...current.slice(-199), entry]));
    }
    logger.info('[experiment]', entry);
    return entry;
  };
}

export const UPLOAD_CTA_EXPERIMENT = Object.freeze({
  key: 'upload-cta-v1',
  variants: [
    {key: 'control', weight: 50, label: '작품 등록'},
    {key: 'treatment', weight: 50, label: '첫 작품 보호하기'},
  ],
});
