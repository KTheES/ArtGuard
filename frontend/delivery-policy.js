export function normalizeIdentity(value) {
  return String(value ?? '').trim().toLowerCase();
}

export function parseCsvTargets(value = '') {
  return [...new Set(String(value).split(',').map(normalizeIdentity).filter(Boolean))];
}

export function clampRolloutPercentage(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.min(100, Math.max(0, parsed)) : fallback;
}

export function stableBucket(value) {
  let hash = 2166136261;
  for (const character of String(value)) {
    hash ^= character.codePointAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0) % 100;
}

export function assignWeightedVariant(experimentKey, userId, variants) {
  if (!experimentKey || !Array.isArray(variants) || variants.length < 2) throw new Error('At least two weighted variants are required.');
  const weights = variants.map((variant) => clampRolloutPercentage(variant.weight));
  const total = weights.reduce((sum, weight) => sum + weight, 0);
  if (total <= 0) throw new Error('Variant weights must total more than zero.');
  const position = (stableBucket(`${experimentKey}:${normalizeIdentity(userId)}`) / 100) * total;
  let cumulative = 0;
  for (let index = 0; index < variants.length; index++) {
    cumulative += weights[index];
    if (position < cumulative) return variants[index].key;
  }
  return variants.at(-1).key;
}

export function decideCanaryTransition({candidate, stable, healthy}) {
  return healthy
    ? {action: 'PROMOTE', percentage: candidate, stablePercentage: candidate}
    : {action: 'ROLLBACK', percentage: stable, stablePercentage: stable};
}
