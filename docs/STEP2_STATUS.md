# STEP 2 검증 결과

구현 위치: C:\copyright\_detect\_project

## 완료

- 회원가입·로그인·JWT·Refresh Token 갱신·로그아웃·본인 조회 API 구현
- Flyway V2 회원 테이블 및 이메일 UNIQUE 제약 추가
- BCrypt 비밀번호 해시, 256비트 Refresh Token 난수, Redis 해시 저장 및 14일 TTL
- Redis Lua를 통한 원자적 토큰 교체 및 조건부 폐기
- JWT 서명·발급자·audience·만료·용도 검증
- 요청 DTO 검증, stateless 보안 설정, Swagger Bearer 인증 설정
- 최종 소스와 통합 테스트 코드 컴파일 성공
- 단위·MVC 테스트 22개 성공 (실패 0, 오류 0)
  - JWT 6개
  - 인증 서비스 7개
  - MVC 및 보안 6개
  - 기존 공통 오류 처리 3개
- 최종 bootJar 생성 성공

## 미완료 검증

`integrationTest`는 실제 Docker 환경을 찾지 못해 컨테이너 생성 단계에서 실패했습니다.
Docker Desktop 시작을 시도했으나 Docker Linux 엔진 파이프가 제공되지 않았습니다.
기존 인프라 테스트와 새 인증 통합 테스트 모두 초기화 실패이며 실제 DB/Redis 시나리오는 실행되지 않았습니다.

아래 시나리오는 테스트 코드를 작성하고 컴파일했지만 실행 성공을 확인하지 못했습니다.

- 실제 PostgreSQL에 회원 저장, 비밀번호 해시 및 중복 이메일 검증
- 회원가입 → 로그인 → 본인 조회 → 갱신 → 과거 토큰 거절 → 로그아웃
- Redis TTL, 만료, 해시 저장, 동시 갱신에서 정확히 하나의 성공
- 새로운 로그인으로 이전 Refresh Token 대체
- 정지 계정 로그인 및 본인 조회 거절
- Flyway V2의 실제 DB 적용, 실행 중 Health UP

Docker Desktop의 Linux 엔진을 실행한 뒤 backend에서 `.\gradlew.bat check bootJar`로 전체 완료 조건을 확인해야 합니다.
시스템 PATH는 변경하지 않았고 테스트 프로세스에서만 잘못된 리터럴 `$env:PATH` 항목을 제외했습니다.

로그아웃은 Refresh Token 폐기이며 Access Token은 15분 TTL(기본 clock skew 60초 허용)까지 유효합니다.
이번 변경에서 실제 사용자 계정 생성, 기존 DB 마이그레이션 실행, 외부 서비스 연결은 수행하지 않았습니다.

실행 방법과 API 예시는 STEP2_AUTH.md를 참고하세요.
