> STEP 11부터 반복 수집에는 검색 캐시와 상품 중복 처리 생략이 적용됩니다. 즉시 상품 임베딩 재시도는 이미지 embedding POST를 사용하세요. 현재 정책: [STEP11_REDIS](STEP11_REDIS.md).

# ArtworkGuard STEP 7 — 상품 이미지 임베딩

대상 프로젝트: C:\copyright\_detect\_project

## 구현

Mock 상품 수집 트랜잭션 안에서 상품 이미지별 임베딩 작업과 Outbox를 함께 기록합니다.
이미지 ID, SHA-256, 모델 ID/버전, 전처리 버전이 같은 작업은 중복 생성하지 않습니다.

처리 순서:
1. product_embedding_outbox → product.embedding.requested Kafka 토픽
2. 활성 상품·이미지·마켓 및 현재 이미지 해시 확인
3. 번들 이미지의 실제 SHA-256 검증
4. private S3에 product-images/{imageId}/{hash}.png 저장
5. 60초 서명 GET URL로 기존 AI API 호출
6. product_image_embedding의 vector(768) 저장과 작업 COMPLETED 처리를 같은 트랜잭션으로 완료

Flyway V6가 product_embedding_job, product_embedding_outbox, product_image_embedding과
current_product_image_embedding 뷰를 생성합니다.
뷰는 비활성 이미지/상품/마켓과 현재 해시가 다른 과거 벡터를 제외합니다.
STEP 8 검색에서는 이 뷰에 작품 임베딩과 동일한 모델·전처리 버전 조건도 적용해야 합니다.

## 실행 설정

기존 STEP 3 S3/MinIO, STEP 4 AI, STEP 5 PostgreSQL·Redis·Kafka 설정이 필요합니다.

백엔드 실행 PowerShell:

```powershell
$env:EMBEDDING_ENABLED='true'
$env:S3_ENABLED='true'
$env:MOCK_MARKETPLACE_ENABLED='true'
$env:AI_SERVICE_URL='http://127.0.0.1:8001'
# AI_API_KEY는 AI 서비스와 동일한 32자 이상의 값
# JWT_SECRET, DB/Redis/Kafka 및 S3 버킷/엔드포인트/자격 증명은 기존 설정 사용
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat bootRun
```

AI 서비스는 별도 터미널에서 시작합니다.
로컬 MinIO를 사용하는 경우 AI_ALLOWED_IMAGE_ORIGINS를 서명 URL의 origin과 정확히 일치시키고
AI_ALLOW_PRIVATE_IMAGES=true를 사용합니다. 예: http://localhost:9000.
실제 S3에서는 해당 HTTPS origin만 허용하고 private 옵션은 false로 유지합니다.
가상 주소 mock.artworkguard.invalid로 네트워크 요청을 보내지 않습니다.

EMBEDDING_ENABLED=false여도 수집 시 DB 작업은 QUEUED로 등록되지만 발행기와 소비자는 실행되지 않습니다.
S3가 꺼져 있거나 AI 접근 설정이 틀리면 처리가 실패하며 재시도 후 FAILED가 됩니다.

## 수집 및 기존 상품 처리

관리자 토큰으로 STEP 6 수집 API를 다시 호출하면 기존 상품 이미지에도 작업을 등록합니다.
완료된 동일 해시 작업은 재처리하지 않습니다.

```powershell
$headers=@{Authorization="Bearer $accessToken"}
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/admin/marketplaces/MOCK/collect' -Headers $headers -ContentType 'application/json' -Body '{}'
```

상품 목록과 상세에서 productId, imageId를 확인합니다.

| Method | 경로 | 권한 |
|---|---|---|
| GET | /api/v1/products/{productId}/images/{imageId}/embedding | 활성 로그인 사용자 |
| POST | /api/v1/products/{productId}/images/{imageId}/embedding | 관리자, DB 역할 재확인 |

POST는 본문 없이 작업 등록 또는 실패 작업 재시도를 실행합니다.
상품 ID와 이미지 ID의 소속 관계도 확인합니다.
응답에는 jobId/status/generation/errorCode/dimension이 있으며 벡터나 서명 URL은 포함하지 않습니다.
상태는 NOT_REQUESTED, QUEUED, COMPLETED, FAILED, CANCELED입니다.

## 중복·변경·장애 처리

- 실패·취소된 작업 재요청 시 generation을 증가시켜 이전 메시지를 무시합니다.
- 완료 메시지 중복 소비는 S3 업로드와 AI 호출을 반복하지 않습니다.
- 이미지가 변경되거나 비활성화되면 대기 중인 과거 작업을 취소합니다.
- Outbox는 Kafka 확인 후 발행 완료를 기록하며 최대 10회 시도합니다.
- AI 처리 실패는 초기 시도 후 2회 재시도하고 product.embedding.requested.dlt로 전송한 뒤 FAILED로 기록합니다.
- 작업별 DB 행 잠금과 세대 검증을 사용합니다. S3는 DB 트랜잭션에 참여하지 않으므로 롤백 시 참조되지 않는 객체가 남을 수 있습니다. 동일 내용 키로 덮어쓰는 재시도는 안전하며 객체 정리는 후속 운영 과제입니다.
- 현재 지원하는 이미지 소스는 MOCK_RESOURCE입니다. 외부 마켓 이미지 다운로드는 STEP 12 범위입니다.

## 검증

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test bootJar --no-daemon
.\gradlew.bat integrationTest --tests '*ProductEmbeddingIntegrationTest' --no-daemon
Set-Location ..\ai-service
.\.venv\Scripts\python.exe -m pytest tests/test_product_model.py -m model -q
```

실제 모델 테스트는 준비된 AI_MODEL_CACHE와 CPU PyTorch 설치가 필요합니다.
합성 PNG 3개를 로컬 HTTP로 제공하고 실제 AI API·DINOv2를 통해 768차원·정규화·유한값을 확인합니다.

통합 테스트는 PostgreSQL·Kafka 컨테이너, HTTP AI 응답 스텁, 모의 S3를 사용합니다.
수집→Outbox→Kafka→벡터 저장, 중복 방지, 비활성/변경 이미지 제외를 확인하도록 작성했습니다.
실제 S3까지 포함한 전체 운영 경로를 검증하는 테스트는 아닙니다.

다음 단계는 개발 계획의 STEP 8 Detection Engine V1: 작품과 상품 벡터의 cosine similarity 비교입니다.
