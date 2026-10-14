# Development Guide

## 브랜치 전략

`main`에 직접 작업하지 않습니다. 최신 `origin/main`에서 목적별 Feature 브랜치를 만듭니다.

```powershell
git fetch origin --prune
git switch -c feat/짧은-기능명 origin/main
```

권장 접두사는 `feat/`, `fix/`, `docs/`, `ci/`, `refactor/`입니다.

## Conventional Commits

커밋 제목은 `<type>: <summary>` 형식을 사용합니다.

- `feat`: 사용자 기능
- `fix`: 버그 수정
- `refactor`: 동작 변경 없는 구조 개선
- `perf`: 성능 개선
- `test`: 테스트 추가·수정
- `style`: 포맷 변경
- `docs`: 문서 변경
- `chore`: 빌드·설정·유지보수
- `ci`: CI/CD 변경

예: `docs: add linked project wiki guides`

## 변경 전 점검

1. `git status`로 사용자 작업과 충돌하지 않는지 확인합니다.
2. `.env`, 키 파일, 토큰, 운영 데이터가 추적되지 않는지 검사합니다.
3. 정상 경로·경계값·실패 경로 테스트를 추가합니다.
4. 관련 단위·통합 테스트를 실행합니다.

## Pull Request와 리뷰

- PR 템플릿의 테스트, 위험, 롤백 항목을 채웁니다.
- `[MUST]`는 병합 전 반드시 해결할 문제에 사용합니다.
- `[SHOULD]`는 후속 개선이 가능한 권고에 사용합니다.
- CI가 모두 통과하고 `[MUST]`가 해결되기 전에는 병합하지 않습니다.
- 민감정보가 포함된 로그나 화면은 리뷰 코멘트에 붙이지 않습니다.

자세한 규칙은 [CONTRIBUTING](https://github.com/KTheES/ArtGuard/blob/main/CONTRIBUTING.md)과 [리뷰 가이드](https://github.com/KTheES/ArtGuard/blob/main/docs/REVIEW_GUIDE.md)를 참고합니다.

## 문서화 기준

기능 변경 시 필요한 범위에서 결정 배경(ADR), 아키텍처, API 명세, 트러블슈팅, 회의·액션 항목, FAQ·온보딩을 함께 갱신합니다.

---

[Wiki 홈](Home) · [Getting Started](Getting-Started) · [Troubleshooting](Troubleshooting)
