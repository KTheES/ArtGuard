> STEP 10부터 탐지 POST 응답은 HTTP 202와 작업 ID로 변경되었습니다. 현재 실행 방법은 [STEP10_KAFKA](STEP10_KAFKA.md)를 참고하세요. 아래 동기 응답 예시는 STEP 8 당시 형식입니다.

# ArtworkGuard STEP 8 — Detection Engine V1

프로젝트: C:\copyright\_detect\_project

## 구현 범위

작품 임베딩과 현재 활성 상품 이미지 임베딩의 cosine similarity를 PostgreSQL pgvector에서 계산하고 유사 상품 탐지를 저장합니다.
기존 벡터를 사용하므로 탐지 실행 자체에는 AI 호출이나 S3 다운로드가 없습니다.

- Flyway V7: detection_run 실행 기록, detection 탐지 결과.
- 같은 모델 ID·모델 버전·전처리 버전의 벡터만 비교.
- 현재 이미지 해시와 다른 과거 벡터, 비활성 이미지·상품·마켓 제외.
- 상품별 최고 점수 이미지 하나를 선택하고 점수순으로 결과 저장.
- 동일 작품·상품·모델 조합은 갱신. ID, 최초 발견 시각, 검토 상태 유지.
- 작품 소유자 확인과 작품 행 잠금으로 동시 실행을 직렬화.
- 실행 기록과 결과 저장을 하나의 트랜잭션으로 처리.

자동 주기 탐지와 이벤트 연결은 후속 단계입니다.
STEP 9의 탐지 목록·상세·상태 변경 API는 아직 구현하지 않았습니다.

## 임시 기준값

| similarity | 분류 |
|---|---|
| 0.92 이상 | CRITICAL |
| 0.85 이상, 0.92 미만 | HIGH |
| 0.75 이상, 0.85 미만 | MEDIUM |
| 0.75 미만 | 탐지 생성 안 함 |

분류는 시각적 유사도에 대한 검토 우선순위입니다.
현재 기준은 개발 계획의 임시 예시이며 실제 데이터 평가로 보정해야 합니다.

환경 변수:
- DETECTION_MEDIUM_THRESHOLD=0.75
- DETECTION_HIGH_THRESHOLD=0.85
- DETECTION_CRITICAL_THRESHOLD=0.92

0 <= MEDIUM < HIGH < CRITICAL <= 1 조건을 만족하지 않으면 시작 시 실패합니다.
실행 당시 기준값을 detection_run에 보관합니다.
부동소수점 계산 결과에 반올림을 적용해 상위 등급으로 올리지 않습니다.
단, 정상 cosine 범위를 아주 조금 벗어나는 상단 수치 오차는 1로 제한합니다.

## 실행 API

`POST /api/v1/artworks/{artworkId}/detections?limit=100`

- Bearer 로그인 및 해당 작품 소유권 필요.
- 요청 본문 없음.
- limit 기본 100, 허용 범위 1~500.
- 활성 사용자·삭제되지 않은 본인 작품만 실행 가능.
- 현재 모델의 작품 임베딩이 없으면 409 ARTWORK_EMBEDDING_NOT_READY.
- 상품 벡터가 없거나 기준 이상인 상품이 없으면 정상적인 0건 실행으로 기록.

```powershell
$headers=@{Authorization="Bearer $accessToken"}
$uri="http://localhost:8080/api/v1/artworks/$artworkId/detections?limit=100"
$result=Invoke-RestMethod -Method Post -Uri $uri -Headers $headers
$result.data
```

응답 data:
```json
{
  "runId": "<uuid>",
  "artworkId": "<uuid>",
  "matchedProducts": 3,
  "truncated": false,
  "limit": 100,
  "mediumThreshold": 0.75,
  "highThreshold": 0.85,
  "criticalThreshold": 0.92
}
```

matchedProducts는 이번 실행에서 저장·갱신한 상품 수입니다. 신규 생성 수나 전체 후보 수가 아닙니다.
한도보다 결과가 많으면 상위 limit개를 처리하고 truncated=true를 반환합니다.
한도 적용 전에 상품별 중복 이미지를 제거합니다.

STEP 3~7 설정으로 작품·상품 임베딩을 COMPLETED까지 준비한 후 실행합니다.
기존 Mock 상품은 관리자가 다시 수집하면 임베딩 작업이 등록됩니다.
EMBEDDING_ENABLED=false라도 이미 저장된 벡터가 있으면 탐지할 수 있습니다.
등록·AI 처리 없이 미완성 작품을 탐지하면 위의 409 응답이 정상입니다.

## 저장 의미

탐지는 과거 발견 기록입니다. 이후 실행에서 후보가 사라지거나 limit 밖으로 밀렸더라도 기존 탐지를 자동 삭제·기각하지 않습니다.
마지막으로 매칭된 실행은 last_run_id, 점수는 similarity, 검토 상태는 review_status에 저장합니다.
review_status 기본값은 NEW이며 재탐지로 CONFIRMED/DISMISSED를 초기화하지 않습니다.
해당 상태 변경 API는 STEP 9에서 구현합니다.

상품·작품 임베딩 ID를 보존하여 비교 근거를 추적할 수 있습니다.
detection_run은 실행 요약과 기준값을 보관하지만, 모든 실행별 결과 목록을 불변 이력으로 복제하지는 않습니다.
상품 변경과 탐지가 동시에 일어나면 결과는 조회 시점의 벡터를 근거로 한 발견 기록입니다.

## 검증과 한계

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test bootJar --no-daemon
.\gradlew.bat integrationTest --tests '*DetectionIntegrationTest' --no-daemon
```

단위/MVC 테스트: 기준값 경계, 잘못된 설정, 소유권, 미준비 벡터, 결과 제한, 빈 결과, 인증과 API 오류.
통합 테스트: 알려진 768차원 단위 벡터로 cosine 순위·등급·상품 중복 제거·검토 상태 보존·변경/비활성/다른 전처리 버전 제외를 검증하도록 작성했습니다.
통합 테스트에는 PostgreSQL pgvector와 Redis 컨테이너가 필요하며, AI·Kafka·S3는 필요하지 않습니다.

V1은 근사 인덱스 없이 전체 적격 상품 벡터를 정확 비교합니다.
반환 한도는 SQL 계산량을 제한하지 않으며 트랜잭션 제한 시간은 30초입니다.
대규모 카탈로그를 위한 인덱스·비동기 실행·부하 제한은 후속 개선 대상입니다.
