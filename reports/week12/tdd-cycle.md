# TDD Red-Green-Refactor 기록

검증일: 2026-10-31

## Red

- 커밋: [`243f5e8`](https://github.com/KTheES/ArtGuard/commit/243f5e8)
- 구현 파일 없이 `delivery-policy.test.js`의 계약 테스트 5개를 먼저 작성
- 결과: `ERR_MODULE_NOT_FOUND: delivery-policy.js`, 테스트 실패(exit 1)

## Green

- 커밋: [`d5ffa98`](https://github.com/KTheES/ArtGuard/commit/d5ffa98)
- 다섯 핵심 기능의 최소 구현 추가
- 결과: 5 tests, 5 pass, 0 fail

## Refactor

- 커밋: [`966b8ab`](https://github.com/KTheES/ArtGuard/commit/966b8ab)
- Feature Flag, runtime config, Canary 레거시 로직의 중복을 공통 `delivery-policy.js`로 이동
- 결과: 14 tests, 14 pass, line 96.62%, branch 83.54%, function 85.71%

## 핵심 기능 5개

1. `normalizeIdentity` — 사용자 식별자 정규화
2. `parseCsvTargets` — 대상 사용자 설정 파싱·중복 제거
3. `clampRolloutPercentage` — 잘못된 롤아웃 비율 보정
4. `assignWeightedVariant` — 결정적 가중 A/B variant 할당
5. `decideCanaryTransition` — 헬스체크 기반 승격/롤백 결정
