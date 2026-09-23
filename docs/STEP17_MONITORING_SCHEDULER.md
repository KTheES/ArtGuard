# STEP 17 Monitoring Scheduler

`monitoring_enabled=true`인 활성 사용자의 작품을 설정된 주기마다 자동 탐지 큐에 등록합니다. 작품에 현재 DINOv2 임베딩이 있어야 스캔 대상이 됩니다.

## 실행 방식

- 시간 구간마다 `monitoring_scan_cycle` 한 개를 생성합니다. `window_start` unique 제약으로 여러 서버의 중복 cycle을 막습니다.
- 가장 오래된 미완료 cycle을 `FOR UPDATE SKIP LOCKED`로 한 서버만 처리합니다.
- 작품 UUID 순서와 `cursor_artwork_id`를 사용해 기본 100개씩 진행합니다.
- 각 작품은 기존 `detection_job`·outbox·Kafka 흐름에 `automatic=true`로 등록됩니다.
- 작업 source key는 `schedule:{cycleId}:{artworkId}`라 같은 cycle의 재시도에서도 중복 작업이 생기지 않습니다.
- 작품이나 계정이 비활성화되면 스케줄 대상에서 제외되며, 큐 등록 후 비활성화된 경우 탐지 작업자가 취소합니다.

## 설정

기본값은 비활성입니다. STEP 18부터 스케줄러는 1시간마다 대상 여부를 평가하고 실제 재검사 주기는 구독 플랜이 결정합니다.

```text
MONITORING_SCHEDULER_ENABLED=false
MONITORING_SCAN_INTERVAL=PT1H
MONITORING_BATCH_SIZE=100
MONITORING_RESULT_LIMIT=100
```

평가 주기는 5분~31일, batch와 결과 제한은 1~500 범위입니다. 실제 탐지 소비까지 실행하려면 `DETECTION_ENABLED=true`와 Kafka 연결도 필요합니다. 탐지 소비가 꺼져 있으면 생성된 작업과 outbox는 DB에 대기합니다.

## 검증

- 백엔드 단위 테스트 185개 통과
- 실행 JAR 생성 성공
- 고정 시계로 시간 버킷 계산, batch 상한, UUID 커서 재개, 빈 cycle 완료 검증
- V15 적용 후 모니터링 작품의 작업 생성과 같은 cycle 중복 방지 통합 시나리오 작성
- Docker 엔진 부재로 PostgreSQL·Kafka 통합 시나리오는 실행하지 못함

현재 스케줄은 애플리케이션이 실행 중인 동안 동작합니다. 장기 중단 기간의 누락된 모든 과거 구간을 소급 생성하지 않고, 재시작 시 현재 구간부터 다시 시작합니다.
