> STEP 11부터 반복 수집에는 검색 캐시와 상품 중복 처리 생략이 적용됩니다. 즉시 상품 임베딩 재시도는 이미지 embedding POST를 사용하세요. 현재 정책: [STEP11_REDIS](STEP11_REDIS.md).

# ArtworkGuard STEP 6 — Mock Marketplace

프로젝트: C:\copyright\_detect\_project

## 구현 범위

- Flyway V5: marketplace, seller, product, product_image 테이블.
- MarketplaceAdapter와 Mock 구현: 판매자 2명, 상품 3개, 합성 PNG 3개.
- 외부 ID의 고유 제약과 PostgreSQL upsert로 재수집 시 ID·최초 발견 시각을 유지하고 제목·가격·판매자·최근 발견 시각을 갱신합니다.
- 상품별 응답에서 사라진 이미지는 비활성화합니다. 검색에 포함되지 않은 다른 상품은 변경하지 않습니다.
- 이미지 내용 해시가 바뀌면 저장소 키를 무효화합니다. 실제 S3 저장과 상품 임베딩 처리는 STEP 7 범위입니다.
- 전체 수집 응답을 먼저 검증한 후 하나의 DB 트랜잭션으로 저장합니다.
- 카탈로그는 로그인 사용자 간 공유됩니다. 개인 작품 API와는 별도입니다.

## 실행

기존 PostgreSQL·Redis 및 백엔드 실행 설정을 사용합니다. 백엔드 시작 시 V5가 적용됩니다.
실제 마켓 접속이나 AI 서비스는 이 단계의 Mock 수집에 필요하지 않습니다.

백엔드를 실행하는 PowerShell에서:

```powershell
$env:MOCK_MARKETPLACE_ENABLED='true'
# 기존 JWT_SECRET, DB, Redis 환경 변수도 설정한 상태에서 실행
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat bootRun
```

.env.example은 설정 참고용이며, Gradle이 자동으로 읽는 파일은 아닙니다.

Mock 수집은 JWT의 ROLE_ADMIN과 DB의 현재 ACTIVE/ROLE_ADMIN을 모두 확인합니다.
개발용 관리자가 없다면 가입한 개발 계정 하나를 로컬 DB에서 명시적으로 승격한 후 다시 로그인합니다.
아래 SQL의 이메일은 본인의 개발 계정으로 교체합니다. 자동 실행되는 관리자 계정이나 기본 비밀번호는 없습니다.

```sql
UPDATE app_user SET role='ROLE_ADMIN', updated_at=now()
WHERE email='your-local-test-account@example.com' AND status='ACTIVE';
```

## API

모든 경로는 Bearer 인증이 필요합니다.

| Method | 경로 | 동작 |
|---|---|---|
| GET | /api/v1/marketplaces | 마켓 목록, 수집 가능 여부 |
| POST | /api/v1/admin/marketplaces/MOCK/collect | 관리자 Mock 수집 |
| GET | /api/v1/products | 상품 검색·페이지 조회 |
| GET | /api/v1/products/{id} | 상품·판매자·이미지 상세 |
| GET | /api/v1/products/{id}/images/{imageId}/preview | 합성 PNG 미리보기 |

수집 본문: `{"query":"","limit":20}`. 빈 객체도 기본값으로 처리합니다.
query는 최대 100자, limit은 1~100입니다.
응답의 upsertedProducts/upsertedImages는 이번에 저장 또는 갱신한 수이며 신규 생성 수가 아닙니다.

목록 예: `/api/v1/products?marketplace=MOCK&query=moon&page=0&size=20`.
page는 0부터, size는 1~100입니다. %와 _는 검색 와일드카드로 실행하지 않습니다.

```powershell
$headers=@{Authorization="Bearer $accessToken"}
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/admin/marketplaces/MOCK/collect' -Headers $headers -ContentType 'application/json' -Body '{}'
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/products?marketplace=MOCK' -Headers $headers
```

MOCK_MARKETPLACE_ENABLED=false이면 관리자 수집에도 503 MOCK_COLLECTION_DISABLED를 반환합니다.
일반 사용자의 수집 요청은 403, 비로그인 요청은 401입니다.
이미지는 상품 ID와 이미지 ID를 함께 확인하고 Cache-Control: no-store를 반환합니다.
내부 sourceKey/storageKey는 응답에 노출하지 않습니다.

## 데이터 성격과 후속 단계

상품 URL은 mock.artworkguard.invalid 아래의 가상 주소이며 접속 대상이 아닙니다.
이미지는 프로젝트에 포함한 테스트용 합성 그림이고 실제 판매 상품이 아닙니다. 응답에 synthetic=true를 표시합니다.
MOCK 이외의 MarketplaceCode는 향후 어댑터 구분용이며 아직 수집을 지원하지 않습니다.
현재 입력 URL 정책과 이미지 소스는 Mock 전용입니다. 실제 마켓 연동 시 별도의 검증 정책이 필요합니다.

STEP 7에서는 상품 이미지 임베딩과 유사도 검색 연결을 이어갑니다.

## 검증 명령

```powershell
.\gradlew.bat test bootJar --no-daemon
.\gradlew.bat integrationTest --tests '*CatalogIntegrationTest' --no-daemon
```

단위/MVC 테스트는 인증·관리자 제한, 입력 제한, 이미지 해시, 경로 허용 목록, 수집 기본 비활성, DB 권한 재검사, 사전 중복 검증을 확인합니다.
CatalogIntegrationTest는 실제 PostgreSQL에서 재수집 ID 유지, 중복 방지, 가격 갱신, 검색, 이미지 소속 확인을 검증하도록 작성했습니다. Docker 엔진이 필요합니다.
