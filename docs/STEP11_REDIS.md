# ArtworkGuard STEP 11 — Redis 작업 보호와 수집 캐시

대상: C:\copyright\_detect\_project

## 적용 범위

개발 계획의 Rate Limiting, Search Job Cache, Product Deduplication, Distributed Lock을 현재 Mock 수집에 적용했습니다.
Search Job Cache는 현재 동기 Mock 수집 요청의 결과 캐시입니다. Kafka 탐지 작업의 상태는 계속 DB에서 읽습니다.

기존 Redis 연결 설정을 그대로 사용하며 별도 활성화 플래그는 없습니다.
인증의 refresh-token 키와 분리된 ag:v1: 네임스페이스를 사용합니다.
현재는 단일 Redis 구성이 기준이며, 마켓 캐시·잠금·상품 표시는 같은 {MOCK} 해시 태그를 사용합니다.

## 요청 제한

| 요청 | 범위 | 기본 한도 |
|---|---|---|
| 관리자 Mock 수집 | 사용자별 | 60초간 10회 |
| 수동 탐지 등록 + 실패 재시도 | 사용자별 합산 | 60초간 30회 |

첫 요청부터 60초인 고정 창이며 Redis Lua INCR/EXPIRE를 함께 실행합니다.
한도를 넘으면 429 RATE_LIMITED와 Retry-After 초 값을 반환합니다.
캐시로 처리되는 수집도 한도에 포함합니다.
Kafka 자동 탐지는 사용자 HTTP 한도에 포함하지 않습니다.
목록/상세 조회, 로그인, 이미지 임베딩 요청 등 모든 API를 제한하는 전역 기능은 아닙니다.
현재 한도는 RedisWorkGuard 호출부의 V1 정책 값이며 환경 변수로 노출하지 않았습니다.

권한·활성 사용자 확인은 제한 및 캐시 조회보다 먼저 처리됩니다.
작품 탐지 요청의 소유권·임베딩 준비 확인도 요청 등록 전에 유지됩니다.

## 수집 캐시와 상품 중복 처리

- 검색 키: 마켓 + 소문자·앞뒤 공백 제거 query + limit의 SHA-256.
- 동일 검색 결과 TTL: 30초.
- 상품 표시 키: 마켓 + externalProductId의 SHA-256.
- 상품 표시 값: listing 전체 내용의 SHA-256과 DB 상품 ID.
- 동일 상품 표시 TTL: 60초.
- 캐시 원문 검색어와 인증 정보는 키에 포함하지 않습니다.

동일 검색 캐시가 있으면 어댑터 호출과 DB upsert를 생략합니다.
다른 검색에서 같은 상품을 발견하면 내용 해시와 기존 DB 상품 ID/상태를 확인하여 불필요한 upsert를 생략합니다.
상품 내용에는 판매자 정보와 이미지 해시도 포함되므로 변경된 응답은 새로 저장합니다.
DB의 외부 ID 고유 제약은 그대로 유지합니다.

응답에 cached, deduplicatedProducts가 추가됩니다.
캐시 응답은 upsertedProducts=0, upsertedImages=0이고 반환 상품 수를 deduplicatedProducts로 표시합니다.
신규 검색의 upsertedProducts/images는 이번 요청에서 실제 DB 저장·갱신한 수입니다.
중복 상품은 last_seen_at 및 임베딩 재요청을 갱신하지 않습니다.
실패한 상품 임베딩을 즉시 재시도하려면 STEP 7의 이미지 embedding POST를 사용합니다.
캐시 TTL 동안은 이전 결과가 반환될 수 있고, 상품 표시를 기반으로 만든 검색 캐시는 그 시점부터 최대 30초 더 유지됩니다.

## 마켓 단위 분산 잠금

동일 마켓 수집은 검색어가 달라도 하나의 잠금을 공유합니다.
SET NX와 120초 TTL, 무작위 소유 토큰을 사용합니다.
잠금을 얻지 못하면 409 COLLECTION_IN_PROGRESS와 Retry-After를 반환합니다.
잠금 획득 후 검색 캐시를 다시 확인하여 중복 수집을 줄입니다.

DB 저장 직전에 토큰을 확인하고 TTL을 120초로 갱신합니다.
토큰을 잃으면 409 COLLECTION_LEASE_LOST로 저장 전에 중단합니다.
DB importer 트랜잭션 제한은 30초입니다.
DB 저장 성공 후 토큰이 아직 일치할 때만 Lua로 캐시와 상품 표시를 함께 기록합니다.
해제도 토큰 비교 후 수행하므로 이전 소유자가 새 잠금을 삭제하지 않습니다.

현재 어댑터는 번들 Mock 데이터를 즉시 반환합니다.
주기적인 잠금 갱신이나 DB fencing token은 아직 없으므로, 프로세스가 임대 시간보다 오래 멈추는 상황까지 단일 실행을 보장하지는 않습니다.
STEP 12 외부 네트워크 어댑터에서는 요청 시간 제한과 임대 갱신 정책을 함께 검토해야 합니다.
DB 고유 제약과 임베딩 작업 고유 키가 최종 중복 저장을 방어합니다.

## 장애 시 동작

Redis의 요청 제한·캐시 읽기·잠금 확보가 실패하면 503 REDIS_GUARD_UNAVAILABLE과 Retry-After: 5를 반환합니다.
Redis 장애 상태에서 보호 없이 새 수집/수동 탐지를 시작하지 않습니다.
이미 DB 저장이 완료된 뒤 캐시 기록이나 잠금 해제가 실패하면 성공 결과를 유지합니다.
잠금은 TTL로 회수됩니다.
손상된 캐시 JSON은 없는 캐시처럼 처리하고 다음 성공 수집으로 교체합니다.
DB 저장이 실패하면 검색 캐시·상품 표시를 기록하지 않습니다.

DB를 수동 복원하거나 카탈로그를 직접 변경하면 짧은 TTL이 끝날 때까지 과거 캐시가 남을 수 있습니다.
현재 API에는 강제 캐시 초기화나 강제 수집 우회 옵션이 없습니다.

## 사용 예

```powershell
$headers=@{Authorization="Bearer $accessToken"}
$uri='http://localhost:8080/api/v1/admin/marketplaces/MOCK/collect'
$first=Invoke-RestMethod $uri -Method Post -Headers $headers -ContentType 'application/json' -Body '{"query":"forest","limit":20}'
$second=Invoke-RestMethod $uri -Method Post -Headers $headers -ContentType 'application/json' -Body '{"query":"FOREST","limit":20}'
$second.data.cached
$second.data.upsertedProducts
```

정상적인 두 번째 요청은 cached=true, upsertedProducts=0입니다.
Mock 수집 활성화와 관리자 설정은 STEP 6을, 임베딩 재시도는 STEP 7을 참고합니다.

## 검증

```powershell
Set-Location C:\copyright\_detect\_project\backend
.\gradlew.bat test bootJar --no-daemon
.\gradlew.bat integrationTest --tests '*RedisGuardIntegrationTest' --no-daemon
```

단위/MVC 테스트: 캐시 적중, 내용 기반 상품 중복 방지, DB 실패 시 캐시 기록 방지,
잠금 충돌·소유권 상실, 요청 제한·Redis 장애, Retry-After 응답.
통합 테스트: 실제 Redis Lua 요청 한도/TTL, 이전 잠금 소유자의 갱신·해제·기록 차단, 캐시와 상품 TTL.
Redis 컨테이너를 실행할 Docker 엔진이 필요합니다.

다음: STEP 12 실제 Marketplace 공식 API 연동.
