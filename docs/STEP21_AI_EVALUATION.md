# STEP 21 AI 평가 데이터셋

## 제공 범위

ai-service/evaluation은 로컬 이미지 쌍을 검증하고 기존 고정 DINOv2 모델로 평가합니다.
합성 smoke 데이터 27쌍은 기하학적 원본 3개에서 생성한 양성 24쌍과 별개 도형 음성 3쌍입니다.
양성은 original/crop/rotate/color_change/mockup/watermark/partial/mirror를 각각 포함합니다.
실제 스타일·캐릭터 유형·색상 유사 음성은 아직 수집되지 않았으며 보고서의 missing_categories에 표시됩니다.

합성 데이터의 높은 점수는 실제 시장에서의 오탐률, 도용 여부, 저작권 침해 판단을 뜻하지 않습니다.
법적·사실적 도용 판단이 아닌 시각적 원본 대응 여부를 평가합니다.

## 실행

ai-service 디렉터리에서 기존 Python 환경을 사용합니다.

~~~powershell
python -m pytest -q
python -m evaluation.run evaluation/fixtures/smoke/dataset.json --output basic-report.json --model-cache .model-cache
python -m evaluation.run evaluation/fixtures/smoke/dataset.json --output advanced-report.json --model-cache .model-cache --advanced
~~~

모델은 local_files_only로 로드하고 외부 이미지를 다운로드하지 않습니다.
없는 모델 캐시를 임의의 모델로 대체하지 않습니다. 기존 scripts/setup.ps1 환경에 맞춰 requirements를 설치하고 프로젝트 고정 모델을 준비해야 합니다.
출력 파일이 이미 있으면 덮어쓰지 않습니다.
새 smoke 데이터는 python -m evaluation.build_smoke <새 디렉터리>로 재생성할 수 있습니다.

## 데이터 형식과 수집

UTF-8 JSON manifest 예시:

~~~json
{
  "schema": "artworkguard-evaluation-v1",
  "synthetic": false,
  "pairs": [{
    "id": "reviewed-pair-0001",
    "split": "validation",
    "label": false,
    "category": "similar_style",
    "reference": {"path": "images/source.png", "sha256": "<파일 SHA-256>", "source_group": "artist-work-001"},
    "candidate": {"path": "images/candidate.png", "sha256": "<파일 SHA-256>", "source_group": "artist-work-002"},
    "provenance": "소유자 제공 또는 원본 출처와 수집 날짜",
    "rights": "평가 이용 허락 또는 라이선스 근거",
    "reviewer": "검토자 식별자",
    "review_status": "reviewed"
  }]
}
~~~

모든 이미지는 manifest 디렉터리 아래에 두고 해시를 기록합니다. 같은 작품의 변형·같은 원본에서 파생된 모든 이미지에는 같은 source_group을 부여합니다.
reference와 candidate 양쪽의 그룹 및 동일 파일 해시가 train/validation/test에 걸치면 검증이 실패합니다.
파일 경로 탈출, 잘못된 해시, 중복 pair ID, 잘못된 label/category, 미검토 실제 데이터를 거부합니다.
출처·권리 필드는 필수지만 문자열 존재 검사만으로 권리의 진위를 확인할 수는 없습니다.

수집 순서:

1. 평가 사용 권한이 있는 서로 다른 원본 작품을 확보하고 source_group을 부여합니다.
2. 원본 그룹을 먼저 train/validation/test로 분리한 뒤, 그 그룹 안에서 변형을 생성합니다.
3. crop/rotate/color_change/mockup/watermark/partial/mirror의 정도와 상품 조건이 다양한 양성을 확보합니다.
4. similar_style/same_character_type/similar_color 음성은 단순 파일 차이가 아니라 원본 대응이 없음을 사람이 검토합니다.
5. 애매한 쌍은 검토 대기 풀에 남기고 evaluated manifest에는 넣지 않습니다. 검토 불일치는 별도 재검토합니다.
6. 실제 시험 데이터는 합성과 별도 manifest로 유지하며 분포와 각 범주의 표본 수를 보고합니다.

## 평가 정의

기본 모드는 전체 이미지 DINO cosine을 사용합니다.
고급 모드는 전체 및 기존 고정 겹침 영역 5개 각각에서
max(cosine, 0.8*max(0,cosine)+0.1*pHashSimilarity+0.1*dHashSimilarity)를 계산한 뒤 최댓값을 사용합니다.
백엔드의 현재 앙상블 공식과 일치합니다.
이미지는 EXIF 방향을 반영하고 투명 영역을 흰색으로 합성합니다.

threshold 이상이면 양성으로 예측합니다. 기본값은 기존 .75이며 이번 단계에서 운영 임계값을 변경하지 않습니다.
보고서에는 TP/FP/TN/FN, Precision, Recall, F1, FPR, FNR, 범주별 결과·누락 범주·개별 점수·모델 revision·전처리 버전·manifest SHA가 포함됩니다.
분모가 0이면 지표는 null입니다. 양성만 있는 범주의 FPR을 0으로 표시하지 않습니다.
이는 이미지 쌍 기준 평가이며, 대규모 검색 후보 생성·상품별 중복 제거·실제 음성 비율을 포함하는 end-to-end 시장 평가와 다릅니다.

STEP 22에서는 validation split으로 후보 임계값을 비교하고 잠근 test split은 최종 평가에만 사용합니다.
현재 합성 데이터만으로 운영 임계값을 결정하면 안 됩니다.
