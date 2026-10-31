# Docker 이미지 로컬 실행 검증

검증일: **2026-10-31**

## 검증 대상

- GHCR 이미지: `ghcr.io/kthees/artguard-frontend:1.0.3`
- Dockerfile: [`frontend/Dockerfile`](../frontend/Dockerfile)
- 자동화: [`.github/workflows/container-publish.yml`](../.github/workflows/container-publish.yml)
- 성공 실행: [Publish Frontend Container #20](https://github.com/KTheES/ArtGuard/actions/runs/41195098338)
- 상세 Job 로그: [Build and push GHCR image](https://github.com/KTheES/ArtGuard/actions/runs/41195098338/job/124519566172)

## CI에서 수행한 로컬 실행 검증

GitHub Actions 러너 내부에서 다음 절차를 수행한 뒤에만 GHCR로 이미지를 푸시합니다.

```bash
docker build --tag artguard-frontend:ci --file frontend/Dockerfile frontend
docker run --detach --name artguard-frontend-ci \
  --publish 127.0.0.1:5173:5173 artguard-frontend:ci
docker inspect --format '{{.State.Health.Status}}' artguard-frontend-ci
curl --fail --silent --show-error http://127.0.0.1:5173/ | grep --quiet "ArtworkGuard"
docker rm --force artguard-frontend-ci
```

검증 결과는 **Success**입니다. 실행 #20에서 로컬 검증 이미지 빌드, 컨테이너 시작, Docker health 확인, HTTP 응답 확인을 모두 통과한 후 `ghcr.io/kthees/artguard-frontend:1.0.3`을 푸시했습니다.

## GHCR 이미지 수동 pull/run 재현

패키지가 비공개이면 먼저 `read:packages` 권한 토큰으로 로그인합니다.

```bash
echo "$PACKAGES_TOKEN" | docker login ghcr.io -u KTheES --password-stdin
docker pull ghcr.io/kthees/artguard-frontend:1.0.3
docker run --detach --name artguard-frontend-local \
  --publish 127.0.0.1:5173:5173 ghcr.io/kthees/artguard-frontend:1.0.3
curl --fail http://127.0.0.1:5173/
docker inspect --format '{{.State.Health.Status}}' artguard-frontend-local
docker rm --force artguard-frontend-local
```

토큰 값은 문서나 실행 로그에 기록하지 않습니다.
