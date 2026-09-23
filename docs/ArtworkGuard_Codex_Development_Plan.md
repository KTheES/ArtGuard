# ArtworkGuard — AI 기반 카피상품 탐지 플랫폼 개발 명세서

> 원작자가 자신의 일러스트/디자인 이미지를 등록하면 AliExpress, Temu, Etsy, eBay 등 오픈마켓에서 해당 작품이 무단 사용된 것으로 의심되는 상품을 탐지하고, 유사도·상품 링크·판매자·증거 이미지를 제공하는 상용 SaaS 플랫폼.

---

# 0. 프로젝트 목표

## 0.1 핵심 문제

일러스트레이터, 디자이너, 사진작가 등의 창작물이 온라인 쇼핑몰에서 무단으로 다음과 같은 형태로 사용될 수 있다.

- 티셔츠
- 아크릴 키링
- 포스터
- 머그컵
- 휴대폰 케이스
- 스티커
- 에코백
- 피규어/굿즈
- 기타 인쇄 상품

기존 Reverse Image Search는 동일/유사 이미지를 찾는 데 강하지만, 작품이 상품 목업(Mockup)에 합성되거나 일부가 잘리고 변형된 경우 탐지 정확도가 크게 떨어질 수 있다.

본 프로젝트의 목표는 다음과 같다.

```text
원작 이미지 등록
        ↓
Marketplace 상품 데이터 수집
        ↓
상품 이미지에서 작품 후보 영역 추출
        ↓
AI Embedding 생성
        ↓
Vector Similarity Search
        ↓
도용 의심 상품 후보 생성
        ↓
사용자 검토
        ↓
증거 보관 / 신고 URL / Takedown 관리
```

---

# 1. 제품의 핵심 원칙

## 1.1 탐지는 "저작권 침해 확정"이 아니다

시스템은 법적 판정을 수행하지 않는다.

UI에서는 반드시 다음과 같은 표현을 사용한다.

- 도용 의심
- 침해 가능성
- 유사 상품
- 검토 필요
- 높은 유사도

사용 금지 표현:

- 저작권 침해 확정
- 불법 판매자
- 범죄 상품
- 도둑 판매자

실제 라이선스 계약, 2차 창작 허가, 공식 굿즈 여부를 시스템이 알 수 없기 때문이다.

---

# 2. 전체 기술 스택

## Backend

- Java 21+
- Spring Boot
- Gradle
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation
- Spring Actuator
- Spring Batch
- Spring WebFlux
- Spring Retry
- Spring Scheduler
- Flyway
- MapStruct
- Lombok

## Authentication / Authorization

- Spring Security
- JWT Access Token
- Refresh Token
- OAuth2 Login
- Google Login
- Apple Login
- Role Based Access Control

Roles:

```text
ROLE_USER
ROLE_CREATOR
ROLE_MODERATOR
ROLE_ADMIN
```

---

## Database

### Primary DB

PostgreSQL

사용 목적:

- User
- Artwork
- Marketplace
- Seller
- Product
- Detection
- Evidence
- Report
- Subscription
- Billing
- Audit Log

### Vector Search

PostgreSQL + pgvector

초기 단계에서는 별도 Vector DB를 사용하지 않는다.

```text
PostgreSQL
+
pgvector
```

장점:

- 운영 복잡도 감소
- 트랜잭션 관리 쉬움
- metadata + vector를 함께 관리 가능
- MVP/초기 상용화에 충분

향후 데이터 수가 매우 커질 경우 다음 제품 검토:

- Qdrant
- Milvus
- Weaviate
- Pinecone

---

## Cache

Redis

사용 목적:

- Refresh Token
- Session-like temporary state
- Rate Limiting
- Search result cache
- Product deduplication
- Distributed Lock
- Idempotency Key
- Crawling state
- Detection result cache

---

## Message Broker

Apache Kafka

주요 비동기 작업:

```text
상품 수집
이미지 다운로드
이미지 분석
Embedding 생성
Vector Search
Detection 생성
알림
증거 저장
```

Kafka Topic 예:

```text
marketplace.product.discovered
marketplace.product.updated

image.download.requested
image.download.completed

image.analysis.requested
image.analysis.completed

embedding.requested
embedding.completed

detection.created

notification.requested

evidence.capture.requested
evidence.capture.completed
```

---

# 3. AI Service

