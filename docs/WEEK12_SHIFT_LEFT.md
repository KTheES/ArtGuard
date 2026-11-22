# 12주차 증빙: Shift-Left 테스트 자동화

검증일: 2026-11-22

- CI 실행: [Week 12 실행 #1](https://github.com/KTheES/ArtGuard/actions/runs/45003985969)
- 로컬 Chromium E2E: 1 passed (2026-11-22)

| 요구사항 | 증거 |
|---|---|
| 단위 테스트 80% 이상 | [커버리지 보고서](../reports/week12/coverage-summary.md): Lines 96.62%, Branches 83.54%, Functions 85.71% |
| CI 자동 실행 | [Week 12 CI 실행 목록](https://github.com/KTheES/ArtGuard/actions/workflows/week12-shift-left.yml) · [워크플로 코드](../.github/workflows/week12-shift-left.yml) |
| 핵심 기능 5개 TDD | [Red-Green-Refactor 커밋 기록](../reports/week12/tdd-cycle.md) · [테스트 코드](../frontend/delivery-policy.test.js) |
| Playwright E2E | [로그인/가입 전환 시나리오](../frontend/e2e/auth-flow.spec.js) |
| 실패 스크린샷 Artifact | [only-on-failure 설정](../frontend/playwright.config.js), CI의 failure() 업로드 단계 |
| 리팩터링 회귀 검증 | 기존 API/플래그 테스트와 신규 정책 테스트 총 14개 및 Canary 테스트 통과 |

커버리지 범위는 `api.js`, `delivery-policy.js`, `feature-flags.js`, `runtime-config.mjs`입니다. 테스트 파일과 DOM/HTTP 진입점은 제외한 핵심 모듈 전체 합산이며, 애플리케이션 전체 커버리지를 뜻하지 않습니다. CI는 line/branch/function 각각 80% 미만일 때 실패합니다.

TDD 핵심 기능은 사용자 식별자 정규화, CSV 대상자 파싱, 롤아웃 비율 보정, 가중 variant 배정, Canary 승격/롤백 결정입니다. 테스트를 먼저 작성한 Red 커밋, 구현을 추가한 Green 커밋, 기존 코드에 공통 정책을 연결한 Refactor 커밋을 순서대로 보존했습니다.

재현: `cd frontend`, `npm ci`, `npm run test:coverage`, `npm run test:canary`, `npx playwright install chromium`, `npm run test:e2e`.

E2E 실패 시 스크린샷·trace·video를 `test-results`에 보관하고 CI Artifact로 업로드합니다. 성공 실행에서는 실패 스크린샷이 생성되지 않습니다.
