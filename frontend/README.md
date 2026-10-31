# ArtworkGuard 로컬 웹 앱

## GitHub Packages

배포 패키지는 `@kthees/artguard-frontend` 이름으로 GitHub Packages에서 관리합니다. `frontend-v<version>` 태그가 `package.json` 버전과 일치할 때만 publish workflow가 실행됩니다.

```bash
npm config set @kthees:registry https://npm.pkg.github.com
npm install @kthees/artguard-frontend@1.0.2
```

Private package 접근이 필요한 경우 토큰을 `.npmrc`에 직접 쓰지 말고 `NODE_AUTH_TOKEN` 환경변수 또는 GitHub Actions Secret으로 주입합니다.

Node.js 22 이상에서 `npm ci` 후 실행합니다. 배포 패키지는 빌드된 정적 자산과 로컬 프록시 서버만 포함합니다.

```powershell
Set-Location C:\copyright\_detect\_project\frontend
node server.mjs
# http://127.0.0.1:5173
node --test
```

백엔드는 별도로 `127.0.0.1:8080`에서 실행해야 합니다. 다른 로컬 포트는 `BACKEND_ORIGIN=http://127.0.0.1:8081` 환경 변수로 설정합니다. 웹 포트는 `PORT`로 바꿉니다. localhost 대신 표시된 127.0.0.1 주소로 접속합니다.

## Docker 실행

```powershell
docker build --tag artguard-frontend:1.0.1-local --file frontend/Dockerfile frontend
docker run --rm --publish 127.0.0.1:15174:5173 artguard-frontend:1.0.1-local
# http://127.0.0.1:15174
```

컨테이너는 non-root `node` 사용자로 실행되며 `/` HTTP 응답을 이용한 health check를 포함합니다. API 프록시 대상은 기본적으로 컨테이너 내부 loopback이므로 실제 연결 환경에서는 별도 네트워크·백엔드 구성을 검토해야 합니다.

## 제공 화면

- 회원가입·로그인·로그아웃. 토큰은 메모리에 보관하며 새로고침하면 재로그인합니다. 동시 401 응답의 갱신 요청을 합치고 갱신 실패 시 로그인 화면으로 돌아갑니다.
- 작품 목록·페이지 이동·PNG/JPEG 최대 20 MiB 업로드·상세 이미지·임베딩 요청/상태.
- 비동기 탐지 요청·수동 상태 확인·실패 작업 재시도.
- 탐지 목록·작품별 결과·상태/심각도 필터·상세 이미지와 상품 URL·버전을 포함한 검토 상태 수정. 409 충돌 시 최신 상태를 다시 조회합니다.
- 빈 결과, 로딩, 권한·요청 제한·연결 실패 안내. API 데이터는 HTML 문자열로 삽입하지 않습니다.

이 서버는 loopback 전용 개발 서버입니다. `/api/v1/`를 고정 로컬 백엔드로 전달하고 임의 외부 프록시를 제공하지 않습니다. 운영 배포, TLS 종료, 정책 동의 화면, 이메일 인증/소유권/결제/관리자 화면은 포함하지 않습니다. 로그아웃 요청 실패 시 브라우저 메모리는 지우지만 서버 토큰 폐기를 보장하지 못하므로 오류가 표시됩니다.

## 저장소 연결

작품은 백엔드가 발급한 서명 URL로 직접 PUT합니다. 저장소 CORS에서 실제 웹 origin(기본 `http://127.0.0.1:5173`), PUT, 발급된 requiredHeaders를 허용해야 합니다. 이 단계에서 버킷 설정을 변경하지 않았습니다. 이미지 읽기 URL도 브라우저가 접근 가능해야 합니다. 저장소 주소에 API Bearer 토큰을 붙이지 않습니다.

백엔드·DB·Redis·Kafka·AI·저장소 기능 설정은 [프로젝트 안내](../README.md)를 따릅니다. 임베딩/탐지가 비활성인 경우 화면만으로 완료되지 않습니다. 탐지 상태는 자동 폴링하지 않으며 상태 확인 버튼으로 갱신합니다. 페이지 이동/새로고침 시 현재 탐지 작업 ID는 보존하지 않습니다.

## 2026-09-15 검증

JavaScript 구문 검사와 Node 테스트 6개 통과: URL 제한, 동시 토큰 갱신, 갱신 실패, 로그아웃 실패 시 로컬 삭제, 409 안내, 개발 서버 파일/Origin 제한.

브라우저에서 가입 화면 배치를 확인하고 격리된 가짜 API 응답으로 로그인·작품 목록·탐지 목록/상세·검토 상태 변경·이미지 실패 안내를 확인했습니다. 해당 가짜 API는 이 앱에 포함하지 않습니다. 실제 DB 인증, S3 업로드, AI 탐지와 전체 수용 시험은 미실행입니다. [수용 점검표](../docs/MVP_ACCEPTANCE.md)의 NOT_RUN을 PASS로 변경하지 않았습니다.

다음 작업은 로컬 서비스 전체 연결, 실제 fixture 업로드·탐지·로컬 메일 수신을 포함한 수용 검증입니다.
