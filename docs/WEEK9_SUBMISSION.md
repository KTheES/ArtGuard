# 9주차 증빙: GitHub Packages 및 의존성 보안 자동화

| 요구사항 | 결과 | 증거 링크 | 성공 실행 |
|---|---|---|---|
| npm 패키지 배포 (`1.0.2 → 1.0.3`) | ✅ | [`@kthees/artguard-frontend` 패키지](https://github.com/users/KTheES/packages/npm/package/artguard-frontend) | [1.0.2 게시 #9](https://github.com/KTheES/ArtGuard/actions/runs/41194258619) · [1.0.3 게시 #10](https://github.com/KTheES/ArtGuard/actions/runs/41195098309) |
| Docker 이미지 자동 빌드/푸시 | ✅ | [`ghcr.io/kthees/artguard-frontend`](https://github.com/users/KTheES/packages/container/package/artguard-frontend) | [Docker 게시 #20](https://github.com/KTheES/ArtGuard/actions/runs/41195098338) |
| 로컬 실행 검증 | ✅ | [`docs/docker-verify.md`](docker-verify.md) | [Build and push GHCR image 로그](https://github.com/KTheES/ArtGuard/actions/runs/41195098338/job/124519566172) |
| Dependabot 정책 | ✅ | [`.github/dependabot.yml`](https://github.com/KTheES/ArtGuard/blob/main/.github/dependabot.yml) · [자동머지 조건](https://github.com/KTheES/ArtGuard/blob/main/.github/workflows/dependabot-auto-merge.yml) | [Dependabot PR #30](https://github.com/KTheES/ArtGuard/pull/30) |
| 보안 스캔 자동화 | ✅ | [`npm audit` 리포트/Artifact](https://github.com/KTheES/ArtGuard/actions/runs/41195094601) | [Dependency Security #13](https://github.com/KTheES/ArtGuard/actions/runs/41195094601) |

검증일: **2026-10-31**

- 저장소: [KTheES/ArtGuard](https://github.com/KTheES/ArtGuard)
- 제출 기준 브랜치: `main`
- 패키지 네임스페이스: GitHub 사용자명 `KTheES`의 소문자 표기인 `kthees`

이 문서는 과제 요구사항과 공개된 실행 증거를 한 URL에서 확인하기 위한 제출 페이지입니다. GitHub Pages 배포는 과제 요구사항이 아니므로 GitHub가 직접 렌더링하는 이 문서를 제출 URL로 사용합니다.

## 1. npm 패키지 게시 및 버전 업데이트

- 패키지: [`@kthees/artguard-frontend`](https://github.com/users/KTheES/packages/npm/package/artguard-frontend)
- 실제 버전 업데이트: **1.0.2 → 1.0.3**
- 1.0.2 게시 성공: [Publish npm Package 실행 #9](https://github.com/KTheES/ArtGuard/actions/runs/41194258619)
- 1.0.3 게시 성공: [Publish npm Package 실행 #10](https://github.com/KTheES/ArtGuard/actions/runs/41195098309)
- 버전 태그: [`frontend-v1.0.2`](https://github.com/KTheES/ArtGuard/tree/frontend-v1.0.2), [`frontend-v1.0.3`](https://github.com/KTheES/ArtGuard/tree/frontend-v1.0.3)
- 자동화 설정: [`.github/workflows/package-publish.yml`](https://github.com/KTheES/ArtGuard/blob/main/.github/workflows/package-publish.yml)
- 버전 정의: [`frontend/package.json`](https://github.com/KTheES/ArtGuard/blob/main/frontend/package.json)

태그가 가리키는 버전과 `package.json` 버전이 다르면 워크플로가 중단됩니다. 게시 전 `npm ci`, lint, test, build, `npm pack --dry-run`을 수행하며 게시에는 저장소 내장 `GITHUB_TOKEN`의 `packages: write` 권한을 사용합니다.

## 2. Docker 이미지 자동 빌드·푸시 및 실행 검증

- 이미지: `ghcr.io/kthees/artguard-frontend`
- GHCR 패키지: [artguard-frontend Container package](https://github.com/users/KTheES/packages/container/package/artguard-frontend)
- 자동화 설정: [`.github/workflows/container-publish.yml`](https://github.com/KTheES/ArtGuard/blob/main/.github/workflows/container-publish.yml)
- 1.0.3 게시·실행 검증 성공: [Publish Frontend Container 실행 #20](https://github.com/KTheES/ArtGuard/actions/runs/41195098338)

워크플로의 검증 순서는 다음과 같습니다.

1. `frontend/Dockerfile`로 로컬 검증 이미지 `artguard-frontend:ci` 빌드
2. `127.0.0.1:5173`에 컨테이너 실행
3. Docker health가 `healthy`가 될 때까지 확인
4. `curl http://127.0.0.1:5173/` 응답에 `ArtworkGuard`가 있는지 검사
5. smoke test가 성공한 경우에만 GHCR 로그인 후 이미지 푸시

따라서 단순 빌드뿐 아니라 CI 러너 안에서의 실제 로컬 컨테이너 실행도 자동 검증됩니다.

## 3. Dependabot 정책

- 설정: [`.github/dependabot.yml`](https://github.com/KTheES/ArtGuard/blob/main/.github/dependabot.yml)
- 자동머지 조건: [`.github/workflows/dependabot-auto-merge.yml`](https://github.com/KTheES/ArtGuard/blob/main/.github/workflows/dependabot-auto-merge.yml)
- 생성된 PR 확인: [Dependabot PR 목록](https://github.com/KTheES/ArtGuard/pulls?q=is%3Apr+author%3Aapp%2Fdependabot)

| 대상 | 일정 (Asia/Seoul) | 그룹 |
|---|---|---|
| npm (`/frontend`) | 매주 월요일 04:00 | minor/patch 그룹 |
| GitHub Actions | 매주 월요일 04:15 | minor/patch 그룹 |
| Docker (`/frontend`) | 매주 월요일 04:30 | frontend base image 그룹 |
| Docker (`/ai-service`) | 매주 월요일 04:45 | AI base image 그룹 |

자동머지는 **Dependabot이 만든 npm 직접 개발 의존성의 semver patch 업데이트**에만 `squash` 방식으로 허용합니다. major/minor, 운영 의존성, GitHub Actions, Docker 변경은 수동 검토 대상으로 남깁니다.

## 4. npm audit 보안 스캔과 리포트·이슈 자동화

- 자동화 설정: [`.github/workflows/dependency-security.yml`](https://github.com/KTheES/ArtGuard/blob/main/.github/workflows/dependency-security.yml)
- 리포트 생성기: [`frontend/scripts/render-audit-report.mjs`](https://github.com/KTheES/ArtGuard/blob/main/frontend/scripts/render-audit-report.mjs)
- 최신 성공 실행: [Dependency Security 실행 #13](https://github.com/KTheES/ArtGuard/actions/runs/41195094601)
- 전체 실행 목록: [Dependency Security workflow](https://github.com/KTheES/ArtGuard/actions/workflows/dependency-security.yml)

push, pull request, 수동 실행과 매주 월요일 06:00 KST 스케줄에서 다음 결과를 자동 생성합니다.

- 원본 `npm-audit.json`
- 사람이 읽을 수 있는 `NPM_AUDIT_REPORT.md`
- GitHub Actions Job Summary
- 90일 보관 Artifact
- high 또는 critical 취약점 발견 시 기존 보안 이슈에 댓글을 추가하거나 새 이슈 생성
- high 또는 critical 취약점이 있으면 워크플로 실패 처리

## 제출 URL

이 문서의 공개 URL을 제출합니다.

<https://github.com/KTheES/ArtGuard/blob/main/docs/WEEK9_SUBMISSION.md>
