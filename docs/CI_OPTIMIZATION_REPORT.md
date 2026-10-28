# CI 워크플로우 최적화 전후 비교 리포트

## 목적과 결정 배경

기존 `Application CI`는 Ubuntu/Windows와 Node.js 22/24를 검증했지만, Matrix 단계의 설정이 주 워크플로우에 반복되어 있었고 모든 관련 브랜치 실행에서 동일한 설치 작업을 수행했다. 8주차 과제로 재사용 구조, 정확한 의존성 캐시, 변경 파일 기반 선택 실행을 도입했다.

## 구조 비교

| 항목 | 최적화 전 | 최적화 후 |
|---|---|---|
| Matrix | Ubuntu/Windows × Node.js 22/24, 4조합 | Ubuntu/Windows/macOS × Node.js 22/24, 6조합 |
| 중복 제거 | 주 워크플로우에 Matrix 단계 직접 작성 | `reusable-node-quality.yml` 호출 |
| 공통 설정 | 각 job에서 Node 설정과 `npm ci` 반복 | `setup-node-project` Composite Action |
| 캐시 | npm 다운로드 캐시를 사용하되 매번 `npm ci` 실행 | OS·실제 Node 버전·lockfile 기준 `node_modules` exact cache |
| 변경 감지 | 관련 브랜치에서 항상 Build/Test | `paths-filter`로 프런트엔드 CI 파일 변경 시에만 실행 |
| Deploy | main의 CI 성공 시 실행 | main + deployable 파일 변경 + Build/Test 성공 시에만 실행 |

## 재사용 구조

```text
Application CI
  ├─ Detect relevant changes
  ├─ Build and lint ───────────────┐
  │    └─ Composite Action         │ build artifact
  ├─ Reusable Node Quality × 6 ◀──┘
  │    └─ Composite Action
  └─ Conditional CI Preview Deploy

CI Cache Benchmark
  └─ no cache → seed cache → warm cache → report artifact
```

## 캐시 전후 측정 결과

- 측정 실행: [CI Cache Benchmark #40628257105](https://github.com/KTheES/ArtGuard/actions/runs/40628257105)
- 원본 아티팩트: [ci-cache-benchmark-40628257105](https://github.com/KTheES/ArtGuard/actions/runs/40628257105/artifacts/12790321832)
- 대상 commit: `01699bb572940e9731c28c797f97ce24e75cb3b3`
- 환경: `ubuntu-latest`, Node.js `22.x`, 동일 `package-lock.json`
- 측정 구간: 캐시 복원 시작 직전부터 `npm ci` 실행 또는 exact cache 확인 완료까지
- warm cache hit: `true`

| 조건 | 실행 시간 |
|---|---:|
| 캐시 비활성, `npm ci` | 3,029ms |
| warm cache, 복원 시간 포함 | 1,600ms |
| 절감 시간 | 1,429ms |
| 개선률 | **47.18%** |

계산식은 `(3,029 - 1,600) ÷ 3,029 × 100 = 47.18%`이다. GitHub-hosted runner의 네트워크와 부하에 따라 값은 달라질 수 있으므로, 이 결과는 동일 실행 내 순차 비교의 측정 스냅샷으로 해석한다.

## 조건부 실행 규칙

1. PR 및 `feat/**`, `fix/**` push는 변경 파일을 분류한다.
2. `frontend/**`, Composite Action 또는 Reusable/Application workflow가 바뀐 경우에만 Build와 Matrix Test를 실행한다.
3. Deploy는 `main`에서 실제 배포 가능한 프런트엔드·인프라 파일이 바뀌고 Build/Test가 모두 성공했을 때만 실행한다.
4. `workflow_dispatch`는 점검 목적이므로 변경 감지 결과와 무관하게 전체 파이프라인을 실행한다.
5. PR과 기능 브랜치는 Environment Secret 보호를 위해 Deploy하지 않는다.

## 검증 증적

- [확장 Matrix 성공 실행](https://github.com/KTheES/ArtGuard/actions/runs/40628257167)
- [Cache Benchmark 성공 실행](https://github.com/KTheES/ArtGuard/actions/runs/40628257105)
- Matrix 6조합, Build/Lint, 변경 감지 성공
- 기능 브랜치 Deploy가 조건에 따라 `skipped` 처리됨
- 벤치마크의 uncached, seed, cached, report 4개 job 성공

## 후속 권장사항

- 월 1회 또는 lockfile 대규모 변경 시 벤치마크를 수동 재실행해 중앙값 추이를 기록한다.
- GitHub Actions 정책 확정 후 외부 Action 태그를 검증된 commit SHA로 고정한다.
- 캐시 용량과 eviction 빈도를 점검하고, 네이티브 의존성이 추가되면 OS·Node ABI 키를 다시 검토한다.
