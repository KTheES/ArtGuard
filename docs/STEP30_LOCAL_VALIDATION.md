# STEP 30 로컬 실행 검증

검증일: 2026-09-15. **개별 테스트 통과, 전체 연결 검증은 BLOCKED**.

## 실행 결과

| 대상 | 결과 | 범위 |
| --- | --- | --- |
| 백엔드 `test bootJar --offline --no-daemon` | 317 tests, 실패/오류 0; JAR 생성 성공 | 현재 소스의 단위 테스트와 패키징 |
| AI 기본 pytest | 70 passed, 3 deselected | 모델 테스트는 별도 실행 |
| AI `pytest -m model -o addopts=` | 3 passed, 70 deselected | 캐시된 실제 DINOv2 모델과 로컬 HTTP 입력·합성 이미지 검증 |
| 웹 `node --test` | 6 passed | 인증 갱신·오류·URL·로컬 프록시 제한 |
| 로컬 준비도 검사 | BLOCKED | Docker, Redis, Kafka, 저장소, API, AI 상시 서비스 준비 안 됨 |

합계 396개 테스트가 통과했습니다. AI 테스트의 Starlette/httpx 및 anyio deprecation 경고 2개는 남아 있습니다. 이번 실행에서 의존성을 변경하지 않았습니다. 실제 모델 테스트 통과는 실물 마켓 정확도 검증이나 백엔드→Kafka→AI 전체 흐름 성공을 의미하지 않습니다.

작업 공간의 격리된 프로젝트 사본에서 실행했습니다. 원본 프로젝트의 운영 데이터나 DB 스키마를 변경하지 않았습니다. PostgreSQL 기본 포트 5432는 응답했으나 프로젝트 DB인지, pgvector·계정·마이그레이션이 준비됐는지는 확인되지 않았으므로 사용하지 않았습니다.

## 전체 연결을 막는 원인

Docker CLI는 설치되어 있으나 Linux 엔진 파이프가 없습니다. Docker Desktop 시작도 시도했으며 백엔드 로그에서 다음 오류로 종료됨을 확인했습니다.

`initializing Inference manager ... Docker/run/dockerInference ... The file cannot be accessed by the system`

해당 런타임 경로는 reparse point로 표시됩니다. 단순 프로젝트 설정 오류로 확정하지 않습니다. Docker 데이터 초기화·WSL 배포 삭제·버킷 삭제는 수행하지 않았습니다. Docker Desktop 실행 환경 복구가 필요하며, 복구 후 `docker version`에 Server 정보가 표시되어야 다음 검증을 진행할 수 있습니다.

## 반복 가능한 준비도 검사

프로젝트 루트에서:

```powershell
node ops/check-local-readiness.mjs
```

Node.js 22 이상을 사용합니다. Docker Server와 기본 로컬 포트, 백엔드 및 AI health를 읽기 전용으로 확인하고 JSON을 출력합니다. 환경 변수나 인증값을 출력하지 않습니다. 포트만 열려 있으면 TCP_ONLY로 기록하며 DB 인증이나 데이터 정합성을 검증한 것으로 취급하지 않습니다. 기본 포트를 변경한 환경은 스크립트의 검사 대상을 조정해야 합니다.

모든 사전 조건이 응답해도 `integrationPassed`는 false입니다. 전체 시험 결과를 이 사전 검사로 대신하지 않습니다.

## 재개 순서

1. Docker 실행 환경을 복구하고 Server 응답 확인.
2. 기존 5432 사용 프로세스·데이터 소유 확인 후, 포트 충돌이 없는 격리 서비스 구성. 임의로 기존 프로세스를 종료하거나 기존 DB에 연결하지 않음.
3. [기본 실행](../README.md), [저장소](STEP3_ARTWORK.md), [AI](../ai-service/README.md)에 따라 로컬 서비스 기동. 브라우저 업로드를 위한 버킷 CORS도 확인.
4. backend `integrationTest` 실행으로 전체 Flyway 스키마·DB·Redis·Kafka 테스트 확인.
5. [MVP 수용 시험](MVP_ACCEPTANCE.md)의 업로드→임베딩→상품 비교→탐지 화면→로컬 메일을 실제로 실행하고 증거 기록.

이번 단계에서는 수용 시험의 NOT_RUN을 PASS로 변경하지 않았습니다. 실제 AliExpress·SMTP·운영 저장소·정책 승인도 완료되지 않았습니다.
