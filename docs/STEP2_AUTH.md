# STEP 2 — 인증

## 구현 범위

| API | 요청 | 성공 응답 |
|---|---|---|
| POST /api/v1/auth/signup | email, password, nickname | 201 + 사용자 DTO |
| POST /api/v1/auth/login | email, password | 200 + Access/Refresh Token |
| POST /api/v1/auth/refresh | refreshToken | 200 + 교체된 토큰 쌍 |
| POST /api/v1/auth/logout | refreshToken | 200, Refresh Token 폐기 |
| GET /api/v1/auth/me | Authorization: Bearer 토큰 | 200 + 본인 사용자 DTO |

공통 응답: `success`, `data`, `error`, `timestamp`.
인증 실패는 401, 유효성 오류는 400, 이메일 중복은 409입니다.

## 설계와 변경 파일

- `backend/build.gradle`: Spring Security OAuth2 Resource Server 추가. 버전은 Spring Boot BOM 관리.
- `user/domain/AppUser.java`, `user/repository/UserRepository.java`: 사용자 저장과 조회.
- `db/migration/V2__create_app_user.sql`: app_user 테이블, 이메일 UNIQUE 및 상태·역할 제약.
- `auth/dto`, `auth/controller`, `auth/service`: 검증 DTO, API, 인증 서비스, Redis Refresh Token 처리.
- `auth/jwt`: HS256 서명·검증, 설정 검증, 토큰 발급.
- `common/config`: stateless Bearer 인증, Swagger Authorize 지원.
- `common/exception`: 공통 인증·중복·저장소 연결 오류 응답.
- `src/test`: JWT·서비스·MVC 보안 테스트와 PostgreSQL/Redis 통합 테스트.

비밀번호는 12자 이상, UTF-8 72바이트 이하이며 BCrypt cost 12로 저장합니다.
이메일은 앞뒤 공백 제거 및 소문자로 정규화합니다. 가입 요청에서 역할을 지정할 수 없고 ROLE_USER를 부여합니다.
JWT에는 사용자 UUID와 역할만 담고 이메일·닉네임·비밀번호는 넣지 않습니다.
HS256 알고리즘, 서명, issuer, audience, 만료, access 용도, 사용자 UUID를 검증합니다.

Refresh Token은 사용자 UUID와 256비트 난수로 구성합니다. Redis의 `refresh-token:{userId}`에는 SHA-256 해시만 14일 TTL로 저장합니다.
계정당 Refresh Token 하나를 유지합니다. 새 로그인은 기존 Refresh Token을 대체하며 갱신할 때마다 새 난수로 교체합니다.
Lua compare-and-set으로 동시 갱신은 하나만 성공합니다. 이전 토큰으로 새 세션을 로그아웃시킬 수 없습니다.
과거 토큰의 재사용은 거절하지만 현재 세션 전체를 자동 폐기하지는 않습니다.

로그아웃은 Refresh Token을 폐기합니다. Access Token은 15분 TTL이며 이미 발급된 Access Token은 만료 전까지 유효합니다.
클라이언트는 로그아웃 후 두 토큰을 모두 삭제해야 합니다. 즉시 Access Token 폐기가 필요하면 이후 단계에서 세션 상태 검증/denylist를 추가해야 합니다.
JWT 시간 검증에는 Spring Security 기본 60초 clock skew가 적용됩니다.
`/me`는 DB에서 활성 계정인지 다시 확인하며 다른 사용자 ID를 요청 파라미터로 넘겨도 조회 대상이 바뀌지 않습니다.

쿠키 인증과 서버 세션을 사용하지 않으며 토큰은 JSON/Authorization 헤더로만 전달합니다. 이 조건에서 CSRF 검사를 비활성화했습니다.
별도 프런트엔드 origin의 CORS 허용은 아직 설정하지 않았습니다. Swagger는 같은 origin에서 사용할 수 있습니다.
OAuth, 메일 인증, 요청 속도 제한, 즉시 Access Token 폐기, 작품 소유권 검사는 이번 범위 밖입니다.
실제 로그인 정보·서명 키·토큰은 로그에 기록하지 마세요. 인증 응답은 no-store로 캐시하지 않습니다.