Spring Boot에 AI 모델을 직접 포함하지 않는다.

별도 AI Inference Service를 운영한다.

## Stack

- Python
- FastAPI
- PyTorch
- OpenCV
- Pillow
- HuggingFace Transformers

추천 모델 후보:

### Feature / Embedding

- DINOv2
- SigLIP
- CLIP

초기 권장:

```text
DINOv2 + CLIP/SigLIP ensemble
```

이유:

DINOv2:
- 시각적 구조/패턴 유사도 탐지에 강점

CLIP/SigLIP:
- 의미적 유사도 탐지에 강점

두 점수를 조합한다.

예:

```text
finalScore =
    dinov2Score * 0.65
    +
    siglipScore * 0.35
```

가중치는 실제 데이터셋으로 튜닝한다.

---

# 4. 이미지 탐지 파이프라인

## Step 1

사용자 원작 이미지 등록

```text
Original Image
↓
Preprocessing
↓
Embedding
↓
PostgreSQL / pgvector
```

---

## Step 2

Marketplace 상품 이미지 수집

```text
Product Listing
↓
Product Images
↓
Image Download
↓
S3 저장
```

---

## Step 3

상품 이미지 분석

상품 이미지 전체만 비교하면 정확도가 낮아질 수 있다.

예:

```text
티셔츠 사진
+
사람
+
배경
+
텍스트
+
원작 이미지
```

따라서 여러 crop을 생성한다.

```text
Original Product Image
↓
Image Region Extraction
↓
Multiple Crop Generation
```

예:

```text
Full Image

Center Crop

Object Crop

Printed Area Crop

Sliding Window Crop
```

---

# 5. Similarity Search

## 5.1 Embedding 저장

Artwork embedding:

```sql
artwork_embedding
```

Product image embedding:

```sql
product_image_embedding
```

---

## 5.2 Candidate Search

pgvector cosine similarity 사용.

개념:

```sql
SELECT
    product_image_id,
    1 - (embedding <=> :artworkEmbedding) AS similarity
FROM product_image_embedding
ORDER BY embedding <=> :artworkEmbedding
LIMIT 100;
```

---

# 6. 탐지 Score

단일 AI score만 사용하지 않는다.

예:

```text
visualEmbeddingSimilarity
semanticEmbeddingSimilarity
perceptualHashSimilarity
localFeatureSimilarity
ocrSimilarity
sellerRiskScore
```

최종 점수 예:

```text
Detection Score

Visual Similarity       50%
Local Feature Match     20%
Perceptual Hash         10%
OCR                     5%
Context / Metadata      5%
Seller History          10%
```

초기 MVP에서는 다음만 사용한다.

```text
Embedding Similarity
+
pHash
```

---

# 7. 시스템 아키텍처

```text
                ┌────────────────────┐
                │      Frontend      │
                │ Web / Mobile App   │
                └─────────┬──────────┘
                          │
                          ▼
                ┌────────────────────┐
                │   API Gateway      │
                │ / Load Balancer    │
                └─────────┬──────────┘
                          │
                          ▼
                ┌────────────────────┐
                │ Spring Boot API    │
                │                    │
                │ Spring Security    │
                │ REST API           │
                │ Business Logic     │
                └──────┬──────┬──────┘
                       │      │
          ┌────────────┘      └─────────────┐
          ▼                                  ▼
 ┌────────────────┐                ┌────────────────┐
 │ PostgreSQL     │                │ Redis          │
 │ + pgvector     │                │                │
 └────────────────┘                └────────────────┘

                       │
                       ▼
                ┌────────────────────┐
                │ Kafka              │
                └──────┬─────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼

 Marketplace       Image Worker     AI Service
 Collector                         FastAPI
        │              │              │
        ▼              ▼              ▼
 Marketplace        Object/S3      Embedding
 APIs / Feeds                     Similarity
```

---

# 8. Storage

Object Storage:

- AWS S3

저장 대상:

```text
original-artworks/
product-images/
evidence/
screenshots/
thumbnails/
reports/
```

예:

```text
s3://artworkguard-prod/
    artworks/
       {userId}/{artworkId}/original.webp

    products/
       {marketplace}/{productId}/{imageId}.webp

    evidence/
       {detectionId}/
```

---

# 9. Spring Boot 패키지 구조

추천:

```text
com.artworkguard

├── auth
│   ├── controller
│   ├── service
│   ├── security
│   ├── jwt
│   └── oauth

├── user

├── artwork
│   ├── controller
│   ├── service
│   ├── repository
│   ├── domain
│   └── dto

├── marketplace
│   ├── collector
│   ├── adapter
│   ├── service
│   └── domain

├── product

├── detection

├── embedding

├── evidence

├── report

├── notification

├── subscription

├── billing

├── kafka

├── redis

├── storage

├── admin

├── common
│   ├── exception
│   ├── response
│   ├── config
│   └── util

└── audit
```

---

# 10. 핵심 도메인

## User

```text
id
email
passwordHash
nickname
role
status
createdAt
updatedAt
```

---

## Artwork

```text
id
userId
title
description
originalImageUrl
thumbnailUrl
status
createdAt
updatedAt
```

---

## ArtworkEmbedding

```text
id
artworkId
model
modelVersion
embedding
createdAt
```

---

## Marketplace

```text
id
code
name
baseUrl
enabled
```

예:

```text
ALIEXPRESS
TEMU
ETSY
EBAY
AMAZON
SHEIN
```

---

## Seller

```text
id
marketplaceId
externalSellerId
sellerName
sellerUrl
riskScore
firstSeenAt
lastSeenAt
```

---

## Product

```text
id
marketplaceId
sellerId
externalProductId

title
productUrl

price
currency

status

firstSeenAt
lastSeenAt
```

---

## ProductImage

```text
id
productId

originalUrl
storageUrl

width
height

imageHash

createdAt
```

---

## ProductImageEmbedding

```text
id
productImageId
model
modelVersion
cropType
embedding
```

cropType:

```text
FULL
CENTER
OBJECT
PRINT_REGION
SLIDING_WINDOW
```

---

## Detection

```text
id

artworkId
productId
productImageId

similarityScore
visualScore
hashScore

riskLevel

status

createdAt
updatedAt
```

Risk Level:

```text
LOW
MEDIUM
HIGH
CRITICAL
```

Status:

```text
NEW
REVIEWING
CONFIRMED
FALSE_POSITIVE
AUTHORIZED
REPORTED
RESOLVED
```

---

# 11. Evidence

실제 상용 서비스에서 매우 중요하다.

상품이 삭제되면 링크만 남겨서는 증거가 사라질 수 있다.

따라서 Detection 생성 시 다음을 저장한다.

```text
상품 URL
상품 제목
판매자
가격
Marketplace
상품 이미지
페이지 Screenshot
탐지 날짜
HTML Snapshot optional
```

Evidence:

```text
id
detectionId
type
storageUrl
sha256
createdAt
```

---

# 12. API 설계

Base URL:

```text
/api/v1
```

---

## Authentication

```http
POST /auth/signup
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /auth/me
```

---

## Artwork

```http
POST   /artworks
GET    /artworks
GET    /artworks/{id}
PATCH  /artworks/{id}
DELETE /artworks/{id}
```

---

## Artwork Image

권장 방식:

```http
POST /artworks/upload-url
```

서버가 S3 Presigned URL을 생성한다.

```text
Frontend
↓
Spring Boot
↓
Presigned URL
↓
Frontend → S3 direct upload
```

대용량 이미지를 Spring 서버로 직접 전달하지 않는다.

---

# 13. Detection API

```http
GET /detections

GET /detections/{id}

PATCH /detections/{id}/status
```

Filter:

```text
artworkId
marketplace
riskLevel
status
from
to
```

---

# 14. Search API

수동 검색:

```http
POST /artworks/{artworkId}/search
```

응답:

```json
{
  "jobId": "uuid",
  "status": "QUEUED"
}
```

실제 탐지는 Kafka 비동기로 수행한다.

---

# 15. Detection Job

```text
User Search Request
↓
SearchJob 생성
↓
Kafka publish
↓
Candidate Search
↓
AI Similarity
↓
Detection 생성
↓
Notification
```

---

# 16. Redis 설계

Key 예:

```text
refresh-token:{userId}

rate-limit:{userId}:{endpoint}

search-job:{jobId}

product:seen:{marketplace}:{externalProductId}

crawler:lock:{marketplace}

detection:cache:{artworkId}
```

TTL 적용.

---

# 17. Kafka Event Schema

예:

```json
{
  "eventId": "uuid",
  "eventType": "PRODUCT_DISCOVERED",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T00:00:00Z",

  "payload": {}
}
```

