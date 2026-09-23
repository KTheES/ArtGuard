# STEP 3 — 작품 및 이미지 업로드

## 구현

- 작품 등록·목록·상세·수정·논리 삭제
- JWT의 사용자 UUID 및 DB의 활성 계정 확인
- 모든 작품·업로드 조회를 사용자 ID로 제한. 타인·삭제·없는 리소스는 동일한 404
- 업로드 요청마다 서버가 임의 경로를 생성하고 10분 유효 PUT URL 발급
- 업로드 완료 후 MIME, 매직 바이트, 실제 길이, 이미지 디코딩, 해상도 검증
- PNG·JPEG 입력, 최대 20 MiB, 한 변 8,192px 이하, 총 1,600만 픽셀 이하
- 정규화한 PNG 및 최대 512px 워터마크 미리보기 저장
- 소유자에게만 60초 GET URL 발급. 메타데이터 API는 저장소 키·영구 이미지 URL을 반환하지 않음
- 같은 uploadId로 등록을 재시도하면 기존 작품을 반환. 비관적 잠금으로 중복 등록 방지
- 수정 시 version 필수. 오래된 버전과 동시 수정 충돌은 409
- 모니터링 설정은 저장만 하며 실제 자동 검색은 이후 단계에서 구현

## API

| Method | Path | 내용 |
|---|---|---|
| POST | /api/v1/artworks/upload-url | contentType, sizeBytes로 업로드 준비 |
| PUT | 응답 uploadUrl | 이미지 파일을 S3로 직접 업로드 |
| POST | /api/v1/artworks | uploadId, title, description으로 등록 |
| GET | /api/v1/artworks?page=0&size=20 | 본인 작품 페이지 조회, size 최대 100 |
| GET | /api/v1/artworks/{id} | 본인 작품 상세 |
| PATCH | /api/v1/artworks/{id} | title, description, monitoringEnabled 및 현재 version |
| DELETE | /api/v1/artworks/{id} | 논리 삭제 및 모니터링 비활성화 |
| GET | /api/v1/artworks/{id}/image-url?thumbnail=false | 원본 크기 정규화 이미지 URL |
| GET | /api/v1/artworks/{id}/image-url?thumbnail=true | 워터마크 미리보기 URL |

애플리케이션 API에는 `Authorization: Bearer ...`를 사용합니다. S3 PUT에는 JWT를 보내지 않고 응답의 requiredHeaders를 사용하세요.
업로드 요청에 사용자 파일명·임의 URL·객체 경로는 받지 않습니다.

## 데이터·저장소 설계

Flyway V3는 `artwork_upload`, `artwork`와 외래키·소유자 조회 인덱스를 생성합니다.
기존 V1/V2는 변경하지 않습니다. AWS SDK for Java v2의 S3 client/presigner를 추가했습니다.

업로드 파일은 `uploads/{userId}/{uploadId}`에만 전송합니다. 검증 시 HEAD의 ETag를 GET의 If-Match에 사용하고 읽기 길이도 제한합니다.
검증·재인코딩된 바이트만 `artworks/{userId}/{artworkId}/original.png` 및 `thumbnail.png`에 기록합니다.
업로드 URL을 재사용해 임시 파일을 바꿔도 이미 등록된 작품은 바뀌지 않습니다.

현재 PNG로 정규화하므로 입력 파일의 메타데이터와 원본 바이너리는 영구 보존하지 않습니다.
WebP 변환, EXIF 방향 보정, 다른 이미지 형식은 아직 지원하지 않습니다.
원본 크기 이미지는 공개 CDN에 노출하지 않습니다. AWS에서는 S3 Block Public Access를 켜고 버킷을 비공개로 운영해야 합니다.

삭제는 논리 삭제입니다. DB 및 저장소의 파일은 남아 있으며 삭제 후 새 조회 URL을 발급하지 않습니다.
이미 발급된 GET URL은 최대 60초간 유효합니다. 영구 삭제·보존 기간 정책과 DB 커밋 실패 시 고아 객체 정리는 후속 과제입니다.
이미지 등록 트랜잭션은 업로드 요청을 잠근 상태에서 제한된 S3 호출을 수행하며 timeout은 90초입니다.
S3와 DB는 단일 원자적 트랜잭션이 아니므로 DB 실패 시 파일만 남을 수 있습니다.

## 실행: 기존 인증만 사용

S3_ENABLED의 기본값은 false입니다. 저장소 키 없이도 기존 인증 및 메타데이터 API가 시작됩니다.
업로드/이미지 URL 요청은 저장소 비활성 상태에서 503 STORAGE_UNAVAILABLE을 반환합니다.
기존 DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET은 [STEP 2](STEP2_AUTH.md)와 같습니다.

## 로컬 MinIO 실행 (PowerShell)