## 실행

Docker Desktop의 Linux 컨테이너 엔진을 시작합니다. PowerShell에서:

```powershell
Set-Location C:\copyright\_detect\_project
# 최초 로컬 DB 생성 시에만 새 비밀번호를 생성하세요.
# 기존 볼륨이 있다면 최초 설정했던 DB_PASSWORD와 REDIS_PASSWORD를 사용하세요.
$env:DB_PASSWORD = [guid]::NewGuid().ToString('N')
$env:REDIS_PASSWORD = [guid]::NewGuid().ToString('N')
# 최소 32바이트의 암호학적 난수를 Base64 인코딩합니다.
$keyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($keyBytes)
docker compose up -d --wait
Set-Location backend
.\gradlew.bat bootRun
```

`JWT_SECRET`은 필수이며 기본값이 없습니다. 재시작 후에도 기존 Access Token을 인정하려면 동일한 키를 안전하게 보관하고 재사용하세요.
Compose는 `.env`를 읽지만 Spring Boot는 자동으로 읽지 않습니다. backend를 실행할 세션에도 환경 변수가 필요합니다.
키·비밀번호를 소스나 Git에 저장하지 마세요.

## API 확인 (다른 PowerShell 창)

```powershell
$base = 'http://localhost:8080/api/v1/auth'
$account = @{ email='creator@example.com'; password='LocalTestPassword123!'; nickname='Creator' }
Invoke-RestMethod "$base/signup" -Method Post -ContentType 'application/json' -Body ($account | ConvertTo-Json)
$loginBody = @{email=$account.email; password=$account.password} | ConvertTo-Json
$tokens = (Invoke-RestMethod "$base/login" -Method Post -ContentType 'application/json' -Body $loginBody).data
Invoke-RestMethod "$base/me" -Headers @{Authorization="Bearer $($tokens.accessToken)"}
$oldRefresh = $tokens.refreshToken
$tokens = (Invoke-RestMethod "$base/refresh" -Method Post -ContentType 'application/json' -Body (@{refreshToken=$oldRefresh} | ConvertTo-Json)).data
# 아래 요청은 401이 예상됩니다: 이전 Refresh Token 재사용
Invoke-RestMethod "$base/refresh" -Method Post -ContentType 'application/json' -Body (@{refreshToken=$oldRefresh} | ConvertTo-Json)
Invoke-RestMethod "$base/logout" -Method Post -ContentType 'application/json' -Body (@{refreshToken=$tokens.refreshToken} | ConvertTo-Json)
# 로그아웃 후 갱신도 401이 예상됩니다.
Invoke-RestMethod "$base/refresh" -Method Post -ContentType 'application/json' -Body (@{refreshToken=$tokens.refreshToken} | ConvertTo-Json)
```

Swagger: http://localhost:8080/swagger-ui.html
Health: `Invoke-RestMethod http://localhost:8080/actuator/health`

## 테스트

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test bootJar
.\gradlew.bat integrationTest
.\gradlew.bat check bootJar
```

통합 테스트는 실제 PostgreSQL(pgvector)·Redis 컨테이너를 사용하며 기존 STEP 1 테스트는 Kafka도 확인합니다.
실행용 DB/JWT 환경 변수는 테스트가 격리된 값으로 재정의하므로 필요하지 않습니다.
Docker가 없으면 통합 테스트가 실패하며 성공으로 간주하거나 자동으로 건너뛰지 않습니다.

이 PC에서 PATH에 문자 그대로 `$env:PATH`라는 잘못된 항목이 관찰됐습니다. 같은 오류가 발생하면 테스트 세션에서만 다음처럼 제외할 수 있습니다.

```powershell
$env:Path = (($env:Path -split ';') | Where-Object { $_ -ne '$env:PATH' }) -join ';'
```

## 공식 참고 문서

- [Spring Security JWT](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)
- [Redis Lua의 원자적 실행](https://redis.io/docs/latest/develop/interact/programmability/eval-intro/)

## 다음 단계

STEP 3: Artwork CRUD와 S3 Presigned Upload, 사용자별 작품 소유권 검사.