모든 Event에 반드시 포함:

```text
eventId
eventType
eventVersion
occurredAt
traceId
```

---

# 18. Kafka 안정성

필수 구현:

- Producer Idempotence
- Consumer Retry
- Dead Letter Topic
- Idempotent Consumer
- Outbox Pattern

예:

```text
detection.created
detection.created.retry
detection.created.dlt
```

---

# 19. Transactional Outbox Pattern

DB Transaction과 Kafka publish 불일치 방지.

```text
Transaction

Detection INSERT
+
Outbox INSERT

COMMIT
```

별도 Publisher가 Outbox를 Kafka에 전달한다.

---

# 20. Security

## Password

BCrypt 또는 Argon2.

---

## JWT

Access Token:

```text
15분
```

Refresh Token:

```text
14~30일
```

Refresh Token은 Redis에 저장한다.

---

## OAuth

지원 권장:

```text
Google
Apple
```

---

# 21. API Security

필수:

- Rate Limit
- Request Validation
- CORS
- CSRF 정책 검토
- XSS 방어
- SQL Injection 방어
- SSRF 방어
- URL Validation
- File Validation

---

# 22. 이미지 업로드 보안

파일명 신뢰 금지.

검증:

```text
MIME Type
Magic Byte
Image Decode
File Size
Pixel Dimension
```

최대 이미지 크기 예:

```text
20 MB
```

변환 후 저장:

```text
WebP
```

원본 보존 여부는 서비스 정책으로 결정한다.

---

# 23. Marketplace Collector

중요:

무조건적인 HTML scraping을 기본 전략으로 두지 않는다.

우선순위:

```text
1. 공식 API
2. Marketplace Partner API
3. Affiliate API
4. 공식 Feed
5. Search API
6. 허용되는 범위 내 웹 수집
```

서비스 약관과 robots 정책을 검토해야 한다.

---

# 24. Marketplace Adapter

공통 Interface:

```java
public interface MarketplaceAdapter {

    MarketplaceCode getMarketplace();

    List<MarketplaceProduct> searchProducts(
        MarketplaceSearchRequest request
    );

    MarketplaceProductDetail getProduct(
        String externalProductId
    );
}
```

구현:

```text
AliExpressAdapter
EtsyAdapter
EbayAdapter
TemuAdapter
```

---

# 25. 상품 수집 전략

원작 이미지 하나당 전체 Marketplace를 완전 탐색하지 않는다.

비용이 너무 크다.

다단계 검색:

```text
Stage 1

Keyword Search
↓
Candidate Product

Stage 2

Thumbnail Embedding
↓
Approximate Search

Stage 3

High Resolution Image Analysis
↓
Final Score
```

---

# 26. Keyword 생성

원작 metadata 기반:

```text
title
tags
description
OCR
image captioning
```

AI를 이용해 검색 키워드를 만든다.

예:

```text
black cat moon illustration
anime cat poster
black cat acrylic keychain
```

---

# 27. 자동 Monitoring

사용자는 작품을 Monitoring 상태로 설정할 수 있다.

```text
Artwork
monitoringEnabled = true
```

스케줄:

```text
FREE
weekly

PRO
daily

BUSINESS
multiple times per day
```

실제 빈도는 비용 정책에 따라 결정한다.

---

# 28. Notification

지원 채널:

MVP:

```text
Email
Web Notification
```

향후:

```text
Push
Slack
Discord
Webhook
```

Notification Event:

```text
DetectionCreated
```

---

# 29. Admin 기능

필수:

```text
사용자 관리
Detection 관리
False Positive 관리
Marketplace 상태
Crawler 상태
AI 모델 버전
Kafka 상태
Job 상태
Reported Listing 상태
```

---

# 30. Observability

## Logging

Structured JSON Logging

포함:

```text
traceId
userId
requestId
jobId
artworkId
productId
```

---

## Metrics

Prometheus

Grafana

측정:

```text
API Latency
Error Rate
Kafka Lag
Detection Count
AI Inference Time
Crawler Success Rate
Marketplace Error Rate
False Positive Rate
```

---

# 31. AWS Infrastructure

권장 초기 구성:

```text
CloudFront
↓
ALB
↓
ECS Fargate
 ├─ Spring Boot API
 ├─ Worker
 └─ Collector
```

