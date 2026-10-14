# Troubleshooting

문제를 재현할 때 비밀번호, 토큰, 쿠키, 개인 데이터가 로그에 포함되지 않았는지 먼저 확인합니다.

## Docker Desktop Inference manager 오류

`dockerInference` Unix 소켓 파일에 접근할 수 없다는 메시지가 나오면 Docker Desktop을 완전히 종료하고, 작업 관리자에서 남은 Docker 프로세스가 종료된 것을 확인한 뒤 다시 시작합니다. 프로젝트 데이터 볼륨은 임의로 삭제하지 않습니다. 계속 실패하면 Docker Desktop의 진단 로그에서 민감정보를 가린 후 오류 시각과 버전을 기록합니다.

## 컨테이너가 준비되지 않음

```powershell
docker compose ps
docker compose logs --tail 100 postgres redis kafka
```

`.env`와 현재 PowerShell의 `DB_PASSWORD`, `REDIS_PASSWORD`가 일치하는지 확인합니다. 로그는 공유 전에 비밀값을 마스킹합니다.

## 8080 또는 프런트엔드 포트 충돌

기존 개발 서버를 정상 종료한 후 다시 시작합니다. 임의의 프로세스를 종료하기 전에 해당 PID가 ArtworkGuard 개발 프로세스인지 확인합니다.

## Gradle/JDK 오류

```powershell
java -version
Set-Location backend
.\gradlew.bat --version
.\gradlew.bat test --stacktrace
```

JDK 21 이상인지 확인합니다. 의존성 다운로드 실패는 네트워크·프록시를 확인한 뒤 재시도하고, 캐시 전체 삭제는 마지막 수단으로 사용합니다.

## 통합 테스트 실패

통합 테스트는 PostgreSQL, Redis, Kafka와 Docker가 필요합니다. `docker compose ps`가 정상이어도 테스트 격리 컨테이너가 실패할 수 있으므로 실패한 테스트 이름과 첫 원인 예외를 확인합니다.

## GitHub Actions 실패

Actions 실행 페이지에서 최초로 실패한 Step을 확인합니다. Secret 값은 출력하지 말고 Secret 이름과 존재 여부만 확인합니다. 로컬에서 동일 명령을 재현한 뒤 Feature 브랜치에 수정 커밋을 추가합니다.

## 해결되지 않을 때

Issue에 운영체제, 도구 버전, 재현 절차, 기대 결과, 실제 결과, 민감정보를 제거한 최소 로그를 남깁니다. 지원 운영 기준은 [지원 운영 가이드](https://github.com/KTheES/ArtGuard/blob/main/docs/SUPPORT_OPERATIONS.md)를 따릅니다.

---

[Wiki 홈](Home) · [Getting Started](Getting-Started) · [Development Guide](Development-Guide)
