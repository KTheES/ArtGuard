# STEP 19 Billing

Stripe Checkout으로 유료 구독을 시작하고 서명된 웹훅으로 구독 상태를 동기화합니다. 기본값은 비활성입니다.

## API

- `POST /api/v1/billing/checkout`: 로그인 사용자용 Checkout 세션 생성
  - `Idempotency-Key` 헤더 필수, 8~100자의 영문·숫자·콜론·밑줄·하이픈
  - 본문: `{"planCode":"CREATOR|PRO|BUSINESS"}`
- `POST /api/v1/billing/webhooks/stripe`: Stripe 공개 웹훅 수신 경로
- `GET /api/v1/subscription`: 결제 상태, 구매 플랜, 현재 유효 권한, 결제 주기 종료와 기간 말 취소 여부 조회

## 상태와 권한

Stripe 상태는 다음 내부 상태로 정규화합니다.

| 내부 상태 | Stripe 상태 | 유료 권한 |
|---|---|---|
| TRIAL | `trialing` | 유지 |
| ACTIVE | `active` | 유지 |
| PAST_DUE | `past_due`, `incomplete` | FREE로 제한 |
| CANCELED | `canceled`, `unpaid`, `incomplete_expired`, `paused` | FREE로 제한 |

구매한 플랜 코드는 보존하고, 연체·취소 중 실제 작품 한도·탐지·모니터링·알림·증거 권한만 FREE 정책으로 계산합니다.

## 멱등성과 웹훅 보안

- Checkout 요청은 사용자와 `Idempotency-Key` 조합으로 한 번만 생성됩니다. Stripe API에도 같은 안정 키를 전달합니다.
- 웹훅은 변환 전 원문 본문과 `Stripe-Signature`를 공식 Stripe Java SDK로 검증하며 허용 시차는 5분입니다.
- `(provider, external_event_id)` 기본키로 같은 이벤트를 한 번만 처리하고, 같은 ID의 본문 해시가 바뀌면 거부합니다.
- `provider_event_created_at`보다 오래된 구독 이벤트는 권한을 되돌리지 않습니다.
- Checkout에서 사용자·플랜 metadata를 세션과 구독에 함께 기록하며, 기존 customer/subscription ID와 다른 이벤트는 거부합니다.

## 설정

```text
BILLING_ENABLED=false
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=
STRIPE_CREATOR_PRICE_ID=
STRIPE_PRO_PRICE_ID=
STRIPE_BUSINESS_PRICE_ID=
BILLING_SUCCESS_URL=http://127.0.0.1:3000/billing/success
BILLING_CANCEL_URL=http://127.0.0.1:3000/billing/cancel
```

Stripe Workbench에서 구독 생성·변경·삭제 이벤트를 `POST /api/v1/billing/webhooks/stripe`로 전송하도록 등록합니다. 키와 웹훅 secret은 저장소에 커밋하지 않습니다.

공식 참고 문서: [Checkout Session 생성](https://docs.stripe.com/api/checkout/sessions/create), [구독 웹훅](https://docs.stripe.com/billing/subscriptions/webhooks), [웹훅 서명 검증](https://docs.stripe.com/webhooks/signature).

## 검증

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test
.\gradlew.bat integrationTest # Docker 필요
.\gradlew.bat bootJar
```

실제 Checkout 생성과 Stripe 운영 웹훅은 테스트 키·Price ID·Webhook secret이 있어야 확인할 수 있습니다.
