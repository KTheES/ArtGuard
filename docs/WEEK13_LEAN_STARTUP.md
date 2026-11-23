# 13주차 Lean Startup 실험 운영

**2026-11-23 설계 수정. 2주 운영 요건은 아직 미완료입니다.**

기존 자동 클릭 데이터는 기능 점검용이므로 새 실험의 관측일에서 제외합니다. 수정된 수집 기간은 2026-11-23~2026-12-06 KST(14일), 종료 검토는 12월 7일입니다. 실행 누락은 소급 생성하지 않습니다.

| 요구사항 | 증거 링크 | 상태 |
|---|---|---|
| 서로 다른 LLM 페르소나 10명 | [페르소나](../experiments/week13/personas.json), [오늘의 판단·이해도·이유·평가 프롬프트](../experiments/week13/assessments/2026-11-23.json) | 대화형 LLM 수동 평가, 실제 사람 아님 |
| 사용자 시나리오 코드와 평가 | [Playwright 코드](../frontend/e2e/week13-panel.spec.js) | 판단에 따라 클릭·보류·이탈 재현; 키보드/모바일 조건 포함 |
| Feature Flag A/B | [플래그 코드](../frontend/feature-flags.js), [계획](../experiments/week13/plan.json) | 기존 키 upload-cta-v1, 사용자 해시 배정 유지 |
| 2주 지표 변화 | [현재 보고서](../reports/week13/REPORT.md), [지표 JSON](../reports/week13/metrics.json), [수정 후 일별 원본](../reports/week13/evaluated-days) | 최신 유효 관측일 수는 보고서 참조; 14일 미완료 |
| 자동 수집·검증·리포팅 | [워크플로](../.github/workflows/week13-experiment.yml), [CI 실행 목록](https://github.com/KTheES/ArtGuard/actions/workflows/week13-experiment.yml), [집계 검증 테스트](../experiments/week13/protocol.test.mjs) | 매일 09:00 KST; 당일 새 평가 파일 필수 |
| Issues 백로그·주간 보고 | [실험 이슈](https://github.com/KTheES/ArtGuard/issues?q=is%3Aissue+%22%5BWeek13%5D%22) | 유효 집계 후 갱신, 7/14 관측일에 댓글; Projects 미사용 |
| Pivot/Persevere | [결정 기록](../experiments/week13/DECISION.md) | 문구 잠정 Pivot; 제품 효과 판단 보류 |

## 평가 방식과 한계

오늘 평가자는 이 대화의 Codex 모델입니다. 기존 10개 페르소나의 역할과 목적, 실제 앱의 빈 작품 목록 안내와 배정된 CTA를 읽고 각자 click/defer/leave, 이해도 1~5점, 선택 이유를 작성했습니다. 기존 정성 피드백도 평가자에게 보였으므로 독립적인 블라인드 평가가 아닙니다. 모델 API가 자동 생성한 결과라고 주장하지 않습니다.

Playwright는 해당 판단을 재현하고 실제 프런트엔드 exposure/upload_cta_click 이벤트를 검사합니다. 보류는 목록에 머물고, 이탈은 클릭 없이 페이지를 떠납니다. 클릭한 경우에만 등록 화면 도달을 확인합니다. 테스트 통과는 판단대로 UI가 작동했다는 뜻이며 사용자 과업 성공·실제 파일 업로드 성공을 뜻하지 않습니다. 백엔드는 mock입니다.

CTR 분모는 실제 노출 이벤트가 있는 고유 페르소나, 분자는 실제 클릭 이벤트가 있는 페르소나입니다. 테스트 실패 수와 보류·이탈 수도 별도로 기록합니다. 이해도와 이유는 평가 원본에 보존합니다. 작은 반복 가상 패널이므로 인간 전환율, 독립 표본, 통계적 승리 또는 인과 효과로 해석할 수 없습니다.

## 데이터 무결성과 자동화 범위

당일 KST 평가 파일을 준비한 뒤 실행별 UUID와 입력 SHA-256을 기록합니다. 10명의 ID, 배정 variant, 평가 날짜, 실행 ID, 관측 시각, 클릭 여부를 검증합니다. 임시 결과는 매 실행 초기화하며 이전 실행이나 전날 데이터를 새 날짜로 복사하지 않습니다. 과거 파일도 재검증하고 잘못된 날짜는 제외 목록에 표시합니다. 실패한 테스트의 결과는 finally에서 기록하고 집계합니다. 프로세스 중단 등으로 10명 결과가 완성되지 않으면 그날은 인정하지 않습니다.

기존 [daily](../reports/week13/daily)와 [latest](../reports/week13/latest)는 자동 클릭 방식의 과거 증거로만 남기며 새 집계는 읽지 않습니다. 같은 날짜 재실행은 그날 스냅샷을 교체하고 관측일을 늘리지 않습니다.

**매일 새 LLM 판단을 만드는 모델 API 연결은 아직 없습니다.** 현재는 당일 평가 파일을 수동으로 추가해야 합니다. 파일이 없으면 워크플로가 실패하고 과거 결과를 재사용하지 않습니다. 예약 실행 설정만으로 2주 실험이 자동 완성되는 것은 아닙니다. 향후 API 연결 시 제공자·모델·프롬프트·생성일을 기록하고 별도로 검증해야 합니다.

## 재현과 CI 결과

저장소 루트에서 `node --test experiments/week13/protocol.test.mjs`, `node experiments/week13/prepare.mjs`를 실행합니다. 당일 작성한 평가가 없으면 prepare는 실패해야 정상입니다.

그다음 frontend에서 `WEEK13_PANEL=1 npx playwright test e2e/week13-panel.spec.js`(PowerShell은 먼저 `$env:WEEK13_PANEL='1'`)를 실행하고, 루트에서 `node experiments/week13/report.mjs`를 실행합니다. 일반 E2E에서는 이 패널을 건너뛰어 과제 데이터가 우연히 생성되지 않게 했습니다.

Actions의 Validate experiment protocol, Require fresh LLM assessment, Run ten synthetic user scenarios, Aggregate dated observations 단계에서 확인합니다. `week13-experiment-<run_id>` Artifact에는 원본·보고서·E2E 결과 및 실패 스크린샷이 포함됩니다(90일). 과거 [첫 CI 성공](https://github.com/KTheES/ArtGuard/actions/runs/45006175119)은 수정 전 방식이며 새 실험의 성공 증거로 사용하지 않습니다.
