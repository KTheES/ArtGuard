# 12주차 증빙: Shift-Left 테스트 자동화

검증일: 2026-11-22

- CI 실행: [선택과제 포함 실행 #5](https://github.com/KTheES/ArtGuard/actions/runs/45004370299)
- 로컬 Chromium E2E: 1 passed (2026-11-22)

| 요구사항 | 증거 |
|---|---|
| 단위 테스트 80% 이상 | [커버리지 보고서](../reports/week12/coverage-summary.md): Lines 96.64%, Branches 88.75%, Functions 89.29% |
| CI 자동 실행 | [Week 12 CI 실행 목록](https://github.com/KTheES/ArtGuard/actions/workflows/week12-shift-left.yml) · [워크플로 코드](../.github/workflows/week12-shift-left.yml) |
| 핵심 기능 5개 TDD | [Red-Green-Refactor 커밋 기록](../reports/week12/tdd-cycle.md) · [테스트 코드](../frontend/delivery-policy.test.js) |
| Playwright E2E | [로그인/가입 전환 시나리오](../frontend/e2e/auth-flow.spec.js) |
| 실패 스크린샷 Artifact | [only-on-failure 설정](../frontend/playwright.config.js), CI의 failure() 업로드 단계 |
| 리팩터링 회귀 검증 | [추가한 레거시 회귀 테스트](../frontend/legacy-regression.test.js): 전체 18개 테스트 및 Canary 테스트 통과 |

커버리지 범위는 `api.js`, `delivery-policy.js`, `feature-flags.js`, `runtime-config.mjs`입니다. 테스트 파일과 DOM/HTTP 진입점은 제외한 핵심 모듈 전체 합산이며, 애플리케이션 전체 커버리지를 뜻하지 않습니다. CI는 line/branch/function 각각 80% 미만일 때 실패합니다.

TDD 핵심 기능은 사용자 식별자 정규화, CSV 대상자 파싱, 롤아웃 비율 보정, 가중 variant 배정, Canary 승격/롤백 결정입니다. 테스트를 먼저 작성한 Red 커밋, 구현을 추가한 Green 커밋, 기존 코드에 공통 정책을 연결한 Refactor 커밋을 순서대로 보존했습니다.

재현: `cd frontend`, `npm ci`, `npm run test:coverage`, `npm run test:canary`, `npx playwright install chromium`, `npm run test:e2e`.

E2E 실패 시 스크린샷·trace·video를 `test-results`에 보관하고 CI Artifact로 업로드합니다. 성공 실행에서는 실패 스크린샷이 생성되지 않습니다.

## 채점용 테스트 코드와 CI 결과 확인 순서

1. 위 CI 실행 링크에서 `Coverage gate and Playwright` Job을 엽니다.
2. `Lint and enforce 80 percent coverage` 로그에서 18개 테스트 및 line/branch/function 80% 게이트를 확인합니다.
3. `Run Playwright E2E` 로그에서 정상 로그인/가입 전환 시나리오의 성공을 확인합니다.
4. `Verify intentional E2E failure produces screenshot`은 `E2E_FAILURE_DRILL=1`로 동일 시나리오를 의도적으로 실패시킵니다. 비정상 종료, 지정한 실패 메시지, PNG 파일 존재를 모두 검사합니다. 이 검증 자체의 성공은 앱의 예기치 않은 실패를 무시한다는 뜻이 아닙니다.
5. 실행 페이지 Artifacts의 `week12-failure-drill-45004370299`에서 실제 실패 PNG, trace, video, failure-drill.txt를 확인합니다. `week12-test-report-45004370299`에는 coverage-ci.txt 및 정상 E2E HTML 보고서가 있습니다. 다운로드/상세 로그에는 GitHub 로그인이 필요할 수 있습니다. 보관 기간은 90일입니다.

## 레거시 테스트 보강 후 리팩터링

- [테스트 선행 커밋 3988fda](https://github.com/KTheES/ArtGuard/commit/3988fda): HTTP 오류 메시지/코드, 세션 종료 후 늦게 도착한 응답 차단, 바이너리 이미지 반환, 잘못된 JSON 처리의 회귀 테스트 4개 추가. 변경 전 전체 18개 통과.
- [리팩터링 커밋 84de1aa](https://github.com/KTheES/ArtGuard/commit/84de1aa): 요청마다 생성하던 오류 메시지 객체를 공통 불변 상수로 추출. 변경 후 동일한 18개 테스트 및 정상 E2E 통과.
- 최초 TDD 5기능은 2026-10-31의 단일 Red-Green-Refactor 사이클로 구현했으며, 2026-11-22에 재검증했습니다. 기능별 독립적인 5회 사이클이나 5개의 assertion 실패 이력이 있는 것으로 표현하지 않습니다.
