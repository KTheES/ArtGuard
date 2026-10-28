# 패키지·컨테이너 배포 및 의존성 보안 운영 가이드

## 결정 배경

프런트엔드 산출물을 npm 패키지와 OCI 이미지 두 형태로 재현 가능하게 배포하고, 의존성 갱신과 취약점 감시를 자동화한다. 인증 토큰은 저장소에 기록하지 않고 GitHub Actions가 실행마다 발급하는 최소 권한 `GITHUB_TOKEN`을 사용한다.

## npm 패키지 배포

- 패키지: `@kthes/artworkguard-web`
- Registry: `https://npm.pkg.github.com`
- 기준 버전: `1.0.0`
- Patch 업데이트: `1.0.1`
- 배포 태그: `frontend-v1.0.1`
- 워크플로우: `.github/workflows/package-publish.yml`

`frontend-v<version>` 태그와 `package.json` 버전이 일치해야 publish한다. Lint, Test, Build, `npm pack --dry-run`이 모두 성공한 뒤 `packages: write` 권한으로 배포한다. `NODE_AUTH_TOKEN` 값은 로그나 `.npmrc`에 저장하지 않는다.

기본 인증은 실행별 `GITHUB_TOKEN`이다. 계정의 Packages installation 연결 오류가 확인된 경우에만 `write:packages` 범위의 classic PAT를 저장소 Secret `PACKAGES_TOKEN`으로 등록한다. 워크플로우는 이 Secret이 있으면 우선 사용하고, 없으면 `GITHUB_TOKEN`으로 복귀한다. 토큰 원문을 코드, 이슈, PR, Actions 입력 또는 채팅에 남기지 않는다.

## Docker 이미지

- 로컬 이미지: `artguard-frontend:1.0.1-local`
- 원격 이미지: `ghcr.io/kthes/artguard/artworkguard-web`
- 워크플로우: `.github/workflows/container-publish.yml`
- 태그: `latest`, 전체 commit SHA, `frontend-v*`에서 추출한 버전
- Build cache: GitHub Actions cache backend

멀티 스테이지 Dockerfile의 build 단계에서 Lint, Test, 정적 자산 Build를 수행한다. build/runtime base image는 검증한 `node:22-alpine` digest로 고정하고 Dependabot이 갱신한다. runtime은 non-root `node` 사용자와 HTTP health check를 사용한다.

### 2026-10-28 로컬 검증

| 항목 | 결과 |
|---|---|
| Docker Engine | 28.3.2, Linux x86_64 |
| 이미지 Build | 성공 |
| 컨테이너 Health | `healthy` |
| HTTP 응답 | `200` |
| 본문 검증 | `ArtworkGuard` 확인 |
| 테스트 | Docker build 내부 6/6 통과 |

Docker Desktop 시작을 막던 손상된 `dockerInference` 런타임 소켓은 삭제하지 않고 `run.stale-20261028-1315` 디렉터리에 보존했으며, 새 `run` 디렉터리에서 엔진 기동을 확인했다.

## Dependabot 정책

| Ecosystem | 경로 | 일정(KST) | 그룹 |
|---|---|---|---|
| npm | `/frontend` | 월요일 04:00 | minor/patch 통합 |
| GitHub Actions | `/` | 월요일 04:15 | minor/patch 통합 |
| Docker | `/frontend` | 월요일 04:30 | base image 통합 |
| Docker | `/ai-service` | 월요일 04:45 | base image 통합 |

자동 병합은 다음 조건을 모두 만족할 때만 활성화한다.

1. 작성자가 `dependabot[bot]`이다.
2. ecosystem이 npm이다.
3. direct development dependency이다.
4. SemVer patch 업데이트이다.
5. 저장소의 필수 검사를 통과한다.

Major, minor, production dependency, GitHub Actions, Docker 업데이트는 수동 검토한다.

## 보안 스캔 자동화

`Dependency Security`는 main 의존성 변경, PR, 매주 월요일 06:00 KST, 수동 실행에서 동작한다.

1. `npm ci`로 잠금 파일을 재현한다.
2. `npm audit --json` 결과를 JSON과 Markdown으로 변환한다.
3. 두 파일을 90일 보관 Actions artifact로 업로드한다.
4. high 또는 critical이 있으면 `[Security]` 이슈를 생성하거나 갱신하고 workflow를 실패 처리한다.
5. 이슈와 리포트에는 토큰·사설 Registry 인증정보를 포함하지 않는다.

### 기준선

- 로컬 `npm audit`: 취약점 0건
- High/Critical: 0건
- 정책 결과: PASS

## 트러블슈팅

- npm publish 403: workflow의 `packages: write`, package scope, 저장소 연결을 확인한다.
- 동일 버전 publish 실패: 기존 버전을 덮어쓰지 말고 patch 버전을 올린 새 태그를 사용한다.
- GHCR push 실패: 이미지 이름이 소문자인지와 `packages: write`를 확인한다.
- Docker 시작 시 `dockerInference` 오류: Docker/WSL을 중지하고 `%LOCALAPPDATA%\Docker\run`을 백업한 뒤 새 runtime 디렉터리로 재시작한다.
- audit JSON 생성 실패: npm Registry 연결과 lockfile 무결성을 확인하며 토큰을 로그에 출력하지 않는다.
