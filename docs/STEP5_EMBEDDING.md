# STEP 5 — 작품 임베딩 작업과 pgvector

## 동작

작품 등록 DB 트랜잭션에 다음을 함께 기록합니다.

1. artwork 및 업로드 완료 상태
2. embedding_job
3. embedding_outbox

API는 AI 추론을 기다리지 않고 작품 정보를 반환합니다. EMBEDDING_ENABLED=true이면 별도 스케줄러가 Outbox를 Kafka의 embedding.requested로 발행합니다.
Kafka 작업자는 소유 계정과 작품 상태를 확인하고 정규화 이미지의 60초 Signed GET URL을 발급해 AI 서비스에 전달합니다.
응답의 모델·모델 리비전·전처리 버전·차원·유한 값·L2 정규화를 검증한 뒤 vector(768)에 저장합니다.
원본 이미지 URL과 서비스 API 키는 Kafka 이벤트/Outbox에 넣지 않습니다.

## DB 변경

Flyway V4가 다음 테이블을 추가합니다.

- embedding_job: 작품별 모델/전처리 버전, 상태, 요청 세대, 실패 코드
- embedding_outbox: 이벤트, 발행 시도 횟수, 다음 시각, 발행/실패 여부
- artwork_embedding: vector(768), 모델 리비전, 전처리 버전, 생성 시각

V1의 pgvector 확장을 사용합니다. 벡터 차원이 고정되어 있으며 다른 모델·전처리 버전의 벡터를 혼합하지 않습니다.
현재는 임베딩 저장 단계입니다. 상품 벡터·유사 상품 검색·Detection 생성은 이후 단계입니다.

## API

기존 인증 및 작품 소유권 검사를 그대로 적용합니다.

| API | 설명 |
|---|---|
| POST /api/v1/artworks | 신규 작품 등록과 동시에 작업/Outbox 생성 |
| POST /api/v1/artworks/{id}/embedding | 기존 작품 최초 요청 또는 FAILED 작업 재요청 |
| GET /api/v1/artworks/{id}/embedding | 본인 작품의 작업 상태 조회 |

반환 data: jobId, status, generation, errorCode, dimension.
QUEUED는 대기 또는 처리 중이며, dimension은 예정 차원입니다. COMPLETED에서 실제 저장이 완료됩니다.
FAILED는 수동 재요청할 수 있습니다. CANCELED는 삭제된 작품 또는 활성 상태가 아닌 계정에 대한 처리 취소입니다.
이전 단계에서 등록한 작품은 NOT_REQUESTED이며 POST로 요청하면 됩니다.
다른 사용자의 작품 및 삭제 작품은 404를 반환합니다. 원시 벡터를 이 API로 노출하지 않습니다.

## 중복·재시도·실패 처리

- Outbox와 작품/작업이 하나의 DB 트랜잭션에 저장됩니다.
- Kafka ACK를 받은 다음 Outbox를 발행 완료로 표시합니다. DB 커밋 실패 시 재발행될 수 있습니다.
- Kafka 수신 처리 중 작업 행을 잠그고 상태/세대를 확인합니다. 완료된 중복 이벤트는 AI를 다시 호출하지 않습니다.
- 실패 후 재요청하면 generation을 증가시키므로 이전 이벤트가 새 작업을 덮어쓰지 않습니다.
- 벡터 저장과 COMPLETED 전환은 같은 트랜잭션입니다.
- Kafka 발행은 Outbox당 최대 10회, 지수 지연 최대 300초입니다. 초과하면 EVENT_PUBLISH_FAILED 상태가 됩니다.
- 작업 처리의 일시 오류는 수신 처리당 최초 시도 + 2회 재시도 후 embedding.requested.dlt로 보냅니다.
- 잘못된 이벤트/벡터 계약은 재시도 없이 DLT로 보냅니다.
- DLT 발행이 확인된 뒤 AI_PROCESSING_FAILED로 표시합니다. DLT 자체를 발행하지 못하면 원본 offset을 확정하지 않으므로 브로커 복구 과정에서 재처리될 수 있습니다.
- 이것은 at-least-once 전달 및 DB 중복 방지이며 분산 exactly-once를 주장하지 않습니다.

