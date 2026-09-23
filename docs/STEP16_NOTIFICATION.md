# STEP 16 이메일 알림

HIGH 또는 CRITICAL 탐지가 증거와 함께 저장되면 같은 DB 트랜잭션에서 이메일 전송 작업을 생성합니다. SMTP 전송은 탐지 트랜잭션 밖의 예약 작업자가 처리합니다.

## 동작

- MEDIUM 결과에는 알림을 만들지 않습니다.
- `(detection, severity)` 조합은 한 번만 생성됩니다. 같은 HIGH 재탐지는 중복되지 않고, HIGH에서 CRITICAL로 상승하면 새 알림이 생성됩니다.
- 받는 주소는 작품 소유자의 활성 계정 이메일입니다.
- 제목과 본문은 불변 evidence 스냅샷으로 생성하며 작품, 마켓, 유사도, 상품 URL, 탐지 결과 링크를 포함합니다.
- 전송 성공 시 `SENT`, 실패 시 지수 지연 후 최대 5회 재시도하고 `FAILED`로 종료합니다.
- 작업자가 중단된 `SENDING` 항목은 2분 lease 만료 후 회수합니다.

SMTP는 본질적으로 전송 성공과 DB 기록을 하나의 원자적 트랜잭션으로 묶을 수 없습니다. SMTP 성공 직후 상태 기록 전에 프로세스가 종료되면 같은 메일이 다시 전송될 수 있는 at-least-once 방식입니다.

## 설정

기본값 `NOTIFICATION_ENABLED=false`에서는 작업을 계속 저장하지만 SMTP 작업자를 실행하지 않습니다.

```text
NOTIFICATION_ENABLED=true
NOTIFICATION_FROM=alerts@example.com
APP_URL=https://app.example.com
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=true
SMTP_STARTTLS=true
```

`APP_URL`은 HTTPS만 허용하며 로컬 개발에서는 localhost/127.0.0.1 HTTP를 허용합니다. 비밀번호를 저장소나 채팅에 넣지 말고 실행 환경 변수로 설정하세요.

## 검증

- 백엔드 단위 테스트 180개 통과
- SMTP gateway는 테스트 대역으로 발신자·수신자·제목·본문 전달을 검증
- MEDIUM 제외, HIGH 큐 생성, 성공·실패·재시도 상태 전환 검증
- 실행 JAR 생성 성공
- Docker 엔진 부재로 V14 migration과 탐지→알림 통합 시나리오는 미실행
- 실제 SMTP 계정이 없어 외부 메일 전송은 실행하지 않음