AI Service:

```text
GPU EC2
or
SageMaker
```

초기 사용자가 적다면 GPU 서버를 상시 운영하지 않고 Batch 형태도 검토한다.

---

## DB

```text
Amazon RDS PostgreSQL
```

---

## Redis

```text
Amazon ElastiCache Redis
```

---

## Kafka

초기:

```text
Amazon MSK Serverless
```

또는 비용 절감을 위해 초기에는 Kafka를 Docker/ECS에서 운영할 수 있지만 상용 운영 환경에서는 관리형 서비스 권장.

---

## Storage

```text
Amazon S3
```

---

# 32. CDN

```text
CloudFront
```

사용:

```text
Thumbnail
Product Image
Artwork Preview
```

원본 작품은 public CDN URL로 노출하지 않는다.

---

# 33. 원작 보호

매우 중요.

사용자가 등록한 원작 자체가 서비스에서 유출되어서는 안 된다.

원본:

```text
Private S3 Bucket
```

접근:

```text
Signed URL
```

Preview:

```text
Watermarked
Low Resolution
```

---

# 34. 데이터베이스 Migration

Flyway 사용.

```text
V1__create_user.sql
V2__create_artwork.sql
V3__create_product.sql
V4__create_detection.sql
V5__enable_pgvector.sql
```

---

# 35. 개발 Step

---

# STEP 1 — 프로젝트 Bootstrap

목표:

Spring Boot 기본 프로젝트 생성.

구현:

- Gradle
- PostgreSQL
- Redis
- Kafka
- Docker Compose
- Flyway
- Global Exception Handler
- API Response Format
- Swagger/OpenAPI
- Health Check

Docker Compose:

```text
PostgreSQL
Redis
Kafka
```

완료 조건:

```text
./gradlew test 성공
docker compose up 성공
GET /actuator/health → UP
```

---

# STEP 2 — Authentication

구현:

```text
Signup
Login
JWT
Refresh Token
Logout
Spring Security
```

Redis:

```text
refresh-token:{userId}
```

완료 조건:

```text
회원가입
로그인
Access Token 발급
Refresh
Logout
보호 API 접근
```

---

# STEP 3 — Artwork Domain

구현:

```text
Artwork CRUD
```

S3 Presigned Upload 구현.

Flow:

```text
POST upload-url
↓
S3 upload
↓
POST artwork
```

완료 조건:

사용자가 작품을 등록하고 조회할 수 있다.

---

# STEP 4 — AI Service

별도 repository/module 생성.

```text
ai-service
```

FastAPI Endpoint:

```http
POST /v1/embeddings
```

Request:

```json
{
  "imageUrl": "..."
}
```

Response:

```json
{
  "model": "dinov2",
  "version": "1",
  "dimension": 768,
  "embedding": []
}
```

완료 조건:

이미지 입력 → embedding 반환.

---

# STEP 5 — pgvector

PostgreSQL pgvector 활성화.

Artwork embedding 저장.

```text
Artwork Upload
↓
Kafka
↓
Embedding Worker
↓
AI Service
↓
pgvector
```

완료 조건:

등록 작품 embedding 저장 성공.

---

# STEP 6 — Marketplace Domain

구현:

```text
Marketplace
Seller
Product
ProductImage
```

초기에는 실제 외부 Marketplace 대신 Mock Marketplace를 만든다.

```text
MockMarketplaceAdapter
```

완료 조건:

가짜 상품 데이터를 DB에 저장할 수 있다.

---

# STEP 7 — Product Embedding

Product Image → AI Service.

```text
ProductImage
↓
Kafka
↓
Embedding
↓
ProductImageEmbedding
```

완료 조건:

상품 이미지 embedding 저장 성공.

---

# STEP 8 — Detection Engine V1

구현:

```text
Artwork Embedding
vs
Product Image Embedding
```

cosine similarity.

Threshold 예:

```text
>= 0.92 CRITICAL
>= 0.85 HIGH
>= 0.75 MEDIUM
```

초기 임시 값이며 실제 데이터로 튜닝한다.

완료 조건:

유사 상품 Detection 생성.

---

# STEP 9 — Detection API

구현:

```text
Detection List
Detection Detail
Status 변경
```

완료 조건:

사용자가 탐지 결과를 검토할 수 있다.

---

# STEP 10 — Kafka Pipeline

