# STEP 8 검증 상태

대상: C:\copyright\_detect\_project

- 구현: 소유자 탐지 실행, 동일 모델·전처리 벡터의 pgvector cosine 비교, 상품별 최고 점수 선택, 임계값 분류, 결과 upsert, 실행 기록.
- Flyway V7: detection_run, detection.
- 백엔드 단위/MVC 테스트: 103개 통과, 실패 0.
- bootJar: 성공.
- DetectionIntegrationTest: 컴파일 성공. Docker 환경 초기화 실패(Could not find a valid Docker environment).
- 실제 PostgreSQL에서 V7 적용·cosine SQL·유사 상품 Detection 생성까지는 아직 검증하지 못했습니다.
- Docker Linux 엔진 시작 후 .\gradlew.bat integrationTest --tests '*DetectionIntegrationTest' --no-daemon 실행 필요.
- 이번 테스트의 벡터는 수학적 기대값을 가진 테스트 벡터이며 실제 데이터 탐지 정확도 평가가 아닙니다.
- 현재 기준값은 초기값이고, 실제 데이터 기반 평가 및 보정은 후속 단계입니다.
- 다음: STEP 9 탐지 목록·상세·검토 상태 API.
