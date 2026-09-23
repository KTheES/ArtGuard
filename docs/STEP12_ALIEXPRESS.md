# ArtworkGuard STEP 12 — AliExpress 제휴 상품 연동 준비

프로젝트: C:\copyright\_detect\_project

## 현재 범위와 미확인 사항

첫 실제 마켓을 AliExpress로 준비했습니다.
공식 공개 문서의 aliexpress.affiliate.product.query를 호출하고 제휴 상품 메타데이터·대표 이미지를 기존 카탈로그에 저장하는 코드입니다.
AliExpress 전체 상품의 전수 검색을 제공하는 API는 아닙니다.

현재 구현은 해당 API 문서에 기재된 TOP 호환 게이트웨이 https://eco.taobao.com/router/rest 및 MD5 요청 서명을 사용합니다.
AliExpress Open Platform의 /rest 경로·HMAC-SHA256 규격과 혼용하지 않습니다.
새 Open Platform 전용 키가 이 TOP API와 호환된다고 확인한 상태는 아닙니다.
실제 계정의 허용 API와 게이트웨이를 확인해야 합니다. 다른 게이트웨이만 허용된 계정이면 해당 규격의 추가 어댑터 작업이 필요합니다.

이번 환경에는 ALIEXPRESS_APP_KEY와 ALIEXPRESS_APP_SECRET이 설정되어 있지 않았습니다.
따라서 실제 네트워크 검색, 계정 접근 권한, 실제 상품 수집 성공은 아직 확인하지 못했습니다.
구현 완료와 운영 API 검증 완료를 구분해야 합니다.

## 공식 자료

