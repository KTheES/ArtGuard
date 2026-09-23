# STEP 28 MVP 범위와 구현 차이

점검일: 2026-09-15. 결과: **MVP 구현·검증 진행 중**. 화면과 전체 흐름 검증이 남아 있으므로 MVP 성공 또는 상용 출시 완료로 표시하지 않습니다.

## 범위 기준

[원본 계획서](ArtworkGuard_Codex_Development_Plan.md)의 상세 STEP 28 및 29번 MVP 성공 시나리오를 기준으로 삼습니다. 말미 개발 순서 요약의 `STEP 28+ Scale / Enterprise`와 상세 단계가 다릅니다. 이번 작업은 상세 단계의 MVP 점검이며 확장 시스템 도입은 포함하지 않습니다.

첫 로컬 MVP는 Mock Marketplace로 작품 등록 → 임베딩 → 상품 비교 → 탐지 화면 → 상품 URL 확인을 입증합니다. AliExpress는 사용자가 선택한 첫 실제 마켓으로 연동 준비를 유지하되, 실제 계정 권한·호출 검증을 별도 통과해야 합니다. Mock 성공을 AliExpress 검색 성공으로 기록하지 않습니다.

## 기능별 현황

| MVP 항목 | 구현 근거 | 남은 확인 |
| --- | --- | --- |
| 회원가입·로그인 | [인증](STEP2_AUTH.md), AuthController | 브라우저 로그인·만료·로그아웃 흐름 |
| 작품 등록·S3 | [작품](STEP3_ARTWORK.md), ArtworkController | 실제 업로드·이미지 조회 및 실패 복구 |
| AI Embedding | [AI 서비스](../ai-service/README.md), [연결](STEP5_EMBEDDING.md) | 실제 모델 로딩·저장소 접근·작업 완료 |
| Mock Marketplace·Product | [수집](STEP6_MARKETPLACE.md), [상품 임베딩](STEP7_PRODUCT_EMBEDDING.md) | 권리 확보 fixture 상품으로 전체 흐름 실행 |
| pgvector·Similarity Search | [탐지](STEP8_DETECTION.md), [비동기 작업](STEP10_KAFKA.md) | 실제 DB 마이그레이션·벡터 검색·Kafka 완료 |
| Detection·검토 | [검토 API](STEP9_DETECTION_API.md), DetectionReviewController | 사용자 격리·상태 충돌을 연결 환경에서 확인 |
| Detection UI | frontend에는 README만 존재 | 로그인·작품·탐지 목록/상세 화면 구현 |
| Email Alert | [메일 큐/작업자](STEP16_NOTIFICATION.md) | 로컬 메일 수신함으로 실제 전달 및 중복 확인 |

계획서 알림 장의 Web Notification은 별도 알림함 요구로 남습니다. 탐지 목록을 읽음 상태·알림함 구현으로 간주하지 않습니다. 첫 화면은 탐지 목록을 제공하고 별도 알림함은 후속 구현으로 추적합니다.

## 검증 상태

- 직전 STEP 26 기록: 백엔드 단위 테스트 317개 및 bootJar 통과. 이 단계에서 재실행한 결과는 아닙니다.
- DB·Redis·Kafka 통합 테스트 실행을 통한 현재 전체 스키마와 연결 검증은 미완료입니다. Docker 가용 상태를 실행 전에 다시 확인해야 합니다.
- 실제 AI 평가 데이터와 운영 임계값 보정은 미완료입니다. [평가](STEP21_AI_EVALUATION.md)·[임계값](STEP22_THRESHOLD_TUNING.md) 도구와 합성 fixture가 있다는 사실은 시장 정확도 증거가 아닙니다.
- 실제 AliExpress 계정, SMTP, 운영 S3, Stripe 검증은 별도 작업입니다. 로컬 대체 서비스의 결과와 분리합니다.
- 정책은 [STEP 27](STEP27_LEGAL_READINESS.md)의 내부 초안 상태입니다. 물리 삭제·보존 정책·공개 승인 등이 남아 있습니다.

## 다음 구현 순서와 완료 조건

| 순서 | 작업 | 완료 증거 |
| --- | --- | --- |
| 1 | MVP 웹 화면 | 실제 API 기반 로그인, 작품 업로드/목록, 작업 진행, 탐지 목록/상세·상품 링크·검토 상태; 로딩/빈 결과/실패/401/409 표시 |
| 2 | 로컬 서비스 연결 | PostgreSQL·Redis·Kafka·MinIO·AI와 로컬 메일 수신함의 기동 기록 및 통합 테스트 결과 |
| 3 | MVP 수용 시나리오 | [수용 점검표](MVP_ACCEPTANCE.md)의 각 항목을 실행 증거와 함께 통과 |
| 4 | AliExpress 파일럿 | 계정 허용 범위 확인, 제한된 실제 상품 수집·이미지 저장·탐지 검증 |
| 5 | 공개 베타 준비 | 실제 데이터 정확도, 보안·복구·삭제 검증과 정책 승인 |

다음 개발 작업은 1번 MVP 웹 화면입니다. 기존 API DTO와 오류 형식에 맞추고 업로드 서명 URL·비밀값을 화면 로그에 남기지 않습니다. 미구현 기능을 버튼만 있는 완료 화면으로 표시하지 않습니다.

## MVP에서 제외하는 범위

여러 마켓 동시 확장, 자동 신고, Seller Risk AI, 모바일 앱, 고급 결제, Enterprise 기능은 원본 MVP 제외 목록을 유지합니다. 이미 있는 구독·결제·판매자 집계·수동 신고 API는 보존하되 첫 MVP 성공의 필수 조건으로 추가하지 않습니다. 이메일 알림 검증에서는 해당 플랜 권한이 있는 테스트 계정이 필요합니다.

이번 단계는 문서와 README 정리이며 런타임 코드·설정·의존성은 변경하지 않습니다.