소유권 확인·삭제와 이미 발급된 URL의 동작은 STEP 3 정책을 따릅니다. 논리 삭제 후 기존 벡터/이미지를 즉시 물리 삭제하지 않습니다.
작업 행 잠금을 유지한 상태에서 AI를 호출하는 초기 구현입니다. AI 연결 timeout은 3초, 응답은 90초, 작업 트랜잭션은 120초입니다.
모델이 커지거나 작업량이 늘면 lease 기반 작업 분리를 검토해야 합니다.
Kafka 토픽은 로컬 개발용 1 partition/replica입니다. 운영 환경의 복제 수·보안·감시 설정은 별도 적용이 필요합니다.

## 실행 순서 (PowerShell)

1. STEP 3 안내로 PostgreSQL, Redis, Kafka, MinIO와 비공개 버킷을 준비합니다.
2. ai-service README대로 CPU PyTorch·의존성과 고정 모델 캐시를 준비합니다.
3. AI와 백엔드에 동일한 AI_API_KEY를 설정합니다. 키는 32자 이상이어야 합니다.
4. AI_ALLOWED_IMAGE_ORIGINS가 백엔드 S3_ENDPOINT와 정확히 일치하도록 설정합니다.

AI 실행 창:

```powershell
Set-Location C:\copyright\_detect\_project\ai-service
# 앞서 안전하게 준비한 동일한 키 사용
$env:AI_API_KEY = $sharedAiKey
$env:AI_ALLOWED_IMAGE_ORIGINS = 'http://localhost:9000'
$env:AI_ALLOW_PRIVATE_IMAGES = 'true'
.\scripts\start.ps1
```

백엔드 실행 창: 기존 DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET 및 S3 변수도 필요합니다.

```powershell
Set-Location C:\copyright\_detect\_project\backend
$env:AI_API_KEY = $sharedAiKey
$env:AI_SERVICE_URL = 'http://127.0.0.1:8001'
$env:EMBEDDING_ENABLED = 'true'
.\gradlew.bat bootRun
```

별도 PowerShell 창의 변수는 공유되지 않습니다. $sharedAiKey는 두 창에서 동일한 값으로 준비해야 합니다.
EMBEDDING_ENABLED의 기본값은 false입니다. false에서도 작품 등록 시 작업은 QUEUED로 저장되며, 나중에 true로 실행하면 전송을 시작합니다.
이 옵션은 기존 인증/작품 기능을 AI·Kafka 없이 실행할 수 있게 합니다.
DB 비밀번호를 새로 생성하면 기존 DB 볼륨과 불일치할 수 있으므로 기존 값을 재사용하세요.

로그인 및 작품 등록 후:

```powershell
$auth = @{Authorization="Bearer $($tokens.accessToken)"}
$base = 'http://localhost:8080/api/v1/artworks'
Invoke-RestMethod "$base/$($artwork.id)/embedding" -Headers $auth
# 이전 단계 작품 또는 FAILED 작업 재요청
Invoke-RestMethod "$base/$($artwork.id)/embedding" -Method Post -Headers $auth
```

## 검증

```powershell
# backend
.\gradlew.bat test bootJar
.\gradlew.bat integrationTest
```

EmbeddingIntegrationTest는 실제 PostgreSQL/pgvector·Redis·Kafka 컨테이너와 로컬 AI 응답 서버를 사용합니다.
작업 → Outbox → Kafka → AI HTTP → vector(768) 저장 및 중복 이벤트의 단일 저장을 검사합니다.
AI 응답 서버는 이 테스트에서 스텁이며, 실제 DINOv2는 STEP 4의 별도 실제 모델 테스트로 검증합니다.
실행한 결과와 미검증 항목은 STEP45_STATUS.md를 참고하세요.

다음 단계: STEP 6 Marketplace/Seller/Product/ProductImage 도메인과 Mock Marketplace.

공식 참고: [Spring Kafka 오류 처리](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html), [pgvector](https://github.com/pgvector/pgvector).
