# STEP 33 로컬 MVP 수용 시험 결과

검증일: 2026-09-29. 결과: **M01~M12 PASS**.

## 범위와 환경

이번 시험은 운영 데이터와 분리한 `artworkguard-acceptance` Docker Compose 환경에서 수행했다. PostgreSQL, Redis, Kafka, MinIO, Mailpit, Spring Boot 백엔드, MVP 웹과 실제 DINOv2 AI 서비스를 연결했다. 테스트 비밀값과 토큰은 보고서에 남기지 않았다.

| 구성 요소 | 시험 주소/버전 |
| --- | --- |
| 웹 | `127.0.0.1:15173` |
| 백엔드 | `127.0.0.1:18080` |
| AI | `127.0.0.1:18001` |
| PostgreSQL / Redis / Kafka | `15432` / `16379` / `19092` |
| MinIO / Mailpit | `19000` / `18025` |
| AI 모델 | `facebook/dinov2-base` |
| 모델 revision | `f9e44c814b77203eaa57a6bdbbd535f21ede1415` |
| 전처리 | `rgb-white-bicubic256-center224-imagenet-cls-l2-v1` |

격리 환경 정의는 `infra/docker/compose.acceptance.yml`에 있다. 해당 파일에는 공유 가능한 로컬 기본값만 두고, 실행 중 사용한 비밀값은 환경 변수로만 전달했다.

재현할 때는 네 가지 환경 변수에 새 로컬 전용 값을 지정한 뒤 Compose를 실행한다. 값은 저장소에 커밋하지 않는다.

```powershell
$env:ACCEPTANCE_DB_PASSWORD = '<local-only-password>'
$env:ACCEPTANCE_REDIS_PASSWORD = '<local-only-password>'
$env:ACCEPTANCE_S3_ACCESS_KEY = '<local-only-access-key>'
$env:ACCEPTANCE_S3_SECRET_KEY = '<local-only-secret-key>'
docker compose -f infra/docker/compose.acceptance.yml up -d
docker compose -f infra/docker/compose.acceptance.yml ps
```

## Fixture

프로젝트의 Mock Marketplace PNG를 사용했다. 법적 권리나 실제 마켓 성능을 입증하는 데이터가 아니라 연결 동작을 재현하기 위한 합성 fixture다.

| 파일 | SHA-256 | 역할 |
| --- | --- | --- |
| `moon-cat-poster.png` | `f850127435a66dc61e6e6d0655ea5b8b9ed51cf586ca63e73dfe75e9329d100a` | 등록 작품과 동일한 양성 |
| `moon-cat-shirt.png` | `36fb0935b5b6144c8a20872dbed53bfcd860e5377c963d972d17204d7b50ea14` | 변형 양성 및 장애 복구 작품 |
| `forest-mug.png` | `71111483bcf083af94d63bd83884e831080d2d2ada225d487eeb03b441d6bab1` | 무관 음성 |

## 결과

| ID | 결과 | 실제 관측 |
| --- | --- | --- |
| M01 | PASS | 브라우저에서 창작자 로그인 후 작품 목록 표시. 잘못된 암호는 로그인 화면에 남고 `INVALID_CREDENTIALS` 안내 표시. API도 401 반환. |
| M02 | PASS | MinIO 서명 URL로 PNG PUT 200. 작품 `860cd56e-3b6d-4a19-aca8-25b60e4496a2`가 512×512로 등록되어 목록에 표시됐고 서명 GET은 200/6,451 bytes였다. |
| M03 | PASS | 작품 임베딩 작업 `477bf8d9-0d9d-4aa8-8ca0-2b74c2b8fd93`가 generation 1에서 COMPLETED. 실제 모델의 768차원 벡터가 생성됐다. |
| M04 | PASS | 일반 계정의 Mock 수집은 403. 관리자 수집은 상품 3건·이미지 3건을 만들었고 세 상품 임베딩이 모두 COMPLETED였다. |
| M05 | PASS | 탐지 작업 `c439da98-0e92-4f4c-80d0-7d56e783d45a`가 generation 1에서 COMPLETED. 동일 포스터와 변형 티셔츠 두 건이 생성됐다. |
| M06 | PASS | 브라우저 목록에 CRITICAL/CONFIRMED 100.0%, MEDIUM/NEW 75.1%가 표시됐다. 상세에서 등록 작품과 비교 상품 이미지가 모두 512×512로 로드되고 상품 URL이 표시됐다. “저작권 침해 여부를 확정하지 않습니다.” 문구도 확인했다. |
| M07 | PASS | 동일 포스터 탐지를 CONFIRMED로 변경해 version 1→2가 됐고 재조회 후 유지됐다. version 1 재사용 요청은 409 `DETECTION_CONFLICT`였다. |
| M08 | PASS | 다른 일반 계정으로 원 계정 작품과 탐지 상세를 요청하면 각각 404 `ARTWORK_NOT_FOUND`, `DETECTION_NOT_FOUND`로 내부 존재를 숨겼다. |
| M09 | PASS | 이메일 권한이 있는 CREATOR 테스트 계정에 `[ArtworkGuard] CRITICAL 도용 의심 상품 발견` 메일 1건이 Mailpit으로 전달됐다. 같은 작품을 두 번 탐지했지만 수신함 총계는 1건으로 유지됐다. |
| M10 | PASS | AI 서비스를 중단한 뒤 만든 작품의 임베딩이 generation 1에서 `FAILED / AI_PROCESSING_FAILED`가 됐다. AI 복구 후 같은 작업을 재요청하자 generation 2에서 COMPLETED가 됐다. |
| M11 | PASS | 무관한 `forest-mug`는 탐지 결과에 없었고 양성 두 건만 반환됐다. `status=DISMISSED` 필터는 HTTP 200, items 0, totalElements 0으로 오류와 구분됐다. |
| M12 | PASS | STEP 32 기준 단위 422개와 Docker 통합 21개, 총 443개가 실패·오류·skip 없이 통과했다. |

## 결론과 남은 범위

격리된 로컬 Mock 환경의 MVP 사용자 흐름과 실패 복구는 수용 기준을 충족했다. 이 결과는 실제 AliExpress 호출, 운영 SMTP·S3·Stripe, 실제 저작권 침해 판단, 실제 데이터 Precision/Recall, 부하·침투 시험, 법률·개인정보 승인 또는 백업·물리 삭제 검증을 대신하지 않는다.

브라우저 자동화에서는 일부 동적 버튼의 마우스 click 이벤트가 시험 도구에서 반영되지 않아 동일 버튼을 키보드 Enter로 작동시켰다. 실제 화면 전환과 API 결과는 정상 확인했으며, 이 도구 상호작용 차이는 애플리케이션 결함으로 판정하지 않았다.
