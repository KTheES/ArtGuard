# 13주차 Lean Startup 실험 운영

**2026-11-25: Copilot 자동 평가·브라우저 검증·집계·이슈 갱신 성공. 본 수집 1/14일, 2주 운영은 아직 미완료입니다.**

[첫 자동 평가 성공 CI #8](https://github.com/KTheES/ArtGuard/actions/runs/45569512031) (1분 6초): 검증 테스트 11개, 페르소나 시나리오 10개, 결과 커밋과 보고서/이슈 갱신, Artifact 저장까지 완료했습니다. [10명 응답 원본](../experiments/week13/assessments/2026-11-25.json), [브라우저 관측](../reports/week13/evaluated-days/2026-11-25.json), [CLI 구조화 출력](../reports/week13/model-output/2026-11-25.jsonl)을 확인할 수 있습니다.

첫날 합계: 클릭 6명, 보류 4명, 이탈 0명, 테스트 실패 0건. control은 1/4(25%), treatment는 5/6(83.3%)의 모의 CTR입니다. 페르소나 구성 차이와 작은 표본 때문에 이 차이를 treatment의 인과적 효과나 승리로 해석하지 않습니다.

[연결 검증 CI #6](https://github.com/KTheES/ArtGuard/actions/runs/45424194462): 테스트 단계 통과 후 `Missing COPILOT_GITHUB_TOKEN: no model call made`로 중단됐습니다. 이는 인증 미등록으로 인한 **실패 실행**이며 자동 평가 성공 증거가 아닙니다. CLI 설치·모델 호출·시나리오 실행·새 관측일 저장은 건너뛰었습니다.

추가 유료 사용은 허용하지 않습니다. Copilot Student의 포함 크레딧만 사용하며, 계정의 추가 사용 예산 $0과 Cloud Sandbox paid usage Disabled를 유지합니다. 코드가 과금 설정을 변경하지는 않지만 계정 예산을 자동 조회하는 기능은 없으므로 이 설정을 유지해야 합니다.

[무료 한도·인증 설정 및 운영 안내](WEEK13_COPILOT_SETUP.md)

| 요구사항 | 증거 | 현재 상태 |
|---|---|---|
| 서로 다른 LLM 페르소나 10명 | [역할·시나리오](../experiments/week13/personas.json), [11월 23일 수동 LLM 피드백](../experiments/week13/assessments/2026-11-23.json) | 탐색용 피드백 10명 작성; 실제 사람 아님 |
| 사용자 시나리오 코드와 평가 | [Playwright](../frontend/e2e/week13-panel.spec.js) | 클릭·보류·이탈 및 이벤트 연결; 기존 로컬 10/10 통과 |
| 자동 LLM 피드백 | [수집 코드](../experiments/week13/collect.mjs), [프롬프트·검증](../experiments/week13/copilot-evaluation.mjs) | 11월 25일 실제 Copilot 응답 10개 생성·검증 완료 |
| Feature Flag A/B | [플래그](../frontend/feature-flags.js), [계획](../experiments/week13/plan.json) | 기존 upload-cta-v1 해시 배정 유지 |
| 2주 지표 변화 | [보고서](../reports/week13/REPORT.md), [지표 JSON](../reports/week13/metrics.json), [일별 기록](../reports/week13/evaluated-days) | 11월 25일 기준 1/14일; 최신 일수는 보고서 참조 |
| 수집·주간 보고 자동화 | [Actions](../.github/workflows/week13-experiment.yml), [실행 목록](https://github.com/KTheES/ArtGuard/actions/workflows/week13-experiment.yml), [이슈](https://github.com/KTheES/ArtGuard/issues?q=is%3Aissue+%22%5BWeek13%5D%22) | 날짜별 수집, 백로그 갱신, 7/14 관측일 댓글; Projects 미사용 |
| Pivot/Persevere | [결정 기록](../experiments/week13/DECISION.md) | 문구 잠정 Pivot, 제품 효과 판단 보류 |

## 운영 일정과 평가 방법

본 수집 예정 기간은 **2026-11-25~12-08 KST**, 검토일은 **12월 9일**입니다. 매일 09:00 KST 예약 실행합니다. 인증 지연·무료 한도 소진·실행 누락으로 14일을 채우지 못하면 미완료로 남습니다. 누락일의 응답을 소급 생성하지 않습니다.

이전 수동 평가(11월 23일)와 강제 클릭 데이터는 탐색용 자료로 보존하되 새 관측일에 합산하지 않습니다. Copilot에는 역할·시나리오·화면 안내와 고정 배정 variant만 제공하고, 기존 선호·피드백을 전달하지 않습니다. 새 응답의 click/defer/leave, 이해도 1~5, 이유를 원본으로 저장합니다.

CLI는 별도 임시 작업 공간에서 도구/MCP/사용자 지침 없이 실행합니다. 메모리를 켜지 않습니다. 자동 모델 선택을 사용하므로 실제 모델은 바뀔 수 있습니다. 한 세션의 가상 역할 10개이며 독립적인 인간 표본이 아닙니다.

## 지표와 데이터 검증

Playwright는 LLM 선택을 재현하고 exposure와 upload_cta_click 이벤트를 검사합니다. 보류는 목록에 머물고, 이탈은 클릭 없이 떠나며, 클릭한 경우에만 등록 화면 도달을 검사합니다. 백엔드가 mock이므로 실제 업로드 완료 또는 법적 보호를 검증하는 것은 아닙니다.

CTR은 노출된 고유 페르소나 중 클릭 이벤트가 발생한 비율입니다. 보류·이탈·테스트 실패 수를 함께 기록하고, 이해도와 이유는 원본 평가에 보존합니다. 사용자 ID·A/B 배정·날짜·실행 ID·입력 해시를 검사하고, 과거 날짜도 재검증합니다. 불완전한 10명 결과는 유효 관측일로 세지 않습니다.

이 지표를 실제 고객 전환율이나 인과적 효과로 해석하지 않습니다. 같은 LLM 가상 패널의 반복 평가이며, 하루 CTR 변화만으로 승리 variant를 선언하지 않습니다.

## 검증 결과와 제출 방법

- [기존 수동 평가 재현 CI #3 성공](https://github.com/KTheES/ArtGuard/actions/runs/45114709224): 집계 검증 6개, 브라우저 시나리오 10개, 보고서/이슈/Artifact 저장. **Copilot 자동 생성 성공 증거는 아닙니다.**
- 2026-11-25 로컬: 기존 데이터 검증 6개 + Copilot 프롬프트·인증·응답 검증 4개 = **10/10 통과**. 이 테스트는 fixture를 사용하며 무료 크레딧을 소비하지 않습니다.
- 실제 자동 생성은 [CI #8](https://github.com/KTheES/ArtGuard/actions/runs/45569512031)의 Collect fresh Copilot persona evaluation 단계와 Artifact `week13-experiment-45569512031`에서 확인할 수 있습니다. 터미널 출력에 제어문자가 섞인 #7 실패를 수정하고 JSONL 구조화 출력으로 전환한 뒤 성공했습니다.
- Artifact에는 날짜별 평가와 보고서, Playwright 실패 스크린샷 등이 포함됩니다(90일).

제출 URL은 이 문서입니다. 제출 전에 14개의 실제 관측일과 최종 Pivot/Persevere 기록을 확인해야 합니다.
