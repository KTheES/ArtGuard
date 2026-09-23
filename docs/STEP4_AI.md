# ArtworkGuard AI — STEP 4

FastAPI가 DINOv2 이미지 임베딩을 반환하는 내부 서비스입니다. Spring Boot와 별도 프로세스로 실행합니다.

- 모델: facebook/dinov2-base
- 고정 리비전: f9e44c814b77203eaa57a6bdbbd535f21ede1415
- 출력: CLS token, 768차원, L2 정규화
- 전처리 버전: rgb-white-bicubic256-center224-imagenet-cls-l2-v1
- 기본 실행: CPU, 추론 동시 실행 1개, torch thread 4개
- 모델 로드는 로컬 safetensors 파일만 사용하며 원격 코드는 실행하지 않습니다.
- 모델이 없거나 로드에 실패하면 readiness/추론은 503이며 임의 벡터를 반환하지 않습니다.

## 설정과 설치

Python 3.12 이상에서 실행합니다. CPU PyTorch를 별도 공식 인덱스로 먼저 설치합니다.

```powershell
Set-Location C:\copyright\_detect\_project\ai-service
.\scripts\setup.ps1 -Python python -Dev
```

이 PC에는 일반 python 명령이 없었습니다. 검증에 사용한 Python을 지정하려면:

```powershell
.\scripts\setup.ps1 -Python 'C:\Users\espls\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -Dev
```

설치는 .venv에만 적용합니다. 모델 캐시는 .model-cache에 저장되며 Git에서 제외됩니다.
검증에 사용한 모델 캐시도 프로젝트에 배치했으므로 같은 리비전은 재사용할 수 있습니다.
다른 PC에서는 최초 다운로드에 인터넷과 약 350MB의 모델 공간이 필요합니다.

## 실행

AI_API_KEY는 백엔드와 동일한 값을 사용해야 합니다. 예시의 새 난수는 최초 로컬 설정 시 한 번만 생성하고 필요한 프로세스에 같은 값을 전달하세요.

```powershell
$env:AI_API_KEY = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
# STEP 3의 로컬 MinIO 주소와 정확히 일치해야 합니다.
$env:AI_ALLOWED_IMAGE_ORIGINS = 'http://localhost:9000'
$env:AI_ALLOW_PRIVATE_IMAGES = 'true'
.\scripts\start.ps1
```

실제 S3 사용 시에는 허용할 HTTPS origin만 쉼표로 나열하고 AI_ALLOW_PRIVATE_IMAGES=false로 설정합니다.
예: https://your-bucket.s3.ap-northeast-2.amazonaws.com
허용 목록이 비어 있으면 이미지 다운로드는 모두 거절됩니다.
.env.example은 항목 안내이며 .env를 자동으로 읽지는 않습니다.

## API

```http
POST /v1/embeddings
X-API-Key: <service-key>
Content-Type: application/json

{"imageUrl":"<private S3 signed GET URL>"}
```

응답은 model, modelId, version, preprocessingVersion, dimension, normalized, embedding을 포함합니다.
version은 모델 commit이며 전처리 버전이 다른 벡터끼리는 같은 검색 인덱스에 혼합하지 마세요.

```powershell
$headers = @{ 'X-API-Key' = $env:AI_API_KEY }
$body = @{imageUrl=$signedImageUrl} | ConvertTo-Json
$result = Invoke-RestMethod http://127.0.0.1:8001/v1/embeddings -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$result.dimension
$result.embedding.Count
Invoke-RestMethod http://127.0.0.1:8001/health/live
Invoke-RestMethod http://127.0.0.1:8001/health/ready
```

live는 프로세스 상태, ready는 모델 로딩 상태입니다. ready가 UP이어도 저장소 접근 설정은 별도로 유효해야 합니다.
인증 실패 401, 허용하지 않은 origin/IP 403, 요청/이미지 크기 초과 413, 잘못된 JSON 422,
동시 추론 용량 초과 429, 모델 준비 안 됨 503입니다.

## 이미지 검증

URL의 scheme/host/port를 정확히 비교합니다. credentials URL, fragment, 리다이렉트, 링크 로컬/메타데이터 주소는 거절합니다.
DNS 확인 결과를 검증한 뒤 해당 IP로 연결하고 원래 Host 및 HTTPS 인증서/SNI를 유지합니다.
환경 proxy 설정을 자동 사용하지 않습니다. 내부 IP는 명시적인 로컬 개발 옵션과 origin 허용이 모두 필요합니다.

PNG/JPEG, 최대 64 MiB, 한 변 8192px 및 총 1600만 픽셀 이하를 지원합니다.
64 MiB 한도는 STEP 3에서 입력 이미지를 PNG로 정규화하면서 커질 수 있는 크기를 수용합니다.
매직 형식과 MIME 일치, 이미지 verify/decode, EXIF 방향, 투명 영역 흰 배경 처리를 적용합니다.
shortest edge 256 → center crop 224 → ImageNet normalize를 사용합니다.
리사이즈 중간 이미지가 4194304 픽셀을 넘는 극단적 종횡비는 거절합니다.

요청 JSON은 16 KiB까지이며 다운로드는 크기 제한·연결/읽기 timeout·청크별 deadline을 적용합니다.
한 요청이 처리 중이면 다음 요청은 즉시 429를 반환합니다.
추론 자체에는 강제 종료 타이머가 없으므로 운영 환경에서는 프로세스/프록시 시간 제한을 추가해야 합니다.

## 테스트

```powershell
.\.venv\Scripts\python.exe -m pytest -q
# 다운로드한 실제 모델 + 로컬 HTTP 서버를 사용하는 테스트
.\.venv\Scripts\python.exe -m pytest -q -m model -o addopts=
```

기본 테스트는 모델 없이 API 계약과 검증 로직을 검사합니다.
실제 모델 테스트는 768차원·유한 값·단위 벡터·반복 추론 재현성 및 HTTP 이미지 입력을 검사합니다.
탐지 정확도나 crop/mockup 강건성을 평가한 결과는 아닙니다.

Dockerfile도 제공하지만 이번 환경에서는 Docker 엔진이 없어 이미지 빌드는 검증하지 못했습니다.
Docker에서 실행하려면 /models에 준비된 모델 캐시를 마운트하고 API 키·저장소 origin을 설정해야 합니다.
컨테이너 내부 localhost는 호스트 PC의 MinIO가 아니므로 같은 서명 hostname으로 접근하도록 네트워크를 구성해야 합니다.

공식 참고: [DINOv2](https://huggingface.co/docs/transformers/model_doc/dinov2),
[CPU PyTorch](https://pytorch.org/get-started/locally/).

다음 연결은 STEP 5의 Kafka 작업자 및 pgvector 저장입니다.