현재 Sync 작업을 Event Driven 방식으로 전환한다.

```text
ArtworkCreated
↓
EmbeddingRequested

ProductDiscovered
↓
ImageAnalysisRequested
↓
EmbeddingRequested
↓
DetectionRequested
```

완료 조건:

API 요청이 AI 작업을 기다리지 않는다.

---

# STEP 11 — Redis

구현:

```text
Rate Limiting
Search Job Cache
Product Deduplication
Distributed Lock
```

완료 조건:

중복 crawling 방지.

---

# STEP 12 — Real Marketplace Integration

Marketplace 하나부터 시작한다.

추천:

```text
eBay 또는 Etsy
```

공식 API 접근이 상대적으로 명확한 플랫폼부터 PoC를 진행한다.

그 후 AliExpress 등 확대.

완료 조건:

실제 상품 listing을 자동 수집한다.

---

# STEP 13 — Advanced Image Detection

상품 이미지에서 artwork 영역 탐지.

구현 후보:

```text
Object Detection
Segmentation
Multi Crop
Sliding Window
Local Feature Matching
```

최종:

```text
Full Image Embedding
+
Region Embedding
```

완료 조건:

티셔츠/머그컵 등에 삽입된 작품 탐지 성능 향상.

---

# STEP 14 — pHash

Perceptual Hash 추가.

```text
pHash
dHash
```

Embedding Score와 ensemble.

완료 조건:

Crop/Resize/Compression에 대한 탐지력 강화.

---

# STEP 15 — Evidence System

Detection 생성 시 증거 저장.

```text
Screenshot
Product Image
Seller
Price
URL
Timestamp
SHA-256
```

완료 조건:

상품 삭제 후에도 당시 Detection 자료 확인 가능.

---

# STEP 16 — Notification

Email Notification.

예:

```text
새로운 도용 의심 상품이 발견되었습니다.

Artwork
Marketplace
Similarity
Product URL
```

완료 조건:

HIGH 이상 Detection 발생 시 알림.

---

# STEP 17 — Monitoring Scheduler

작품 자동 검색.

```text
ArtworkMonitorJob
```

Spring Batch / Scheduler.

대량 작업은 Kafka로 분배한다.

완료 조건:

등록 작품 자동 모니터링.

---

# STEP 18 — Subscription

Plan:

```text
FREE
CREATOR
PRO
BUSINESS
```

예:

FREE

```text
3 Artwork
Weekly Scan
Basic Detection
```

CREATOR

```text
50 Artwork
Daily Scan
Email Alert
Evidence
```

PRO

```text
Unlimited or High Limit
Priority Scan
Advanced Detection
Reports
```

---

# STEP 19 — Billing

Stripe 등 결제 Provider 연동.

Subscription 상태:

```text
TRIAL
ACTIVE
PAST_DUE
CANCELED
```

Webhook은 반드시 Idempotency 처리.

---

# STEP 20 — Production Hardening

필수:

```text
Rate Limit
Circuit Breaker
Retry
Timeout
DLQ
Idempotency
Audit Log
Backup
Encryption
Secret Manager
```

---

# STEP 21 — AI Evaluation Dataset

상용화를 위해 가장 중요한 단계 중 하나.

테스트 데이터셋을 구축한다.

Positive:

```text
원본
Crop
Rotate
Color change
Mockup
Watermark
Partial
Mirror
```

Negative:

```text
비슷한 스타일
같은 캐릭터 유형
색상만 비슷한 이미지
```

평가:

```text
Precision
Recall
F1
False Positive Rate
False Negative Rate
```

---

# STEP 22 — Detection Threshold 튜닝

사용자에게 가장 불쾌한 문제는 False Positive가 지나치게 많이 발생하는 것이다.

목표:

```text
Precision 우선
```

추천 UX:

```text
90+ 매우 높은 유사도
80~89 높은 유사도
70~79 검토 필요
<70 기본 숨김
```

실제 숫자는 모델 평가 결과로 결정한다.

---

# STEP 23 — Takedown Workflow

향후:

```text
Detection
↓
User Confirm
↓
Report Draft
↓
Marketplace Report Link
↓
Report Tracking
```

중요:

자동 신고는 Marketplace 정책과 법적 리스크를 검토한 후 도입한다.

초기에는:

```text
신고 페이지 링크
+
필요 증거 정리
```

까지만 제공한다.

---

