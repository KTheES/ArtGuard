# STEP 11 검증 상태

프로젝트: C:\copyright\_detect\_project

- Redis 요청 제한: 수집 사용자별 10회/60초, 수동 탐지 등록·재시도 합산 30회/60초.
- 30초 검색 결과 캐시, 60초 상품 내용 중복 표시, 120초 토큰 기반 마켓 잠금.
- DB commit 후 캐시 기록, 실패 시 캐시 오염 방지, 429/503 Retry-After 응답.
- 단위/MVC 테스트 144개 통과, 실패 0.
- bootJar 성공.
- RedisGuardIntegrationTest 컴파일 성공. Docker 초기화 실패(Could not find a valid Docker environment).
- 실제 Redis에서 Lua 원자성·TTL·잠금 소유권 보호는 아직 검증하지 못했습니다.
- Docker Linux 엔진 시작 후 backend에서 .\gradlew.bat integrationTest --tests '*RedisGuardIntegrationTest' --no-daemon 실행 필요.
- 현재 수집 대상은 Mock이며 실제 외부 crawling은 STEP 12 범위입니다.
- 긴 프로세스 정지에 대한 fencing/주기적 잠금 갱신은 미구현이며, DB 고유 제약이 최종 중복 저장 방어입니다.
- 다음: STEP 12 실제 마켓 공식 API 연동.
