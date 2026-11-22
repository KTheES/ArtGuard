# Week 12 coverage summary

검증일: 2026-11-22 (재실행 통과)

| Metric | Result | Gate |
|---|---:|---:|
| Lines | 96.62% | 80% |
| Branches | 83.54% | 80% |
| Functions | 85.71% | 80% |

실행 명령:

```bash
npm run test:coverage
```

Node.js 내장 V8 커버리지에서 테스트 파일, DOM 진입점 `app.js`, Playwright가 검증하는 HTTP 진입점 `server.mjs`를 제외하고, 단위 테스트 대상 핵심 모듈(`api.js`, `delivery-policy.js`, `feature-flags.js`, `runtime-config.mjs`)에 line/branch/function 각각 80% 최소 임계값을 강제합니다. 임계값 미달 시 프로세스가 실패하여 CI가 중단됩니다.
