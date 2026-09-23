# STEP 18 Subscription

FREE, CREATOR, PRO, BUSINESS 플랜과 사용량·기능 권한을 서버 정책으로 관리합니다. 결제 상태와 외부 결제 제공자 연동은 STEP 19 범위입니다.

## 플랜 정책

| 플랜 | 작품 | 자동 스캔 | 탐지 결과 | 우선순위 | 이메일 | 증거 | 고급 탐지 | 보고서 |
|---|---:|---:|---:|---:|---|---|---|---|
| FREE | 3 | 7일 | 25 | 0 | - | - | - | - |
| CREATOR | 50 | 24시간 | 100 | 0 | 지원 | 지원 | - | - |
| PRO | 500 | 6시간 | 250 | 1 | 지원 | 지원 | 지원 | 지원 |
| BUSINESS | 5,000 | 1시간 | 500 | 2 | 지원 | 지원 | 지원 | 지원 |

PRO의 작품 수는 무제한 대신 운영 가능한 높은 한도 500개로 정했습니다. BUSINESS는 5,000개입니다. 한도는 `subscription_plan`의 명시적 값이며 추후 상품 정책 변경 시 migration으로 관리합니다.

## API

- `GET /api/v1/subscription/plans`: 공개 플랜 카탈로그
- `GET /api/v1/subscription`: 로그인 사용자의 플랜, 활성 작품 수, 남은 작품 수

신규 회원은 데이터베이스 트리거를 통해 FREE 구독을 원자적으로 받습니다. 기존 회원도 V16 적용 시 FREE로 채워집니다.

## 적용 지점

- 작품 생성 트랜잭션은 사용자 행을 잠근 뒤 활성 작품 수를 검사하여 동시 요청으로 한도를 넘지 못하게 합니다.
- 수동 탐지의 요청 결과 수는 현재 플랜 상한으로 제한됩니다.
- FREE와 CREATOR는 전체 이미지 DINO 유사도만 사용하고, PRO와 BUSINESS는 영역 벡터와 pHash/dHash 앙상블까지 사용합니다.
- 자동 모니터링과 상품 임베딩 신호는 최근 자동 작업 시각과 플랜 스캔 주기를 함께 확인합니다.
- PRO와 BUSINESS 작업은 detection outbox에서 우선 발행됩니다.
- 이메일 알림은 CREATOR 이상에서만 큐에 들어가며, 증거 API는 CREATOR 이상에서만 열립니다. 증거 원본은 향후 플랜 변경과 법적 보존을 위해 모든 탐지에서 계속 생성됩니다.

## 검증

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test
.\gradlew.bat integrationTest # Docker 필요
.\gradlew.bat bootJar
```

통합 테스트는 V16 플랜 데이터와 신규 회원의 FREE 자동 가입을 확인합니다.
