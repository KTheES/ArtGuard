# CI 파이프라인 운영 가이드

## 목적과 결정 배경

`Application CI`는 프런트엔드의 Lint, Build, Matrix Test, CI Preview Deploy를 한 워크플로우에서 검증한다. Node.js 22는 프로젝트의 최소 지원 버전이며, Node.js 24와 Windows/Ubuntu 조합을 함께 검증해 런타임 및 OS 회귀를 조기에 발견한다.

## 파이프라인 구조

```text
Build + Lint
    │  frontend-build 아티팩트 업로드
    ▼
Matrix Test (Ubuntu/Windows × Node.js 22/24)
    │  빌드 아티팩트 다운로드 및 테스트
    ▼
Deploy CI Preview
       테스트된 아티팩트 다운로드 및 재업로드
       `ci-preview` 환경의 GitHub Deployment 상태 자동 기록
```

PR에서는 Build와 Matrix Test까지만 실행한다. `main`, `feat/**`, `fix/**` 브랜치 push 및 수동 실행에서는 모든 테스트가 성공한 뒤 `ci-preview` 환경에 배포 이력을 기록한다. 실제 운영 배포는 별도 `Record Production Deployment` 워크플로우가 담당한다.

## Secret 설정

선택형 외부 배포 알림이 필요하면 저장소의 `Settings → Environments → ci-preview → Environment secrets`에 `ARTGUARD_DEPLOY_WEBHOOK_URL`을 등록한다.

- 값은 소스, 워크플로우, 로그, 아티팩트에 기록하지 않는다.
- Secret이 없으면 Webhook 단계만 건너뛰고 CI Preview 배포 기록은 정상 완료된다.
- `GITHUB_TOKEN`은 Actions가 실행마다 발급하는 최소 권한 토큰을 사용한다.
- PR, 이슈, 리뷰 코멘트에 Secret 원문을 붙여 넣지 않는다.

## 실패 대응

1. Build 실패: `npm ci`, 구문 검사, 정적 파일 빌드 순서로 로그를 확인한다.
2. 특정 Matrix만 실패: 해당 OS와 Node.js 버전 조합을 로컬 또는 동일 Runner에서 재현한다.
3. Artifact 검증 실패: Build job의 업로드 경로와 Test/Deploy job의 다운로드 이름을 비교한다.
4. Webhook 실패: URL을 공개하지 말고 `ci-preview` 환경 Secret을 재등록한다.

## 실행 및 증적

- 워크플로우: `.github/workflows/application-ci.yml`
- 실행 내역: 저장소의 `Actions → Application CI`
- 아티팩트: 각 실행 상세 화면 하단의 `Artifacts`
