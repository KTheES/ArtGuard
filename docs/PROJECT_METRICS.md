# GitHub Project 운영 및 흐름 지표

ArtworkGuard의 개발 백로그는 GitHub Project에서 `Backlog → To Do → In Progress → Review → Done` 순서로 관리합니다. 이슈는 반드시 우선순위, 작업 영역, 마일스톤과 Story Points를 갖도록 운영합니다.

## 스프린트 운영 규칙

- 스프린트 계획 시 `Backlog`에서 이번 마일스톤 범위만 `To Do`로 이동합니다.
- 실제 작업을 시작할 때 `In Progress`로 이동하고 Start Date를 입력합니다.
- Pull Request를 열면 `Review`, 병합과 검증이 끝나면 `Done`으로 이동하고 이슈를 닫습니다.
- 긴급 장애는 `incident`, 결함은 `type:bug`, 신규 기능은 `type:feature` 라벨을 사용합니다.
- 진행 중인 작업은 한 사람당 1~2개로 제한하여 WIP 증가를 방지합니다.

## Cycle Time

Cycle Time은 이슈가 `In Progress`로 이동한 시점부터 `Done`이 된 시점까지의 시간입니다. 초기 기준선은 저장소 분리 및 DORA 구성 완료 이슈이며, 자동화가 충분한 이력을 쌓기 전까지는 Project의 Start Date와 이슈 종료 시각을 사용해 수동 검증합니다.

권장 지표:

- 중앙값 Cycle Time
- p85 Cycle Time
- 7일을 초과한 진행 중 이슈 수

## Velocity

Velocity는 마일스톤 종료 시점에 `Done` 상태로 완료된 Story Points 합계입니다. 부분 완료 작업은 포함하지 않습니다. 두 스프린트의 평균 Velocity를 다음 스프린트 계획 상한으로 사용합니다.

## Burndown

Burndown의 시작값은 마일스톤에 포함된 전체 Story Points입니다. 매일 `Done`으로 이동한 포인트를 차감하여 이상적인 직선과 비교합니다.

```text
Remaining Points = Sprint Total Points - Done Points
```

GitHub Project의 마일스톤 필터와 Story Points 합계를 사용해 스프린트별 잔여 작업을 확인합니다. 하루 이상 갱신되지 않은 `In Progress` 작업과 목표일을 넘긴 작업은 스탠드업에서 확인합니다.

## 초기 분석 기준

- 완료된 기반 작업 2개를 초기 Velocity 기준선으로 사용합니다.
- 신규 백로그가 같은 날 생성되므로 첫날 Cycle Time은 성과 비교에 사용하지 않습니다.
- 유효한 Cycle Time과 Burndown 추세는 실제 스프린트 운영 후 갱신합니다.
