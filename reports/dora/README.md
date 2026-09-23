# DORA 메트릭 운영 가이드

이 디렉터리는 GitHub Actions가 생성한 최신 DORA 측정 결과를 보관합니다.

- [`latest.json`](./latest.json): 대시보드와 후속 자동화가 읽는 구조화 데이터
- [`WEEKLY_REPORT.md`](./WEEKLY_REPORT.md): 사람이 검토하는 주간 보고서
- [대시보드](../../dashboard/): JSON을 시각화하는 정적 Chart.js 화면

## 지표 정의

| 지표 | 이 저장소의 계산 기준 |
| --- | --- |
| Lead Time for Changes | PR의 `merged_at`부터 같은 merge SHA가 운영 환경에 처음 성공 배포된 시점까지의 중앙값과 p90 |
| Deployment Frequency | 관측 기간의 성공한 운영 배포 수를 주 단위로 환산한 값 |
| Mean Time to Restore | `incident` 라벨 이슈의 생성부터 종료까지 걸린 시간의 평균 |
| Change Failure Rate | 실패 또는 오류 상태의 운영 배포 수 ÷ terminal 상태의 전체 운영 배포 수 |

## 측정을 위한 저장소 운영 규칙

1. 배포 파이프라인은 GitHub Deployment를 만들고 `success`, `failure`, `error` 중 하나의 최종 Deployment Status를 기록합니다.
2. Deployment의 `sha`에는 배포된 PR의 merge commit SHA가 들어가야 Lead Time과 연결됩니다.
3. 운영 장애는 `incident` 라벨의 GitHub Issue로 만들고 서비스가 복구된 시점에 닫습니다.
4. 운영 환경 이름이 기본 정규식 `^prod(uction)?$`와 다르면 수동 실행 입력값 또는 워크플로우 기본값을 수정합니다.

## 자동 실행

`.github/workflows/dora-metrics.yml`은 매주 월요일 오전 9시(KST)에 실행됩니다. Actions의 **Run workflow**에서 관측 기간, 운영 환경 정규식, incident 라벨을 바꿔 수동 실행할 수도 있습니다.

각 실행은 JSON·Markdown·SVG 파일을 90일 보관 아티팩트로 업로드합니다. 최신 결과는 저장소에도 커밋을 시도합니다. 브랜치 보호로 자동 push가 차단되어도 아티팩트는 그대로 받을 수 있습니다.

실제 배포 플랫폼이 아직 연결되지 않은 동안에는 `.github/workflows/record-production-deployment.yml`을 사용합니다. 외부 또는 로컬 운영 배포가 실제로 끝난 뒤에만 **Record Production Deployment**를 수동 실행하고, 배포된 commit SHA와 실제 결과를 입력합니다. 이 워크플로우는 애플리케이션을 배포하지 않으며 GitHub Deployment 이력만 기록합니다.

## 한계

- 여러 PR을 squash/rebase하거나 배포 SHA가 PR merge SHA와 다르면 Lead Time 표본이 누락될 수 있습니다.
- 이슈 종료 시간을 복구 시점의 대리값으로 사용하므로 실제 서비스 복구와 이슈 종료 사이의 지연이 MTTR에 포함됩니다.
- 실패 배포 없이 사후 롤백만 기록하는 운영 방식에서는 Change Failure Rate가 실제보다 낮게 계산될 수 있습니다.
- 표본이 없으면 `0`이 아닌 `null`/`N/A`로 표시합니다.
