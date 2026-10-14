# Getting Started

이 문서는 ArtworkGuard를 처음 실행하는 개발자를 위한 최소 절차입니다.

## 1. 준비 사항

- Git
- JDK 21 이상
- Node.js 22 이상
- Docker Desktop(Linux 컨테이너 모드)

## 2. 저장소 받기

```powershell
git clone https://github.com/KTheES/ArtGuard.git
Set-Location ArtGuard
Copy-Item .env.example .env
```

`.env`에는 로컬 개발용 값만 입력하고 커밋하지 않습니다. 실제 비밀번호, 토큰, API 키는 Wiki·Issue·PR·로그에도 올리지 않습니다.

## 3. 로컬 인프라와 백엔드 실행

```powershell
docker compose up -d --wait
Set-Location backend
.\gradlew.bat bootRun
```

백엔드가 시작되면 `http://localhost:8080/actuator/health`에서 상태를 확인합니다. Swagger UI는 `http://localhost:8080/swagger-ui.html`입니다.

## 4. 프런트엔드 실행

별도 PowerShell에서 다음을 실행합니다.

```powershell
Set-Location frontend
npm test
npm run dev
```

정확한 명령은 저장소의 `frontend/package.json`과 [README](https://github.com/KTheES/ArtGuard/blob/main/frontend/README.md)를 우선합니다.

## 5. 최초 검증

```powershell
Set-Location backend
.\gradlew.bat test
.\gradlew.bat check bootJar
```

Docker 연결 시험은 `.\gradlew.bat integrationTest`로 수행합니다. 실패하면 [Troubleshooting](Troubleshooting)을 확인합니다.

## 다음 읽을 문서

- 코드를 변경하려면 [Development Guide](Development-Guide)
- 실행이 막히면 [Troubleshooting](Troubleshooting)

---

[Wiki 홈](Home) · [Development Guide](Development-Guide) · [Troubleshooting](Troubleshooting)
