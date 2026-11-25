# Copilot 무료 한도 내 실험 자동화

2026-11-25: 인증 등록 및 실제 모델 호출 검증 완료. [CI #8 성공](https://github.com/KTheES/ArtGuard/actions/runs/45569512031)으로 첫 관측일을 수집했습니다. 아래는 재설정 시 참고하는 절차입니다.

## 비용 조건

2026-11-24 계정 화면에서 Copilot Student 활성화, 포함 크레딧 0/200 사용, Additional usage Not enabled 및 예산 $0, All AI Credit SKUs $0 / Usage blocked, Cloud Sandbox paid usage Disabled를 확인했습니다. 이 상태를 유지해야 합니다. 코드는 결제 설정을 바꾸지 않지만 계정의 결제 상태를 API로 재검증하는 기능은 없습니다.

별도 OpenAI/Anthropic 등 유료 API를 사용하지 않습니다. 외부 제공자 키가 들어오면 중단합니다. 하루 한 패널 요청, 180초 제한, 자동 재시도 없음, 실패 시 중단으로 설정했습니다. 무료 크레딧 소진 시 예산을 올리지 말고 누락일로 남깁니다. CLI 자체 한 번의 요청이 크레딧 한 개라는 뜻은 아닙니다.

## 필요한 사용자 인증

사용자가 Actions Secrets에 COPILOT_GITHUB_TOKEN을 등록했고 실제 Copilot 호출이 성공했습니다. 기존 PACKAGES_TOKEN은 변경하지 않았습니다.

1. GitHub Settings → Developer settings → Personal access tokens → **Fine-grained tokens**에서 새 토큰을 생성합니다.
2. Resource owner는 **KTheES**, Repository access는 **Public repositories**, Account permissions의 **Copilot Requests**만 허용합니다. 공식 인증 안내에 필요한 권한을 선택하고, Contents/Workflows 쓰기 권한은 추가하지 않습니다.
3. 만료일은 과제 검증 기간을 포함하는 짧은 기간으로 설정합니다.
4. ArtGuard → Settings → Secrets and variables → Actions → **New repository secret**에 이름 **COPILOT_GITHUB_TOKEN**으로 저장합니다. 토큰 값은 채팅·코드·로그에 넣지 않습니다.
5. Week 13 Lean Experiment의 Run workflow를 실행해 Collect fresh Copilot persona evaluation 이후 모든 단계가 성공했는지 확인합니다.

학생 플랜은 이용 자격이고 PAT는 Actions 인증 수단입니다. 기본 GITHUB_TOKEN이나 classic PACKAGES_TOKEN을 이름만 바꿔 사용하지 않습니다.

[GitHub 공식 Copilot CLI 인증 안내](https://docs.github.com/en/copilot/how-tos/copilot-cli/set-up-copilot-cli/authenticate-copilot-cli)

## 실험 설계와 증빙

본 수집 예정 기간은 **2026-11-25~12-08 KST**, 최종 검토는 12월 9일입니다. 11월 23일 수동 평가와 그 이전 강제 클릭은 파일을 보존하되 본 수집 기간에서 제외합니다. 인증 지연이나 예약 누락으로 유효 관측일이 14개 미만이면 미완료입니다.

페르소나의 역할·시나리오와 실제 화면 안내만 전달하며 이전 선호·피드백은 프롬프트에 넣지 않습니다. 10명은 하나의 LLM 세션 안에서 평가하는 가상 역할이며 독립적인 실제 사용자 10명이 아닙니다. 자동 모델 선택은 학생 플랜과 호환되지만 모델이 바뀔 수 있어 연구 한계로 기록합니다.

CLI는 비어 있는 임시 작업 공간과 별도 설정을 사용합니다. 도구·MCP·사용자 지침을 차단하고 메모리를 활성화하지 않습니다. 생성 응답은 JSON 구조, 10개 ID, A/B 배정, 선택과 이유를 검증한 후에만 날짜별 파일로 저장합니다. 유효한 당일 파일이 있으면 다시 호출하지 않습니다. 예전 날짜로 복사하지 않습니다.

Actions는 새 평가 → Playwright 재현 → 지표 집계 → Git 저장 → Issues 백로그/7·14일 리포트 갱신 → Artifact 저장을 수행합니다. 불완전한 모델 응답과 인증 실패는 성공 데이터로 처리하지 않습니다. Projects 보드는 만들지 않았습니다.

제출 링크: [Week13 문서](https://github.com/KTheES/ArtGuard/blob/main/docs/WEEK13_LEAN_STARTUP.md).
