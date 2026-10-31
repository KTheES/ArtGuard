# [11주차] Feature Flags 및 점진적 배포 실습 — 제출 증빙

검증일: **2026-10-31**

| 요구사항 | 결과 | 증거 링크 |
|---|---|---|
| Feature Flag 3개 이상 | ✅ | [`feature-flags.js`](../frontend/feature-flags.js) · [`runtime-config.mjs`](../frontend/runtime-config.mjs) |
| 환경 변수/대상 사용자 토글 | ✅ | [환경 변수 예시](../.env.example) · [단위 테스트](../frontend/feature-flags.test.js) |
| A/B 2개 variant와 일관된 할당 | ✅ | [`control`/`treatment` 배정 코드](../frontend/feature-flags.js) · [일관성 테스트](../frontend/feature-flags.test.js) |
| 이벤트 추적 | ✅ | [익명화된 실험 이벤트 로그](../reports/week11/experiment-events.json) · [로그 생성기](../frontend/scripts/simulate-experiment.mjs) |
| Canary 1%-10%-50%-100% | ✅ 선택과제 | [롤아웃 설정](../ops/canary-rollout.mjs) · [자동 롤백 결과](../reports/week11/canary-rollout.json) |
| CI 검증/Artifact | ✅ | [성공 실행 #1](https://github.com/KTheES/ArtGuard/actions/runs/41195803435) · [90일 보관 Artifact](https://github.com/KTheES/ArtGuard/actions/runs/41195803435/artifacts/13040198061) |

## 1. Feature Flag 구성

| Flag | 앱 동작 | 전체 토글 | 대상 사용자 | 점진 비율 |
|---|---|---|---|---|
| `enhancedUpload` | 향상된 작품 업로드 문구와 흐름 | `FF_ENHANCED_UPLOAD` | `FF_ENHANCED_UPLOAD_USERS` | `FF_ENHANCED_UPLOAD_PERCENT` |
| `confidenceBadges` | 탐지 카드 신뢰도 배지 | `FF_CONFIDENCE_BADGES` | `FF_CONFIDENCE_BADGES_USERS` | `FF_CONFIDENCE_BADGES_PERCENT` |
| `compactNavigation` | 축소형 사이드 내비게이션 | `FF_COMPACT_NAV` | `FF_COMPACT_NAV_USERS` | `FF_COMPACT_NAV_PERCENT` |

평가 우선순위는 **전체 활성화 → 대상 사용자 일치 → 사용자별 안정적 퍼센트 버킷**입니다. 서버 실행 시 환경 변수로 생성한 `/runtime-config.js`를 브라우저가 읽습니다. 정적 빌드에서도 빌드 시점 환경 변수를 동일한 런타임 설정 파일로 생성합니다.

예시:

```bash
FF_ENHANCED_UPLOAD=false
FF_ENHANCED_UPLOAD_USERS=pilot@example.com
FF_COMPACT_NAV_PERCENT=10
```

이 경우 향상된 업로드는 `pilot@example.com`에게만 표시되고, compact navigation은 사용자 식별자의 결정적 해시 버킷 기준 10%에게 표시됩니다.

## 2. A/B 테스트와 이벤트 추적

- 실험 키: `upload-cta-v1`
- variant A: `control` — **작품 등록**
- variant B: `treatment` — **첫 작품 보호하기**
- 할당 방식: `실험 키 + 사용자 ID`의 결정적 FNV-1a 해시, 50:50
- 추적 이벤트: `exposure`, `upload_cta_click`
- 저장 위치: 브라우저 `localStorage`의 `artguard-experiment-events`와 개발자 콘솔
- 개인정보 보호: 이메일 원문 대신 `user-XX` 형태의 익명화 버킷만 로그에 저장

동일 사용자는 새로고침하거나 다시 로그인해도 같은 variant에 배정됩니다. 단위 테스트는 100명의 샘플에서 재할당 결과가 완전히 동일하며 두 variant가 모두 생성되는지 검증합니다.

## 3. Canary와 헬스체크 자동 롤백

[`ops/canary-rollout.mjs`](../ops/canary-rollout.mjs)는 다음 단계를 순서대로 승격합니다.

```text
1% → health check → 10% → health check → 50% → health check → 100%
```

`--health-url`을 지정하면 각 단계마다 5초 제한으로 실제 HTTP 헬스체크를 실행합니다. 검증 시나리오는 `--simulate-failure=50`을 사용해 50% 단계 장애를 재현하며, 직전 정상 단계인 **10%로 자동 롤백**합니다.

```bash
node ops/canary-rollout.mjs \
  --simulate-failure=50 \
  --output=reports/week11/canary-rollout.json
```

CI는 플래그/A-B 단위 테스트, 프런트엔드 lint/build, Canary 롤백 테스트를 실행하고 두 JSON 로그를 90일 Artifact로 보관합니다.

- 최신 검증: [Week 11 Feature Flags 실행 #1 — Success](https://github.com/KTheES/ArtGuard/actions/runs/41195803435)
- 실행 Job: [Flags, experiment, and canary rollback](https://github.com/KTheES/ArtGuard/actions/runs/41195803435/job/124521590590)

## 제출 URL

<https://github.com/KTheES/ArtGuard/blob/main/docs/WEEK11_FEATURE_FLAGS.md>
