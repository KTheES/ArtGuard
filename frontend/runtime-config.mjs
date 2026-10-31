import {clampRolloutPercentage, parseCsvTargets} from './delivery-policy.js';

const truthy = new Set(['1', 'true', 'yes', 'on']);

function enabled(value, fallback = false) {
  return value === undefined ? fallback : truthy.has(String(value).toLowerCase());
}

export function runtimeConfig(environment = process.env) {
  return {
    flags: {
      enhancedUpload: {
        enabled: enabled(environment.FF_ENHANCED_UPLOAD),
        targetedUsers: parseCsvTargets(environment.FF_ENHANCED_UPLOAD_USERS),
        rolloutPercentage: clampRolloutPercentage(environment.FF_ENHANCED_UPLOAD_PERCENT, 0),
      },
      confidenceBadges: {
        enabled: enabled(environment.FF_CONFIDENCE_BADGES, true),
        targetedUsers: parseCsvTargets(environment.FF_CONFIDENCE_BADGES_USERS),
        rolloutPercentage: clampRolloutPercentage(environment.FF_CONFIDENCE_BADGES_PERCENT ?? 100, 100),
      },
      compactNavigation: {
        enabled: enabled(environment.FF_COMPACT_NAV),
        targetedUsers: parseCsvTargets(environment.FF_COMPACT_NAV_USERS),
        rolloutPercentage: clampRolloutPercentage(environment.FF_COMPACT_NAV_PERCENT, 0),
      },
    },
    experiment: {uploadCta: {enabled: enabled(environment.EXPERIMENT_UPLOAD_CTA, true)}},
  };
}

export function runtimeConfigScript(environment = process.env) {
  return `globalThis.__ARTGUARD_CONFIG__=${JSON.stringify(runtimeConfig(environment))};\n`;
}
