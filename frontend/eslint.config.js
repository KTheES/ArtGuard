import js from '@eslint/js';
import globals from 'globals';

export default [
  {
    ignores: ['dist/**', 'playwright-report/**', 'test-results/**', 'drill-results/**', 'playwright-drill-report/**'],
  },
  {
    ...js.configs.recommended,
    files: ['**/*.js', '**/*.mjs'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
  },
];