Docker Desktop Linux 엔진을 켜고, 이전 단계의 DB_PASSWORD·REDIS_PASSWORD·JWT_SECRET이 설정된 PowerShell에서 실행하세요.
기존 DB 볼륨의 비밀번호는 새로 생성하지 말고 최초 값을 재사용해야 합니다.

```powershell
Set-Location C:\copyright\_detect\_project
# 아래 키는 로컬 MinIO용입니다. AWS 운영 키를 넣지 마세요.
$env:AWS_ACCESS_KEY_ID = 'artworkguard-local'
$env:AWS_SECRET_ACCESS_KEY = [guid]::NewGuid().ToString('N')
$env:AWS_REGION = 'us-east-1'
$env:S3_ENABLED = 'true'
$env:S3_ENDPOINT = 'http://localhost:9000'
$env:S3_BUCKET = 'artworkguard-local'
$env:S3_INIT_BUCKET = 'true'
docker compose -f docker-compose.yml -f infra/docker/compose.s3.yml up -d --wait
Set-Location backend
.\gradlew.bat bootRun
```

MinIO Console은 http://localhost:9001 에서 로컬 키로 로그인합니다.
초기화 옵션은 loopback 주소의 로컬 저장소만 허용합니다. 새 버킷을 생성할 때 임시 uploads/ 파일의 1일 만료 정책도 설정합니다.
이미 있는 버킷의 정책은 변경하지 않습니다. 최초 초기화 이후에는 S3_INIT_BUCKET=false로 실행해도 됩니다.
원본과 미리보기에는 이 임시 파일 만료 정책을 적용하지 않습니다.

AWS S3를 사용하려면 S3_ENDPOINT를 비우고, S3_INIT_BUCKET=false로 설정하고, 미리 준비한 private bucket과 IAM credentials/role을 사용하세요.
필요 권한은 임시 경로 PutObject/GetObject와 작품 경로 PutObject/GetObject입니다.
버킷을 공개하거나 실제 AWS 리소스를 생성하는 작업은 이번 구현에서 수행하지 않았습니다.
브라우저에서 다른 origin으로 직접 PUT하는 경우 저장소 CORS 설정이 별도로 필요합니다. 아래 PowerShell 예시는 CORS 영향을 받지 않습니다.

## API 테스트 (PowerShell 7)

STEP 2 로그인 응답을 `$tokens`에 담고, 테스트 PNG 파일 경로를 지정하세요.

```powershell
$base = 'http://localhost:8080/api/v1/artworks'
$auth = @{ Authorization="Bearer $($tokens.accessToken)" }
$imagePath = 'C:\path\artwork.png' # 실제 PNG 파일로 변경
$size = (Get-Item -LiteralPath $imagePath).Length
$ticket = (Invoke-RestMethod "$base/upload-url" -Method Post -Headers $auth -ContentType 'application/json' -Body (@{contentType='image/png';sizeBytes=$size}|ConvertTo-Json)).data
$putHeaders = @{}
$ticket.requiredHeaders.PSObject.Properties | ForEach-Object { $putHeaders[$_.Name]=$_.Value }
Invoke-WebRequest $ticket.uploadUrl -Method Put -Headers $putHeaders -InFile $imagePath
$artwork = (Invoke-RestMethod $base -Method Post -Headers $auth -ContentType 'application/json' -Body (@{uploadId=$ticket.uploadId;title='첫 작품';description='테스트'}|ConvertTo-Json)).data
Invoke-RestMethod $base -Headers $auth
$image = (Invoke-RestMethod "$base/$($artwork.id)/image-url?thumbnail=true" -Headers $auth).data
# $image.url을 브라우저에서 열거나 GET하여 미리보기를 확인하세요.
$artwork = (Invoke-RestMethod "$base/$($artwork.id)" -Method Patch -Headers $auth -ContentType 'application/json' -Body (@{title='변경한 제목';version=$artwork.version}|ConvertTo-Json)).data
Invoke-RestMethod "$base/$($artwork.id)" -Method Delete -Headers $auth
# 이후 상세 및 image-url 조회는 404가 예상됩니다.
```

## 검증

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test bootJar
.\gradlew.bat integrationTest
.\gradlew.bat check bootJar
```

단위 테스트는 이미지 위조·잘림·과대 크기, 소유권, 업로드 만료·재시도, 수정 버전 충돌, 서명 URL을 확인합니다.
통합 테스트는 실제 PostgreSQL·Redis·MinIO로 업로드 → 등록 → 소유권 → URL 비공개 → 수정 → 삭제를 확인합니다.
Docker 없이는 통합 테스트가 실패하며 자동으로 건너뛰지 않습니다. 실제 실행 결과는 STEP3_STATUS.md를 참고하세요.

## 공식 참고

- [AWS SDK S3 Presigned URL](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html)
- [S3 조건부 읽기](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-reads.html)

다음 단계: STEP 4 Python FastAPI 기반 AI Embedding 서비스.
