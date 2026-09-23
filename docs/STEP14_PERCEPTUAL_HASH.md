# STEP 14 pHash/dHash 앙상블

STEP 13의 전체·영역 DINOv2 벡터에 64비트 pHash와 dHash를 추가했습니다. 해시 규격은 `phash32-dhash9-luma-v1`입니다.

## 점수 계산

각 전체·영역 후보에 대해 다음 값을 계산합니다.

- `embedding`: DINOv2 cosine 유사도
- `pHash`: `1 - HammingDistance / 64`
- `dHash`: `1 - HammingDistance / 64`
- 결합 후보: `0.8 × max(0, embedding) + 0.1 × pHash + 0.1 × dHash`
- 최종 점수: `max(embedding, 결합 후보)`

해시가 없거나 버전이 다르면 해당 항목에 임베딩 점수를 사용하므로 기존 데이터도 검색할 수 있습니다. 최종 점수는 기존 임베딩보다 낮아지지 않습니다. 상품별 전체·영역 후보 중 최종 점수가 가장 높은 하나를 저장합니다.

탐지 상세의 `scores`는 `embedding`, `pHash`, `dHash`, `ensembleVersion`, `finalScore`를 제공합니다. 현재 앙상블 버전은 `dinov2-80-phash-10-dhash-10-v1`입니다.

## 데이터 갱신

Flyway V12가 작품, 상품 전체, 상품 영역에 해시 열을 추가합니다. 완료된 임베딩 작업은 한 번 다시 큐에 넣으며, 처리 완료 트랜잭션에서 새 자동 탐지 신호를 만듭니다. AI와 저장소가 준비되지 않은 배포에서는 먼저 `EMBEDDING_ENABLED=false`로 migration만 적용하세요.

## 검증

- AI 기본 테스트 41개 통과
- 실제 DINOv2 테스트 3개 통과
- 백엔드 단위 테스트 162개 통과, 실행 JAR 생성 성공
- 128px 원본을 256px로 확대하고 JPEG quality 72로 압축한 고정 fixture:
  - pHash 해밍 거리 2/64
  - dHash 해밍 거리 1/64
- Docker 엔진 부재로 PostgreSQL/pgvector V12 통합 실행은 미검증

pHash/dHash는 색상 변화, 회전, 원근 왜곡에 대한 완전한 판별기가 아닙니다. 저장된 점수는 의심 후보 정렬용이며 저작권 침해 판정이 아닙니다.
