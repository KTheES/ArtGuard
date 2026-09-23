# ArtworkGuard STEP 10 — 비동기 탐지 파이프라인

대상: C:\copyright\_detect\_project

## 동작 변경

기존 STEP 8의 탐지 실행 API는 이제 동기 결과 대신 HTTP 202와 작업 ID를 반환합니다.
경로와 limit 쿼리는 유지되지만 응답 형식이 변경되었습니다.
작품·상품 이미지 임베딩은 기존 STEP 5/7 Kafka 경로를 계속 사용합니다.

수동 요청 → detection_job + detection_outbox → detection.requested → 탐지 엔진 → detection_run + detection → 작업 COMPLETED.

임베딩 저장 → DB 완료 신호 → 모니터링 대상 작품별 작업 등록 → 동일 탐지 경로.

## 설정

백엔드를 실행하는 터미널에 다음 값을 설정합니다.

```powershell
$env:DETECTION_ENABLED='true'
# 새 임베딩도 처리할 때는 기존 설정과 함께 사용
$env:EMBEDDING_ENABLED='true'
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat bootRun
```

기존 DB·Redis·Kafka·JWT 설정이 필요합니다.
새 임베딩 처리에는 기존 S3/AI 설정도 필요하지만, 저장된 벡터의 탐지만 실행할 때는 S3/AI가 필요하지 않습니다.
DETECTION_ENABLED 기본값 false: 작업/신호는 DB에 기록하지만 발행기·소비자·자동 등록기는 실행하지 않습니다.
true로 시작하면 저장된 대기 작업부터 처리합니다.

Flyway V9는 detection_job, detection_outbox, detection_signal 및 임베딩 INSERT 완료 신호 트리거를 생성합니다.
외부 네트워크 호출을 DB 트리거에서 실행하지 않습니다.

## API

| Method | 경로 | 응답 |
|---|---|---|
| POST | /api/v1/artworks/{artworkId}/detections?limit=100 | 202, 작업 등록 |
| GET | /api/v1/artworks/{artworkId}/detection-jobs/{jobId} | 작업 상태 |
| POST | /api/v1/artworks/{artworkId}/detection-jobs/{jobId}/retry | 실패 작업 재시도 |

모두 활성 로그인 사용자와 본인 작품만 허용합니다.
작품 임베딩 미준비 상태의 수동 요청은 기존과 같이 409입니다.
limit은 1~500이며 기본 100입니다.
요청 등록은 탐지 계산이나 AI 응답을 기다리지 않습니다.

```powershell
$headers=@{Authorization="Bearer $accessToken"}
$base="http://localhost:8080/api/v1/artworks/$artworkId"
$queued=Invoke-RestMethod "${base}/detections?limit=100" -Method Post -Headers $headers
$jobId=$queued.data.jobId
$job=Invoke-RestMethod "$base/detection-jobs/$jobId" -Headers $headers
$job.data
# FAILED일 때만 재시도가 새 이벤트를 등록합니다.
Invoke-RestMethod "$base/detection-jobs/$jobId/retry" -Method Post -Headers $headers
```

응답 data:
```json
{
  "jobId": "<uuid>",
  "status": "QUEUED",
  "generation": 1,
  "errorCode": null,
  "runId": null
}
```

상태: QUEUED, COMPLETED, FAILED, CANCELED.
성공 시 runId가 채워집니다. 결과 검토에는 STEP 9의 /api/v1/detections를 사용합니다.
완료·대기·취소 상태에 retry를 호출하면 기존 상태를 그대로 반환합니다.
다시 탐지하려면 최초 POST를 호출해 새 작업을 등록합니다.
수동 POST를 반복하면 각각 새 실행입니다. 동일 작업의 Kafka 중복 전달만 중복 계산을 방지합니다.

## 자동 탐지

artwork.monitoring_enabled=true인 작품만 대상입니다.
기존 작품 수정 API의 monitoringEnabled를 사용해 켤 수 있습니다. 수정에는 현재 artwork.version이 필요합니다.

- 작품 임베딩 INSERT: 해당 작품이 활성·모니터링 상태이고 모델이 맞으면 작업 등록.
- 상품 이미지 임베딩 INSERT: 같은 모델·전처리의 작품 임베딩이 준비된 활성 모니터링 작품에 작업 등록.
- 상품 신호는 현재 활성 상품 이미지 벡터인 경우에만 처리.
- 한 번에 최대 100개 작품에 등록하고 UUID 커서를 같은 트랜잭션에서 저장.
- 신호 ID + 작품 ID 고유 키로 등록 중복 방지.
- 소비 시 사용자/작품/모니터링 상태를 다시 확인해 유효하지 않으면 CANCELED.
- 자동 실행의 결과 한도는 100.

마이그레이션 이전 임베딩 전체를 자동 재처리하지 않습니다.
이미 완료된 신호 뒤에 모니터링을 켜는 경우 첫 탐지는 수동 POST로 실행해야 합니다.
각 임베딩 완료는 별도 신호입니다. 여러 이미지가 완료되면 같은 작품에 여러 탐지 실행이 생길 수 있습니다.
완료 신호를 놓치지 않기 위해 신호와 벡터는 동일 DB 트랜잭션으로 저장합니다.

## 장애와 중복

- Outbox는 Kafka 전송 확인 후 발행 완료 처리, 최대 10회 시도.
- 소비 실패는 최초 실행 후 2회 재시도. detection.requested.dlt 전송 확인 후 FAILED 기록.
- 수동 재시도는 generation을 증가시키므로 이전 세대 메시지는 무시.
- 완료 메시지 중복은 새 detection_run을 생성하지 않음.
- 탐지 결과·runId·작업 COMPLETED는 하나의 트랜잭션으로 저장.
- 작업의 threshold 및 모델 비교는 실제 처리 시점의 엔진 설정을 사용하며, 실행 기준은 detection_run에 보관.
- 탐지 소비 트랜잭션 제한은 45초. 큐 등록은 작품 잠금을 기다리도록 설계하지 않았지만 DB/브로커 장애 시 처리 지연은 발생할 수 있음.

등록 커서·완료 작업·신호·DLT의 보관 기간 및 정리 작업은 후속 운영 과제입니다.
수동 요청 속도 제한과 여러 완료 신호의 병합은 아직 적용하지 않았습니다.

## 검증

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test bootJar --no-daemon
.\gradlew.bat integrationTest --tests '*DetectionQueueIntegrationTest' --no-daemon
```

단위/MVC 테스트: 202 응답, 인증·소유자 전달, 실패 재시도 세대, 완료 중복 무시,
모니터링 취소, 100건 등록·커서, Outbox 확인 후 발행과 제한 재시도.
통합 테스트는 PostgreSQL·Redis·Kafka 컨테이너와 알려진 단위 벡터를 사용해
수동 큐 → Kafka → 탐지 결과, 중복 전달, 모니터링 비활성 제외, 상품 임베딩 완료 자동 탐지를 검증하도록 작성했습니다.
실제 AI 정확도나 전체 S3/AI 처리 검증을 대체하지 않습니다.

다음은 개발 계획의 STEP 11 Redis Optimization입니다.