- [상품 검색 API·응답 필드](https://developer.alibaba.com/docs/api.htm?apiId=45803)
- [TOP 요청·서명 규격](https://developer.alibaba.com/docs/doc.htm?articleId=101617&docType=1&treeId=1)
- [별도 Open Platform 서명 규격](https://developer.alibaba.com/docs/doc.htm?articleId=120692&docType=1&treeId=727)

공개 자료 중 이전 문서도 포함되어 있어 계정 콘솔의 API 권한·문서를 함께 확인해야 합니다.

## 설정

기본값 ALIEXPRESS_ENABLED=false입니다. 자동으로 외부 API에 접근하지 않습니다.
API 키·시크릿은 파일이나 채팅에 붙이지 않고 백엔드를 시작하는 터미널 환경 변수로 설정합니다.

```powershell
$env:ALIEXPRESS_ENABLED='true'
$env:ALIEXPRESS_APP_KEY=Read-Host 'AliExpress API app key'
$aliSecret=Read-Host 'AliExpress API app secret' -AsSecureString
$env:ALIEXPRESS_APP_SECRET=[System.Net.NetworkCredential]::new('',$aliSecret).Password
Remove-Variable aliSecret
# 계정에서 사용하는 경우에만 설정
$env:ALIEXPRESS_TRACKING_ID=''
$env:S3_ENABLED='true'
# 기존 DB·Redis·JWT·S3 버킷/주소/인증 정보 필요
# 임베딩/자동 탐지를 함께 실행하려면 기존 Kafka·AI 설정과 아래 값 사용
$env:EMBEDDING_ENABLED='true'
$env:DETECTION_ENABLED='true'
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat bootRun
```

예제 .env는 설정 참고용이며 Gradle이 자동 로드하지 않습니다.
AliExpress 설정은 toString에 키나 시크릿을 출력하지 않습니다.
서명된 요청은 HTTPS POST 본문으로 전송하며 시크릿 자체는 전송하지 않습니다.
벤더 원문 오류·자격 증명을 응답이나 로그에 출력하지 않습니다.

## 수집 API

`POST /api/v1/admin/marketplaces/ALIEXPRESS/collect`

Bearer 관리자 권한과 DB의 현재 ACTIVE/ROLE_ADMIN을 모두 확인합니다.
본문은 query, page, limit을 명시해야 합니다.

```powershell
$headers=@{Authorization="Bearer $accessToken"}
$body=@{query='cat art poster';page=1;limit=3} | ConvertTo-Json
Invoke-RestMethod 'http://localhost:8080/api/v1/admin/marketplaces/ALIEXPRESS/collect' -Method Post -Headers $headers -ContentType 'application/json' -Body $body
```

- query: 1~100자.
- page: 1~100.
- limit: 1~5.
- 첫 버전은 USD/EN/배송 대상 US로 고정.
- 요청할 때마다 지정 페이지를 조회합니다. 주기 스케줄러나 자동 페이지 순회는 포함하지 않습니다.
- 대표 이미지 한 장만 저장하며 동영상과 추가 작은 이미지는 수집하지 않습니다.
- 상품 제목·가격·통화·shop ID를 저장하고, 상점 이름은 식별용 AliExpress shop {shopId}로 표시합니다.
- 상품/상점 URL은 ID 기반 aliexpress.com 주소이며 수익 추적 링크로 이동시키지 않습니다.

결과는 /api/v1/products?marketplace=ALIEXPRESS 에서 확인합니다.
실제 마켓 상품 응답의 synthetic은 false입니다.
cached=true인 응답은 이번 요청의 upsert 수가 0입니다.

## 이미지·임베딩 연결

1. API 응답의 대표 이미지 URL을 허용된 HTTPS CDN 주소인지 확인.
2. 리다이렉트 없이 크기·시간을 제한해 다운로드.
3. 기존 ImageValidator로 PNG/JPEG·픽셀 수를 검증하고 PNG로 정규화.
4. 정규화된 실제 바이트의 SHA-256으로 S3 키 생성.
5. private S3 catalog-imports/aliexpress/{hash}.png에 저장.
6. 상품·이미지 메타데이터와 임베딩 작업을 한 DB 트랜잭션에서 저장.
7. 기존 상품 임베딩 worker가 S3 이미지를 읽고 해시를 확인한 뒤 AI 처리.

Flyway V10은 AliExpress 마켓, STORED_PNG 이미지 소스 및 size_bytes를 추가합니다.
이미지의 해시가 바뀌면 새 임베딩 작업이 생기며 이전 벡터는 기존 current 뷰에서 제외됩니다.
이미지 미리보기는 인증된 백엔드를 통해 제공하며 저장소 키를 클라이언트에 노출하지 않습니다.

허용 CDN:
ae01~ae05.alicdn.com, ae-pic-a1.aliexpress-media.com, ae-pic-a2.aliexpress-media.com.
그 외 호스트·HTTP·별도 포트·userinfo·fragment·리다이렉트는 거절합니다.
DNS의 로컬/사설 주소를 확인하고 HTTPS 인증서 검증을 유지합니다.
프로덕션 네트워크의 outbound 정책을 대체하는 기능은 아닙니다.

## 제한·오류 처리

- 기존 수집 사용자별 10회/60초 제한을 Mock과 공유.
- AliExpress 마켓 단위 Redis 잠금 및 30초 동일 검색 캐시.
- 각 이미지 처리 전과 DB 저장 전에 잠금 소유권·TTL 갱신.
- AliExpress는 내용 비교를 위해 캐시 만료 후 대표 이미지를 다시 읽습니다. Mock의 60초 상품 표시를 그대로 사용하지 않습니다.
- API 응답 최대 2 MiB, 다운로드 이미지 최대 20 MiB.
- 연결 제한 5초, 읽기 대기 10초, 본문 읽기 경과 제한 20초.
- 정규화 후 PNG가 20 MiB를 넘으면 거절.
- 실패 시 자동 외부 요청 재시도는 하지 않습니다.
- ALIEXPRESS_NOT_CONFIGURED: 설정 또는 키 누락, 503.
- ALIEXPRESS_HTTP_ERROR / UNAVAILABLE / INVALID_RESPONSE / IMAGE_UNAVAILABLE: 외부 호출·응답·이미지 오류, 502.
- Redis·S3·인증 오류는 기존 오류 규약을 사용합니다.

상품 배치 중 이미지가 실패하면 DB 저장을 진행하지 않습니다.
이미 S3에 저장한 파일은 DB 롤백과 함께 삭제되지 않으므로 참조되지 않는 객체가 남을 수 있습니다.
실제 API 제공 범위·계정 쿼터·이미지 CDN은 운영 검증 시 확인할 항목입니다.

## 테스트

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test bootJar --no-daemon
.\gradlew.bat integrationTest --tests '*AliCatalogIntegrationTest' --no-daemon
```

단위/MVC: 독립적으로 계산한 UTF-8 서명 값, 요청 인코딩·시크릿 비전송, 응답/가격 검증,
CDN 제한, 다운로드 크기 제한, 관리자 권한, 설정 누락, S3 실패 시 DB 미저장.
통합 테스트는 PostgreSQL·Redis 컨테이너, 모의 S3/AI, 합성 이미지로 상품 중복 방지와 벡터 저장을 확인하도록 작성했습니다.
실제 AliExpress API 테스트를 대체하지 않습니다.

STEP 12의 실제 수집 완료 조건은 아직 충족 확인되지 않았습니다.
계정 키·허용 게이트웨이 확인과 실호출 검증을 마친 뒤 STEP 13으로 진행합니다.
