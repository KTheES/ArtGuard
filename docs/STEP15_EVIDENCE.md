# STEP 15 Evidence System

탐지 결과가 생성되거나 다시 탐지될 때마다 당시 상품 정보를 별도 스냅샷으로 저장합니다. 상품이 `REMOVED` 상태가 되더라도 증거 조회는 상품 테이블의 현재 상태 대신 스냅샷을 사용합니다.

## 보존 항목

- 마켓플레이스와 외부 상품 ID
- 상품 제목·URL·가격·통화
- 판매자 ID·이름·URL
- 상품 이미지 원본 URL
- 불변 PNG 저장 키·크기·이미지 SHA-256
- 캡처 시각
- 정확한 JSON 스냅샷과 SHA-256
- 서버 생성 SVG 증거 카드와 SHA-256

SVG 카드는 저장된 필드를 사람이 확인하기 쉽게 렌더링한 자료입니다. 실제 마켓 페이지를 브라우저로 캡처한 화면은 아닙니다. 외부 HTML이나 스크립트를 포함하지 않으며 응답에는 `Content-Security-Policy: default-src 'none'; sandbox`와 `nosniff`를 적용합니다.

## API

```http
GET /api/v1/detections/{detectionId}/evidence
GET /api/v1/detections/{detectionId}/evidence/{evidenceId}/screenshot
GET /api/v1/detections/{detectionId}/evidence/{evidenceId}/image
Authorization: Bearer <access-token>
```

세 API 모두 작품 소유자만 접근할 수 있습니다. screenshot은 `image/svg+xml`, image는 SHA-256을 다시 검증한 `image/png`를 `no-store`로 반환합니다. 저장 키는 API 응답에 노출하지 않습니다.

## 무결성과 수명

Flyway V13의 `evidence_snapshot`은 탐지 실행별 append-only 레코드입니다. DB trigger가 UPDATE와 DELETE를 거부합니다. JSON과 SVG의 원문 및 각 SHA-256을 함께 저장하고, 상품 이미지는 콘텐츠 주소 방식의 `product-images/{imageId}/{sha256}.png`만 허용합니다. 증거 생성에 실패하면 탐지 저장 트랜잭션도 롤백됩니다.

기존 상품 이미지에는 저장 크기가 없으므로 V13이 완료된 상품 임베딩 작업을 한 번 재처리 큐에 넣습니다. AI·S3가 준비되지 않았다면 `EMBEDDING_ENABLED=false`로 migration을 적용하고 준비 후 작업자를 켜세요.

## 검증

- 백엔드 단위·MVC 테스트 170개 통과
- 실행 JAR 생성 성공
- 소유권 차단, SVG 보안 헤더, 이미지 저장 키 제한과 SHA-256 재검증 테스트 포함
- 상품 `REMOVED` 후 증거 조회 통합 시나리오 작성
- Docker 엔진 부재로 V13 migration과 해당 통합 시나리오는 실행하지 못함

이 스냅샷은 내부 감사와 사용자 검토 자료입니다. 공인 시각 인증이나 제3자 전자증거 보관 서비스를 대체하지 않습니다.
