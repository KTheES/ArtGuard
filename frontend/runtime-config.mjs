const truthy = new Set(['1', 'true', 'yes', 'on']);

function enabled(value, fallback = false) {
  return value === undefined ? fallback : truthy.has(String(value).toLowerCase());
}

function percentage(value, fallback) {
  const parsed = Number(value ?? fallback);
  return Number.isFinite(parsed) ? Math.min(100, Math.max(0, parsed)) : fallback;
}

function users(value = '') {
  return String(value).split(',').map((item) => item.trim().toLowerCase()).filter(Boolean);
}

export function runtimeConfig(environment = process.env) {
  return {
    flags: {
      enhancedUpload: {
        enabled: enabled(environment.FF_ENHANCED_UPLOAD),
        targetedUsers: users(environment.FF_ENHANCED_UPLOAD_USERS),
        rolloutPercentage: percentage(environment.FF_ENHANCED_UPLOAD_PERCENT, 0),
      },
      confidenceBadges: {
        enabled: enabled(environment.FF_CONFIDENCE_BADGES, true),
        targetedUsers: users(environment.FF_CONFIDENCE_BADGES_USERS),
        rolloutPercentage: percentage(environment.FF_CONFIDENCE_BADGES_PERCENT, 100),
      },
      compactNavigation: {
        enabled: enabled(environment.FF_COMPACT_NAV),
        targetedUsers: users(environment.FF_COMPACT_NAV_USERS),
        rolloutPercentage: percentage(environment.FF_COMPACT_NAV_PERCENT, 0),
      },
    },
    experiment: {uploadCta: {enabled: enabled(environment.EXPERIMENT_UPLOAD_CTA, true)}},
  };
}

export function runtimeConfigScript(environment = process.env) {
  return `globalThis.__ARTGUARD_CONFIG__=${JSON.stringify(runtimeConfig(environment))};\n`;
}
