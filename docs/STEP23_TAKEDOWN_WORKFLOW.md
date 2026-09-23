# STEP 23 신고 준비 및 수동 추적 API

탐지 확인 → 보존 증거 선택 → 권한 확인 → 초안 생성 → 마켓에서 직접 신고 → 접수번호/처리 상태 기록 흐름입니다. 외부 신고 전송이나 이메일 발송 기능은 없습니다. 프런트엔드 화면은 아직 없으며 아래 API를 제공합니다.

## API

JWT 인증과 본인 소유의 활성 작품·탐지가 필요합니다. 기존 증거 열람 구독 권한도 적용됩니다.

- `POST /api/v1/detections/{id}/takedown`: `{"evidenceId":"UUID","detectionVersion":2,"rightsConfirmed":true}`
- `GET /api/v1/detections/{id}/takedown`: 기존 초안과 수동 기록 조회. 미생성은 data=null.
- `PATCH /api/v1/detections/{id}/takedown`: `{"status":"SUBMITTED","version":0,"externalReference":"마켓 접수번호"}`
- 처리 완료 기록: `{"status":"RESOLVED","version":1}`

먼저 기존 탐지 상태 API로 `CONFIRMED`를 기록하고 최신 detection version을 사용합니다. 합성 탐지는 초안 생성을 거절합니다. 보존 증거는 해당 탐지에 속해야 합니다. 초안에는 작품 ID·제목, 증거 스냅샷(상품·판매자·URL·캡처 시각·해시), 권한 확인 사용자 및 탐지 버전, 사용자 보완 항목이 저장됩니다. 원본 작품 자료와 신고자 정보·권리 자료는 사용자가 준비해야 합니다.

초안은 탐지당 하나이며 동일 증거로 재요청하면 기존 초안을 반환합니다. 다른 증거로 덮어쓰지 않습니다. 생성 후 초안·증거 연결은 불변입니다. 상태는 DRAFT → SUBMITTED/WITHDRAWN, SUBMITTED → RESOLVED/REJECTED/WITHDRAWN만 허용됩니다. 종료 상태는 다시 열지 않습니다. 상태 version으로 동시 수정 충돌을 감지합니다.

SUBMITTED는 사용자가 직접 제출했다고 기록한 상태입니다. 마켓에서 접수번호를 검증하거나 처리 결과를 자동 조회하지 않습니다. 제출 기록 시 탐지가 여전히 CONFIRMED인지 다시 확인합니다. 접수번호는 제출 기록 시에만 입력하고 이후 유지합니다. DB 마이그레이션 V19는 초안 불변성·상태 전이와 운영 감사 이벤트를 적용합니다.

## AliExpress 안내

AliExpress 증거에는 [Alibaba IPP](https://ipp.alibabagroup.com/) 링크를 제공합니다. [공식 안내](https://ipp.alibabagroup.com/instruction/en.htm) 및 [FAQ](https://ipp.alibabagroup.com/faq/en.htm)를 2026-09-10 확인했습니다. 지역별 별도 플랫폼은 해당 마켓 안내를 확인해야 합니다. 기타 마켓에는 검증되지 않은 링크를 추정해 제공하지 않고 null을 반환합니다. 링크는 신고 완료를 뜻하지 않습니다.

증거의 SVG는 보존 데이터로 만든 요약 화면이며 마켓 페이지를 브라우저로 캡처한 화면이 아닙니다. 유사도는 법적 판단이나 침해 확률로 표시하지 않습니다. 실제 제출 전 초안과 마켓 요구사항을 사용자가 검토합니다.

## 검증 범위

서비스·인증/요청 검증을 포함한 전체 백엔드 단위 테스트 229개가 통과했고 bootJar 생성에 성공했습니다. V19 PostgreSQL 트리거 통합 테스트도 실행했으나 Docker 환경을 찾지 못해 초기화 단계에서 실패했습니다. 따라서 실제 DB 마이그레이션·트리거 동작은 아직 검증되지 않았습니다. 실제 마켓 제출은 수행하지 않았습니다.

다음 개발 항목: STEP 24 판매자 분석. 실제 AI 검토 데이터 확보·임계값 보정과 운영 환경 검증은 별도 미완료입니다.
