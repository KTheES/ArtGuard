# 13주차 Lean Startup 실험 운영

시작일: 2026-11-22 KST. 운영 예정: 2026-11-22~2026-12-05 (14일). 종료 검토: 2026-12-06.

**현재 상태: 1일차. 2주 운영 요건은 아직 완료되지 않았습니다.** 10명은 과제에서 허용한 LLM 가상 페르소나이며 실제 사람 인터뷰가 아닙니다.

| 요구사항 | 코드·데이터·증거 | 상태 |
|---|---|---|
| 다른 페르소나 10명 피드백 | [페르소나와 LLM 피드백 원본](../experiments/week13/personas.json) | 10개 작성 |
| 사용자 시나리오 코드 생성·평가 | [Playwright 패널 시나리오](../frontend/e2e/week13-panel.spec.js) | 로컬 10/10 통과 |
| Feature Flag A/B 테스트 | [기존 플래그/variant](../frontend/feature-flags.js), [실험 계획](../experiments/week13/plan.json) | control/treatment 50:50 해시 배정 |
| 2주 지표 수집 | [일별 원본](../reports/week13/daily), [지표 JSON](../reports/week13/metrics.json), [일별 보고서](../reports/week13/REPORT.md) | 1/14일, 진행 중 |
| Actions 자동화 | [워크플로 코드](../.github/workflows/week13-experiment.yml), [CI 실행 목록](https://github.com/KTheES/ArtGuard/actions/workflows/week13-experiment.yml) | 매일 09:00 KST 실행 설정 |
| Issues 백로그·주간 리포팅 | [실험 이슈 검색](https://github.com/KTheES/ArtGuard/issues?q=is%3Aissue+%22%5BWeek13%5D%22) | Actions가 생성·갱신, 7/14 관측일에 주간 댓글 |
| Pivot/Persevere | [결정 기록](../experiments/week13/DECISION.md) | 문구 잠정 Pivot, 최종 판단 보류 |

## 생성 및 평가 방법

Codex LLM이 기존 앱 코드를 검토하고 경험, 접근성, 개인정보 우려, 사용 기기와 작업 목적이 다른 10개 페르소나를 생성했습니다. 각 페르소나의 시나리오, 선호 variant, 자유 피드백을 JSON에 기록했습니다. 피드백은 LLM이 작성한 정성 평가이며 브라우저 실행으로 생성된 사용자 의견이 아닙니다.

Playwright는 10개 가상 계정의 공통 로그인→작품 목록→배정된 CTA→업로드 화면 동선을 검증합니다. P03에는 키보드 Enter, P10에는 모바일 뷰포트를 적용했습니다. 각 페르소나의 법률·개인정보 우려까지 E2E가 검증한다는 뜻은 아닙니다. 백엔드 응답은 mock이며 실제 이미지 업로드/외부 서비스 연동은 측정하지 않습니다.

`EXPERIMENT_UPLOAD_CTA`가 활성화된 기존 앱에서 실험 키 `upload-cta-v1`을 사용합니다. 클릭률 분모는 해당 날짜/variant의 고유 페르소나 노출 수, 분자는 클릭한 페르소나 수입니다. 새로고침 노출은 사용자 단위로 중복 계산하지 않습니다. 원본은 P01~P10으로 식별해 기존 로그의 100개 해시 버킷 충돌을 집계에 사용하지 않습니다.

## 지표 해석과 종료 조건

최초 control 4명, treatment 6명이며 두 군의 scripted CTR은 100%입니다. 매일 같은 가상 패널을 재실행하므로 CTR 차이나 유의성을 제품 전환율 개선으로 주장할 수 없습니다. 실행 날짜 수, 시나리오 통과 여부, A/B 이벤트 연결의 안정성을 관측합니다. 실제 고객 행동 기반 성과를 요구하는 평가라면 별도의 동의받은 사용자 운영 데이터가 필요합니다.

날짜는 실제 실행 시각의 KST로 기록하고 미래 날짜를 생성하지 않습니다. 같은 날짜 재실행은 그날 스냅샷을 갱신하며 새 관측일로 세지 않습니다. 서로 다른 14개 관측일이 있어야 보고서가 완료 상태가 됩니다. 예약 실행 누락은 소급 생성하지 않습니다.

## 결과 확인

Actions 실행의 `Run ten synthetic user scenarios`에서 10개 결과를, `Aggregate dated observations`에서 일별 지표를 확인합니다. `week13-experiment-<run_id>` Artifact에는 보고서와 E2E 결과가 포함됩니다(90일). Issues 백로그에는 작업 목록과 현재 보고서가 자동 갱신됩니다. Projects 보드는 구성하지 않았습니다.

로컬 재현: `cd frontend`, `npx playwright test e2e/week13-panel.spec.js`, 저장소 루트에서 `node experiments/week13/report.mjs`.
