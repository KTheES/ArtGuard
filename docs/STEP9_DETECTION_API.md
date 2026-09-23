# ArtworkGuard STEP 9 — 탐지 검토 API

프로젝트: C:\copyright\_detect\_project

## 구현

- 본인 작품에 대한 탐지 목록·상세 조회.
- 작품 ID, 등급, 검토 상태 필터와 페이지 조회.
- NEW / CONFIRMED / DISMISSED 상태 변경.
- 버전 기반 동시 수정 방지.
- Flyway V8: version, reviewed_at, 버전 증가 트리거와 필터 인덱스.

로그인 및 DB의 활성 사용자 상태를 확인합니다.
타인 작품이나 삭제된 작품의 탐지 상세·수정은 404 DETECTION_NOT_FOUND입니다.
목록에 타인 artworkId를 필터로 지정해도 해당 데이터는 반환하지 않습니다.
관리자도 이 API로 타인의 탐지에 접근할 수 없습니다.

## API

| Method | 경로 | 동작 |
|---|---|---|
| GET | /api/v1/detections | 본인 탐지 목록 |
| GET | /api/v1/detections/{id} | 상세 및 비교 근거 |
| PATCH | /api/v1/detections/{id}/status | 검토 상태 변경 |

목록 쿼리:
- artworkId: 선택, UUID
- status: NEW, CONFIRMED, DISMISSED
- severity: MEDIUM, HIGH, CRITICAL
- page: 기본 0, 0~100000
- size: 기본 20, 1~100

최근 발견 시각 내림차순, ID순으로 정렬합니다.
items/page/size/totalElements/totalPages를 반환합니다.
페이지 목록과 전체 수는 동일 DB 스냅샷에서 읽습니다.

목록 항목에는 작품·상품 ID/제목, 상품 URL, 유사도, 등급, 검토 상태, 버전, 최초/최근 발견 시각,
currentEvidence 및 synthetic이 포함됩니다.

상세는 detection 항목과 imageId, 비교한 작품/상품 임베딩 ID, lastRunId,
모델·전처리 버전, 이미지 해시, reviewedAt을 반환합니다.
원본 벡터, 내부 저장소 키, 서명 URL은 반환하지 않습니다.

## 상태 수정 예

```powershell
$headers=@{Authorization="Bearer $accessToken"}
$base='http://localhost:8080/api/v1/detections'
$list=Invoke-RestMethod "${base}?status=NEW&size=20" -Headers $headers
# 결과의 id 중 검토할 탐지를 선택해 $detectionId에 설정
$detail=Invoke-RestMethod "$base/$detectionId" -Headers $headers
$body=@{
  status='CONFIRMED'
  version=$detail.data.detection.version
} | ConvertTo-Json
$updated=Invoke-RestMethod "$base/$detectionId/status" -Method Patch -Headers $headers -ContentType 'application/json' -Body $body
$updated.data.detection
```

필수 본문 예: `{"status":"DISMISSED","version":4}`.
status는 위의 문자열만 허용합니다. version은 조회 응답의 0 이상 정수입니다.
상태 간 이동은 제한하지 않아 NEW로 다시 검토 대기 상태로 돌릴 수 있습니다.
동일 상태를 다시 저장해도 새로운 검토로 취급하여 버전과 reviewedAt을 갱신합니다.

409 DETECTION_CONFLICT이면 최신 상세를 다시 조회하고 점수·비교 근거·상태를 확인한 후 수정합니다.
재탐지로 점수나 근거가 갱신되어도 버전이 증가하므로 오래된 화면의 수정은 충돌합니다.
재탐지는 검토 상태를 초기화하지 않습니다.

## 과거 기록과 현재 근거

탐지는 과거 발견 기록이므로 상품 이미지가 변경되거나 비활성화되어도 목록에서 자동 삭제하지 않습니다.
currentEvidence=false는 사용한 상품 벡터가 현재 활성 이미지·상품·마켓의 근거에서 제외되었음을 뜻합니다.
이 값은 최신 탐지 실행에 포함되었다거나 최신 모델이라는 뜻은 아닙니다.
현재 이미지가 같아도 이후 실행의 기준 변경·결과 제한 때문에 재매칭되지 않았을 수 있습니다.
상품 제목·URL은 현재 카탈로그 값이고 임베딩 ID·해시는 발견 당시 비교 근거입니다.
synthetic=true는 Mock 합성 상품입니다.

상태는 사용자의 검토 분류입니다. CONFIRMED 저장으로 외부 신고·알림 등의 작업이 실행되지는 않습니다.
상태 변경 전체 이력을 따로 저장하는 감사 로그는 후속 단계이며, 현재는 최종 상태·시각·버전을 저장합니다.

## 검증

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test bootJar --no-daemon
.\gradlew.bat integrationTest --tests '*DetectionIntegrationTest' --no-daemon
```

단위/MVC 테스트는 인증, 소유자 전달, 상태/필터/페이지 검증, 버전 필수, 숫자 상태 거절, 404 및 409를 확인합니다.
통합 테스트는 STEP 8의 실제 pgvector 탐지 테스트를 확장해 사용자 격리, 필터, 삭제 작품 제외,
상태 저장, 버전 증가, 재탐지와 검토 충돌을 확인하도록 작성했습니다.
PostgreSQL pgvector와 Redis용 Docker 엔진이 필요합니다.

다음 단계: STEP 10 Kafka Pipeline — 탐지 실행까지 이벤트 흐름에 연결.
