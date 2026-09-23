# STEP 20 운영 환경 강화

## 적용 내용

| 요구사항 | 구현 및 운영 경계 |
|---|---|
| Rate Limit | 기존 탐지 30회/분·수집 10회/분에 Checkout 5회/분 추가. Redis 장애 시 신규 요청 제한 |
| Circuit Breaker | Stripe와 AI 호출 5회 연속 실패 시 30초 차단, 이후 탐색 호출 한 개 허용. 인스턴스별 상태 |
| Retry / Timeout | Stripe 연결 3초·읽기 10초, SDK 내부 재시도 0회. 동일 Checkout 멱등 키로 재요청. AI·Kafka·SMTP는 기존 제한된 재시도 유지 |
| DLQ | 기존 embedding/product/detection DLT와 실패 outbox 보존. ops/queue-health.sql로 실패·대기 현황 조회 |
| Idempotency | 기존 outbox 이벤트·탐지 source key·Checkout 요청 키·웹훅 이벤트 ID 유지 |
| Audit Log | V18 트리거로 구독 플랜·상태·취소예약 및 탐지 검토 상태 변경 기록. 비밀값·본문·이메일 제외 |
| Backup | PostgreSQL custom archive 생성, 종료 코드·archive 목록·SHA-256 확인 스크립트 |
| Encryption | prod 프로파일의 HTTPS, DB verify-full, Redis TLS, Kafka SASL_SSL. 저장 볼륨·S3·백업의 저장 시 암호화는 배포 환경에서 설정 |
| Secret Manager | prod의 필수 configtree 경로로 외부 Secret Manager가 마운트한 파일 읽기. 특정 클라우드 계정 연동은 배포 시 설정 |

감사 기록의 subject_user_id는 변경 대상 회원입니다. 관리자 등 실제 행위자를 식별하는 전체 감사 기능은 후속 Audit 단계의 범위입니다. DB 트리거는 일반 UPDATE/DELETE를 거부하지만 테이블 소유자나 DB 관리자의 변조까지 막지는 못합니다. 운영 런타임 DB 계정에는 DDL/TRUNCATE 권한을 주지 않고, migration 계정과 분리해야 합니다.

## 운영 프로파일

SPRING_PROFILES_ACTIVE=prod 로 실행합니다. 로컬 Docker Compose는 TLS 운영 환경을 제공하지 않으므로 이 프로파일로 그대로 실행할 수 없습니다.

- SECRETS_DIRECTORY: 기본 /run/secrets/, 끝에 경로 구분자 필요. 디렉터리 부재 시 시작 실패
- 외부 Secret Manager/CSI/배포 에이전트가 비밀값을 읽기 전용 파일로 마운트
- 파일명은 DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET, STRIPE_SECRET_KEY 등 기존 설정 키와 일치
- TLS_KEY_STORE: API용 PKCS12 경로, TLS_KEY_STORE_PASSWORD: 비밀번호
- DB_SSL_ROOT_CERT: PostgreSQL CA 인증서, DB_URL: 인증서 호스트명과 일치하는 주소
- KAFKA_SASL_JAAS_CONFIG: Kafka SCRAM-SHA-512 인증 설정
- S3는 관리형 HTTPS endpoint와 버킷 기본 SSE-KMS·versioning, 최소 권한 IAM을 사용
- AI 서비스도 운영용 HTTPS 주소와 인증 키를 사용
- 운영 OpenAPI 비활성, 종료 유예 30초, DB 풀 20개·연결 대기 5초·잠금 대기 5초

암호화된 볼륨·버킷과 Secret Manager 리소스는 이번 로컬 작업에서 생성하지 않았습니다. 공급자 계정, 리전, 인증서, 배포 대상이 정해진 뒤 실제 연결 검증이 필요합니다.

## 백업과 복구 훈련

PostgreSQL 17 호환 pg_dump/pg_restore를 설치하고 PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSFILE, PGSSLROOTCERT, PGSSLMODE=verify-full을 설정합니다. 비밀번호를 명령 인수에 넣지 않습니다. 출력 경로는 접근 제한된 암호화 볼륨을 사용합니다.

~~~powershell
./ops/backup-database.ps1 -OutputDirectory D:/ArtworkGuardBackups
./ops/verify-backup.ps1 -Archive D:/ArtworkGuardBackups/<archive>.dump
~~~

스크립트는 기존 파일을 삭제하지 않으며 실패한 아카이브는 조사용으로 남깁니다. 검증 통과는 실제 복구 성공을 뜻하지 않습니다.

복구 훈련은 격리된 PostgreSQL 17 + pgvector 인스턴스의 비어 있는 새 DB에서 실행합니다.

~~~powershell
pg_restore --exit-on-error --single-transaction --no-owner --no-acl --dbname=artworkguard_restore_drill <archive>.dump
~~~

복구 후 Flyway 이력, 작품·탐지·증거·감사 테이블 건수와 이미지 SHA를 대조합니다. 운영 DB에 --clean이나 --create 옵션으로 덮어쓰지 않습니다. DB 백업만으로 S3 이미지가 복원되지 않으므로 S3 versioning/복제와 함께 복구 시점을 맞춥니다. Redis 토큰 복원 시 보안 영향을 검토하고, Kafka offset/outbox/DLT 재처리는 중복 방지 키를 유지합니다.

일일 백업·주간 격리 복구 훈련을 운영 기준으로 제안합니다. 스케줄과 보존 기간은 아직 자동 등록하지 않았습니다. 실제 훈련 시간으로 RPO/RTO를 측정해야 합니다.

## 장애 대응

ops/queue-health.sql로 실패 outbox와 가장 오래된 대기를 확인합니다. Kafka DLT payload에는 이미지 URL 등 민감 정보가 포함될 수 있으므로 읽기 권한을 제한합니다. 원인을 해결한 뒤 기존 소유자 재시도 API로 작업 generation을 증가시키고, DLT 메시지의 무분별한 재발행은 피합니다.

회로 차단기는 애플리케이션 재시작 시 초기화됩니다. 열린 회로는 기존 worker의 제한된 재시도/실패 처리로 전달됩니다. Stripe 재시도에는 반드시 원래 Idempotency-Key를 재사용합니다.

## 검증과 남은 환경 작업

단위 테스트는 회로 차단·탐색 호출 경쟁·회복, Checkout 제한과 입력 검증을 확인합니다. PostgreSQL 통합 테스트에는 감사 기록과 변경 거부 시나리오를 추가했습니다.
실제 인증서·Secret Manager·클라우드 저장 암호화·백업 복구 훈련은 운영 인프라에서 별도로 검증해야 합니다.

2026-09-10 검증 결과: 백엔드 단위 테스트 212개 및 bootJar 통과. PowerShell 구문 검사와 모의 PostgreSQL 도구를 사용한 정상 manifest 생성·해시 변조 거부·dump 실패 거부 통과. 통합 테스트 10개 클래스는 Docker 엔진 초기화 단계에서 실패해 V18 SQL 실행은 미검증입니다.

공식 근거: [Spring Boot configtree](https://docs.spring.io/spring-boot/reference/features/external-config.html), [PostgreSQL 17 복원](https://www.postgresql.org/docs/17/app-pgrestore.html).
