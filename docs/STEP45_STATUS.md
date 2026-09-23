# STEP 4·5 검증 결과

대상 프로젝트: C:\copyright\_detect\_project

## STEP 4 — 실제 AI 서비스

- FastAPI POST /v1/embeddings 구현
- 고정 DINOv2 base safetensors 모델 다운로드 및 CPU 로딩 성공
- 실제 이미지에서 768차원 유한 벡터 생성 확인
- L2 norm 1 및 같은 이미지의 반복 결과 일치 확인
- 로컬 HTTP 이미지 서버 → URL 검증/다운로드 → 실제 모델 → API 응답 테스트 성공
- API·이미지·주소 검증 기본 테스트 37개 성공
- 실제 모델 테스트 1개 성공
- 최종 AI 테스트 전체 38개 성공, 실패 0
- 모델/소스는 프로젝트에 배치하며 가상환경 설치는 ai-service/scripts/setup.ps1로 재현 가능

모델 리비전: f9e44c814b77203eaa57a6bdbbd535f21ede1415
전처리: rgb-white-bicubic256-center224-imagenet-cls-l2-v1
검증 장치: CPU (GPU 미검증)

테스트 프레임워크의 httpx/Starlette/AnyIO 관련 deprecated 경고 2개가 있으나 테스트 실패는 없었습니다.
평가 데이터셋으로 precision/recall을 측정한 것은 아니며 실제 카피상품 탐지 정확도는 아직 평가하지 않았습니다.

## STEP 5 — 비동기 임베딩 저장

- 작품 등록 트랜잭션과 embedding_job/embedding_outbox 생성 연결
- 본인 작품의 임베딩 상태 조회 및 실패 작업 재요청 API
- 모델 리비전·전처리·768차원·유한 값·단위 벡터 검증
- Kafka Outbox 발행, 제한된 재시도, DLT 처리
- 중복 이벤트 및 이전 요청 세대 무시
- Flyway V4: 작업·Outbox·artwork_embedding vector(768) 테이블
- 최종 백엔드 테스트 58개 성공, 실패 0
- 최종 Java 컴파일 및 bootJar 성공

## 미검증 항목

Docker Linux 엔진을 찾지 못해 Testcontainers 통합 테스트 4개 클래스가 초기화 단계에서 실패했습니다.
기존 인프라·인증·작품 및 새 임베딩 통합 테스트 모두 실제 컨테이너 생성 전에 실패했습니다.
따라서 Flyway V4의 실제 PostgreSQL 적용, Kafka 전송/DLT, 실제 pgvector 저장을 성공으로 표시하지 않았습니다.

EmbeddingIntegrationTest는 PostgreSQL/Redis/Kafka와 로컬 AI 응답 스텁으로 전체 저장 경로와 중복 방지를 확인하도록 작성하고 컴파일했습니다.
실제 DINOv2 자체는 앞서 별도의 모델 테스트로 확인했습니다. 두 테스트를 하나의 실제 전체 환경으로 연결한 검증은 남아 있습니다.
Dockerfile 이미지 빌드 및 Docker 기반 전체 서비스 실행도 미검증입니다.

Docker Desktop Linux 엔진을 실행한 뒤 backend에서 다음을 실행하세요.

```powershell
.\gradlew.bat check bootJar
```

실행 안내: ai-service/README.md, docs/STEP4_AI.md, docs/STEP5_EMBEDDING.md.
다음 개발 단계는 STEP 6 Marketplace 도메인 및 Mock Marketplace입니다.