# STEP 24 — Seller Intelligence

Seller 별 분석:

```text
Detection Count
Creator Count
Marketplace Count
Risk Score
First Seen
Last Seen
```

반복적으로 의심 상품을 판매하는 seller 식별.

---

# STEP 25 — Abuse Prevention

악의적 사용자가 다른 사람의 작품을 자신의 작품이라고 등록할 수 있다.

대책:

```text
Email verification
Account age
Upload limit
Proof of ownership optional
Social account verification
Original file metadata
PSD / Procreate source verification optional
```

---

# STEP 26 — Audit Log

관리자 및 주요 사용자 행동 기록.

```text
LOGIN
ARTWORK_CREATED
DETECTION_STATUS_CHANGED
REPORT_CREATED
ADMIN_ACTION
```

---

# STEP 27 — 개인정보 / 법률

상용화 전 반드시 검토:

```text
개인정보 처리방침
서비스 이용약관
저작권 신고 정책
DMCA 대응 정책
Marketplace 이용약관
크롤링 정책
이미지 저장 정책
Data Retention
삭제 요청
```

법률 전문가 검토 권장.

---

# STEP 28 — MVP 범위

MVP에서 구현할 것:

```text
회원가입
로그인

Artwork 등록
S3

AI Embedding

Mock Marketplace

Product 등록

pgvector

Similarity Search

Detection

Detection UI

Email Alert
```

MVP에서 제외:

```text
여러 Marketplace
자동 신고
Seller Risk AI
Mobile App
Advanced Billing
Enterprise Features
```

---

# 29. MVP 성공 기준

다음 시나리오가 동작하면 MVP 성공.

```text
1.

사용자가 artwork.png 등록


2.

시스템이 embedding 생성


3.

Mock Marketplace에

shirt.png

등록


4.

shirt.png 안에 artwork가 포함


5.

AI가 similarity 탐지


6.

Detection 생성


7.

사용자 Dashboard에

"높은 유사도 상품"

표시


8.

상품 URL 제공
```

---

# 30. 첫 상용 Beta

초기 베타 대상:

```text
일러스트레이터
웹툰 작가
이모티콘 작가
팬아트 작가
디자이너
사진작가
```

추천:

약 20~50명의 창작자와 Closed Beta.

---

# 31. 가장 중요한 기술 리스크

## 1

Marketplace Data Access

각 Marketplace API 정책 차이.

---

## 2

AI False Positive

비슷한 그림체를 도용으로 탐지할 가능성.

---

## 3

AI False Negative

심한 crop / 변형 탐지 실패.

---

## 4

비용

수백만 상품 이미지 inference 비용.

---

## 5

법률

저작권 침해 확정 표현 금지.

Marketplace 약관 준수.

---

# 32. 비용 최적화 전략

전체 이미지를 고성능 AI 모델에 넣지 않는다.

```text
Cheap Filter
↓
Medium Filter
↓
Expensive AI
```

예:

```text
pHash

↓ Candidate

Small Embedding Model

↓ Candidate

DINOv2

↓ Candidate

Local Feature Matching
```

---

# 33. Repository 구성

추천 Monorepo:

```text
artworkguard/

├── backend/
│   └── Spring Boot

├── ai-service/
│   └── FastAPI

├── frontend/

├── infra/
│   ├── docker
│   └── terraform

├── docs/

└── docker-compose.yml
```

---

# 34. Local Development

Docker Compose:

```text
PostgreSQL
pgvector
Redis
Kafka
Kafka UI
MinIO(optional)
```

AWS 의존 없이 로컬 개발 가능하도록 구성한다.

---

# 35. CI/CD

GitHub Actions.

Pipeline:

```text
Pull Request

Backend Test
AI Test
Lint
Build
Docker Build
Security Scan
```

Main Merge:

```text
Docker Image
↓
ECR
↓
ECS Deploy
```

---

# 36. Test

Backend:

```text
JUnit 5
Mockito
Testcontainers
RestAssured
```

통합 테스트에서는 Testcontainers로:

```text
PostgreSQL
Redis
Kafka
```

를 띄운다.

---

# 37. Codex 구현 규칙

Codex는 한 번에 전체 시스템을 구현하지 않는다.

반드시 STEP 단위로 개발한다.

각 STEP마다:

