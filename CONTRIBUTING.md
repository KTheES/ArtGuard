# ArtworkGuard 기여 가이드

ArtworkGuard에 기여해 주셔서 감사합니다. 이 문서는 외부 오픈소스 기여자와 조직 내부 기여자가 같은 절차로 안전하게 협업하도록 돕습니다.

## 행동 원칙

- 참여 전 [행동 강령](CODE_OF_CONDUCT.md)을 읽고 준수합니다.
- 공개 이슈, PR, 로그에 토큰, 비밀번호, 개인정보, 고객 데이터 또는 저작권이 불명확한 이미지를 올리지 않습니다.
- 보안 취약점은 공개 이슈로 신고하지 말고 저장소의 **Security** 탭에서 비공개 신고 기능을 사용합니다. 해당 기능이 보이지 않으면 세부 내용을 공개하지 않은 채 관리자에게 비공개 연락 방법을 요청합니다.
- 탐지 결과는 검토가 필요한 유사 후보이며 법적 침해 판정으로 표현하지 않습니다.

## 기여 절차

1. 기존 이슈와 PR을 검색합니다.
2. 큰 변경은 구현 전에 이슈 또는 Discussion으로 범위와 설계를 합의합니다.
3. 저장소를 fork하거나 권한이 있다면 최신 `main`에서 기능 브랜치를 만듭니다.
4. 코드와 테스트, 관련 문서를 함께 변경합니다.
5. 로컬 검증 후 PR 템플릿을 빠짐없이 작성하고 리뷰를 요청합니다.

브랜치 이름은 `feat/<topic>`, `fix/<topic>`, `docs/<topic>`, `refactor/<topic>` 형식을 권장합니다. 하나의 PR에는 하나의 논리적 변경만 포함합니다.

## 개발 환경

필수 도구와 서비스는 다음과 같습니다.

- JDK 21
- Node.js 22
- Python 3.12 권장
- Docker Desktop(Linux 컨테이너 모드)

전체 실행 방법과 환경 변수 생성 예시는 [README](README.md#실행-powershell)를 따릅니다. 실제 비밀값은 커밋하지 않고 `.env.example`에는 변수 이름과 안전한 예시만 둡니다.

## 검증

변경 영역에 맞는 검증을 실행합니다.

```powershell
# 백엔드 단위 테스트 및 빌드
Set-Location backend
.\gradlew.bat check bootJar

# 프런트엔드 테스트
Set-Location ..\frontend
npm test

# AI 서비스 테스트
Set-Location ..\ai-service
python -m pytest
```

Docker 기반 통합 테스트가 필요한 변경은 `backend`에서 `.\gradlew.bat integrationTest`도 실행합니다. 로컬 환경 문제로 실행하지 못한 검증은 PR에 사유와 위험을 명확히 기록합니다.

## 커밋과 PR

[Conventional Commits](https://www.conventionalcommits.org/) 형식을 사용합니다.

```text
feat: add similarity review filter
fix: reject oversized source images
docs: document inner source rollout
test: cover detection threshold boundary
```

PR은 다음 조건을 충족해야 합니다.

- 연결된 이슈와 변경 목적이 설명되어 있습니다.
- 정상 경로, 경계값, 실패 경로 테스트가 포함되어 있습니다.
- 기존 테스트가 회귀 없이 통과합니다.
- API, 설정, 데이터 구조 또는 운영 절차가 바뀌면 문서를 함께 갱신합니다.
- 생성물, 빌드 캐시, 로컬 설정과 민감정보가 포함되지 않았습니다.
- 최소 1명의 승인을 받고 필수 CI가 통과한 뒤 병합합니다.

리뷰 의견은 차단 사안에 `[MUST]`, 개선 권고에 `[SHOULD]`를 사용합니다. 작성자는 해결 내용 또는 수용하지 않은 이유를 답글로 남깁니다.

## 이슈 작성

버그는 재현 절차, 기대 결과, 실제 결과, 실행 환경을 포함합니다. 기능 제안은 해결하려는 문제, 사용자 가치, 수용 조건과 제외 범위를 설명합니다. 재현 자료는 익명화하고 민감정보를 제거합니다.

## 라이선스

기여물을 제출하면 해당 기여물을 저장소의 [MIT License](LICENSE)로 배포하는 데 동의한 것으로 간주합니다. 제3자 코드나 자산은 원 라이선스와 출처를 확인하고, 호환 여부가 불명확하면 포함하지 않습니다.
