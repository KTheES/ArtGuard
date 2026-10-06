# STEP 31 Docker 복구·백엔드 통합 검증

검증일: 2026-09-29. **Docker 복구 및 백엔드 전체 검증 통과**.

이 문서는 [STEP 30 로컬 실행 검증](STEP30_LOCAL_VALIDATION.md) 당시의 Docker 차단 이후 수행한 복구와 재검증 결과다. STEP 30 기록은 당시 관측으로 유지하며, 이번 결과가 로컬 MVP 전체 수용 시험이나 공개 출시 승인을 의미하지는 않는다.

## Docker 복구

Docker Desktop 시작을 막던 런타임 소켓이 Windows에서 접근 불가능한 reparse point로 남아 있었다. 최초 `dockerInference`를 정리한 뒤 다른 stale socket에서도 같은 문제가 확인되어, Docker 프로세스를 종료하고 런타임 전용 `C:\Users\espls\AppData\Local\Docker\run` 폴더를 `run.codex-backup-20260929-150215`로 이동해 보존했다. Docker가 새 `run` 폴더를 생성한 후 다음을 확인했다.

- `docker desktop status`: `running`
- Docker Client/Server 28.3.2 응답
- Docker Desktop 4.44.3 Linux engine 응답
- 이미지·볼륨·컨테이너 데이터 초기화나 WSL 배포 삭제는 수행하지 않음

## 수정 사항

- 알림 비활성 시 선택 기능인 SMTP Health를 함께 비활성화해 전체 Health 오탐을 방지했다.
- 과거 탐지의 `currentEvidence` 판정에 모델·모델 버전·전처리 버전 일치를 추가했다.
- 9개 Spring 통합 테스트 클래스가 종료 후 컨텍스트를 정리하도록 설정했다.
- MinIO 이미지를 동일 릴리스의 `quay.io/minio/minio` 경로로 변경했다.
- 작품·상품 임베딩 HTTP fixture를 현재 perceptual hash와 영역 임베딩 계약에 맞췄다.
- 상품 3개에서 고정 5영역씩 총 15개 영역 벡터가 저장되는지 검증을 추가했다.
- 로컬 준비도 검사가 Windows `PATH`의 잘못된 문자 그대로의 `$env:PATH` 항목 때문에 Docker를 오판하지 않도록 자식 프로세스 환경을 정리했다.

## 실행 결과

실제 프로젝트 `backend`에서 다음 명령을 실행했다.

```powershell
.\gradlew.bat test integrationTest bootJar --offline --no-daemon
```

| 대상 | 결과 |
| --- | --- |
| 단위 테스트 | 317개 통과, 실패 0, 오류 0, 건너뜀 0 |
| 통합 테스트 | 21개 통과, 실패 0, 오류 0, 건너뜀 0 |
| 통합 테스트 클래스 | 18개 |
| 실행 JAR | `backend/build/libs/artworkguard-0.0.1-SNAPSHOT.jar` 생성 성공 |
| Gradle 빌드 | `BUILD SUCCESSFUL` |

통합 테스트는 Testcontainers의 PostgreSQL/pgvector, Redis, Kafka, MinIO와 필요한 테스트 대역을 사용한다. DB 마이그레이션, 인증, 작품 저장, 임베딩 작업, 상품 수집, 탐지·큐, 이메일 인증, 감사, 악용 제한, 소유권·원본, 판매자·소셜·신고 관련 통합 시나리오가 환경 실패나 skip 없이 실행됐다.

## 남은 검증

- [MVP 수용 점검표](MVP_ACCEPTANCE.md)의 M12만 이번 결과로 충족했다. 화면을 통한 M01~M11 연결 시험은 아직 실행하지 않았다.
- 실제 DINOv2, 로컬 저장소, 백엔드, AI, 웹, 로컬 메일을 한 흐름으로 연결한 수용 시험이 남아 있다.
- 실제 AliExpress 계정/API, 운영 SMTP·S3·Stripe, 실제 데이터 정확도·성능·보안·복구·삭제 및 정책 승인은 별도 미완료다.
- 테스트의 AI HTTP 응답과 일부 저장소는 대역이므로, 이 결과를 실제 마켓 정확도나 운영 인프라 성공으로 해석하지 않는다.