```text
1. 요구사항 분석

2. 변경 파일 목록 제시

3. DB 변경 여부 확인

4. 구현

5. Unit Test

6. Integration Test

7. 실행 방법

8. API 테스트 예시

9. 완료 조건 확인
```

---

# 38. Codex 공통 지시사항

다음 원칙을 항상 지켜라.

```text
- Java 21 이상

- Spring Boot + Gradle

- PostgreSQL 사용

- DB 변경은 Flyway 사용

- Redis 사용

- Kafka Event Driven Architecture 사용

- Controller에 Business Logic 작성 금지

- DTO와 Entity 분리

- Entity 직접 API 반환 금지

- 모든 입력값 Validation

- Global Exception Handler 사용

- Optional 남용 금지

- N+1 문제 방지

- Transaction 경계 명확화

- 외부 API에는 Timeout 설정

- Retry는 무한 Retry 금지

- Kafka Consumer는 Idempotency 보장

- Kafka DLT 구성

- 로그에 Password / JWT / 개인정보 출력 금지

- 테스트 없는 기능 추가 금지

- 환경 변수로 Secret 관리

- 하드코딩된 Credential 금지
```

---

# 39. 첫 Codex Prompt

아래부터 구현한다.

```text
ArtworkGuard 프로젝트를 시작한다.

Architecture:

Backend
- Java 21+
- Spring Boot
- Gradle
- Spring Security
- PostgreSQL
- pgvector
- Redis
- Kafka
- Flyway

AI
- Python FastAPI
- PyTorch
- DINOv2 / SigLIP

Storage
- S3

Architecture Style
- Modular Monolith
- Event Driven
- 향후 Microservice 분리가 가능하도록 Domain Boundary를 명확하게 한다.

현재는 STEP 1만 구현한다.

STEP 1 목표:

1. Spring Boot 프로젝트 기본 구조 생성
2. Gradle 설정
3. Docker Compose 생성
4. PostgreSQL + pgvector
5. Redis
6. Kafka
7. Flyway
8. Spring Actuator
9. OpenAPI
10. Global Exception Handler
11. 공통 API Response
12. 테스트 환경 구성

테스트는 Testcontainers 기반으로 구성한다.

패키지:

com.artworkguard

아래 구조를 기본으로 만든다.

auth
user
artwork
marketplace
product
detection
embedding
evidence
notification
subscription
billing
kafka
redis
storage
admin
audit
common

주의:

아직 Authentication, Artwork CRUD 등 STEP 2 이상의 기능은 구현하지 마라.

먼저:

1. 생성/수정할 파일 목록
2. 설계 결정
3. dependency 목록

을 설명한 후 코드를 작성하라.

작업 완료 후 반드시:

- 실행 방법
- Docker Compose 실행 명령
- Test 실행 명령
- Health Check 방법
- STEP 1 완료 체크리스트

를 출력하라.
```

---

# 40. 개발 순서 요약

```text
STEP 1
Infrastructure Bootstrap

STEP 2
Authentication

STEP 3
Artwork

STEP 4
AI Service

STEP 5
pgvector

STEP 6
Marketplace Domain

STEP 7
Product Embedding

STEP 8
Detection Engine

STEP 9
Detection API

STEP 10
Kafka Pipeline

STEP 11
Redis Optimization

STEP 12
Real Marketplace API

STEP 13
Advanced Image Detection

STEP 14
pHash

STEP 15
Evidence

STEP 16
Notification

STEP 17
Automatic Monitoring

STEP 18
Subscription

STEP 19
Billing

STEP 20
Production Hardening

STEP 21
AI Evaluation

STEP 22
Threshold Tuning

STEP 23
Takedown Workflow

STEP 24
Seller Intelligence

STEP 25
Abuse Prevention

STEP 26
Audit

STEP 27
Legal / Privacy

STEP 28+
Scale / Enterprise
```

---

# 41. 제품의 장기 방향

최종적으로 ArtworkGuard는 단순 Reverse Image Search가 아니라 다음 제품을 목표로 한다.

```text
AI Copyright Monitoring Platform
```

핵심 Value:

```text
Upload Once

↓

Continuous Monitoring

↓

Marketplace Detection

↓

AI Similarity Analysis

↓

Evidence Preservation

↓

Creator Notification

↓

Takedown Assistance
```

궁극적으로는:

```text
"창작자를 위한 자동 저작권 감시 시스템"
```

을 목표로 한다.
