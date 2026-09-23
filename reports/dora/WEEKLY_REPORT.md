# DORA 주간 보고서 — SAMPLE DATA

- 저장소: `KTheES/ArtGuard`
- 생성 시각: `2026-09-23T00:00:00Z`
- 관측 구간: `2026-06-25T00:00:00Z` ~ `2026-09-23T00:00:00Z`
- 환경 정규식: `^prod(uction)?$`
- Incident 라벨: `incident`

> [!WARNING]
> 이 보고서는 대시보드 검증용 샘플 데이터입니다. GitHub Actions가 실행되면 실측 결과로 교체됩니다.

## DORA 4대 지표

| 지표 | 값 | 표본 | 해석 |
| --- | ---: | --- | --- |
| Lead Time for Changes | 2.5h | n=4, p90=5.1h | 낮을수록 좋음 |
| Deployment Frequency | 0.31/week | 성공 배포 4건 | 높을수록 좋음 |
| Mean Time to Restore | 3.5h | incident 2건 | 낮을수록 좋음 |
| Change Failure Rate | 33.33% | 실패 2/6 | 낮을수록 좋음 |

## 주간 추이

| 주 시작 | 성공 배포 | 실패 배포 | Lead Time 중앙값(h) | MTTR 평균(h) | CFR(%) |
| --- | ---: | ---: | ---: | ---: | ---: |
| 2026-06-22 | 0 | 0 | N/A | N/A | N/A |
| 2026-06-29 | 0 | 0 | N/A | N/A | N/A |
| 2026-07-06 | 0 | 0 | N/A | N/A | N/A |
| 2026-07-13 | 0 | 0 | N/A | N/A | N/A |
| 2026-07-20 | 0 | 0 | N/A | N/A | N/A |
| 2026-07-27 | 0 | 0 | N/A | N/A | N/A |
| 2026-08-03 | 1 | 0 | 3 | N/A | 0 |
| 2026-08-10 | 1 | 1 | 2 | 2 | 50 |
| 2026-08-17 | 0 | 0 | N/A | N/A | N/A |
| 2026-08-24 | 1 | 0 | 6 | N/A | 0 |
| 2026-08-31 | 0 | 0 | N/A | N/A | N/A |
| 2026-09-07 | 1 | 0 | 1.5 | N/A | 0 |
| 2026-09-14 | 0 | 1 | N/A | 5 | 100 |
| 2026-09-21 | 0 | 0 | N/A | N/A | N/A |

## 데이터 품질

- Lead Time과 연결되지 않은 성공 배포: **0건**
- Lead Time 연결 기준: `pull_request.merge_commit_sha == deployment.sha`
- Lead Time은 deployment.sha와 Pull Request merge_commit_sha가 일치해야 계산됩니다.
- MTTR은 설정된 incident 라벨의 종료 이슈를 복구 기록으로 사용합니다.
- Change Failure Rate는 failure/error 상태의 운영 배포를 변경 실패의 대리값으로 사용합니다.

## 운영 규칙

- 운영 배포 워크플로우는 GitHub Deployment와 terminal deployment status를 생성해야 합니다.
- 장애 이슈에는 설정된 incident 라벨을 붙이고 복구 완료 시 이슈를 닫습니다.
- 배포 롤백·핫픽스가 실패 배포와 연결되지 않으면 CFR이 실제보다 낮게 보일 수 있습니다.
- 표본이 없을 때는 0이 아니라 `N/A`로 표시합니다.
