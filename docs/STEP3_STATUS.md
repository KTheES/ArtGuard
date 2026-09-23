# STEP 3 검증 결과

대상: C:\copyright\_detect\_project

## 완료

- Artwork CRUD, 사용자별 접근 권한, 논리 삭제
- Flyway V3 작품·업로드 요청 테이블 및 인덱스
- S3 Presigned PUT 10분, GET 60초 및 Content-Type/Content-Length 서명 헤더
- 실제 길이·MIME·매직 바이트·디코딩·픽셀 제한 검사
- 검증 후 PNG 정규화 및 워터마크 미리보기
- 임시 업로드와 최종 저장소 키 분리
- 업로드 재시도 처리, version을 이용한 수정 충돌 검사
- 로컬 MinIO Compose 및 선택적 로컬 버킷 초기화
- 최종 Java·통합 테스트 코드 컴파일 및 bootJar 성공
- 단위/MVC/서명 테스트 46개 성공, 실패 0, 오류 0
- MinIO 포함 docker compose config --quiet 통과

테스트 구성: 기존 인증·공통 22개, 작품 서비스 9개, 이미지 검증 5개, 작품 MVC 6개, S3 서명·저장소 4개.
S3 서명 테스트는 실제 SDK presigner를 사용하되 외부 저장소 연결은 사용하지 않습니다.

## 실행하지 못한 검증

integrationTest는 Docker Linux 엔진을 찾지 못해 초기화에서 실패했습니다.
기존 인프라·인증 및 새 작품 통합 테스트 모두 컨테이너 생성 전에 실패했습니다.
실제 PostgreSQL 마이그레이션, MinIO PUT/GET, 전체 등록 흐름, Health UP은 아직 확인하지 못했습니다.
통합 테스트는 이를 성공이나 skip으로 처리하지 않습니다.

Docker Desktop Linux 엔진을 실행한 뒤 backend에서 다음을 실행해야 합니다.

```powershell
.\gradlew.bat check bootJar
```

새 통합 테스트는 직접 업로드 → 등록 → 중복 재시도 → 사용자별 목록/조회/수정/삭제 제한 → 비공개 이미지 URL → 수정 충돌 → 논리 삭제를 확인하도록 작성했습니다.

## 구현 범위의 제한

- PNG/JPEG만 입력받고 정규화 PNG로 저장합니다. WebP·EXIF 방향 보정은 미구현입니다.
- 논리 삭제 후 DB/저장소 데이터는 남습니다. 기존 GET URL은 최대 60초 유효합니다.
- 저장소 영구 삭제·고아 객체 정리, 실제 자동 모니터링, AI 임베딩은 후속 작업입니다.
- 실제 AWS 리소스를 생성하거나 기존 사용자 DB에 마이그레이션을 실행하지 않았습니다.

실행 설정과 API 예시는 STEP3_ARTWORK.md를 참고하세요.
