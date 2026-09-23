# STEP 22 임계값 검증 도구

`evaluation.tune`은 validation 점수만 사용해 0.00~1.00을 0.01 간격으로 비교합니다. 기본 목표 정밀도는 0.98이며, 이를 만족하는 후보 중 재현율 → 정밀도 → 높은 임계값 순으로 선택합니다. 양성 예측이 없으면 정밀도를 1로 취급하지 않습니다. 목표를 만족하지 못하면 후보는 null입니다.

```powershell
cd C:\copyright\_detect\_project\ai-service
python -m evaluation.run path/to/dataset.json --split validation --advanced --output reports/validation.json
python -m evaluation.tune path/to/dataset.json reports/validation.json --target-precision 0.98 --output reports/tuning.json
```

모델 캐시는 STEP 21과 동일합니다. 튜닝 자체는 저장된 점수를 사용하므로 모델 로드나 네트워크 호출이 없습니다. 출력 파일은 덮어쓰지 않습니다. 입력 manifest와 이미지 해시, split 누수, 점수 보고서의 manifest 해시, ID 전체 포함 여부, label/category, 모델 메타데이터와 점수 범위를 검사합니다. 해시는 입력 추적 수단이며 점수 생성자의 진위를 증명하지 않습니다.

합성 데이터, 양성 또는 음성 30쌍 미만, 필수 변형/혼동 음성 카테고리 누락은 `review_candidate`를 차단합니다. `diagnostic_candidate`는 소프트웨어 진단용입니다. 30쌍은 최소 선별 규칙이며 통계적 신뢰 보장이 아닙니다. 모든 결과의 `production_ready`는 false입니다.

검토한 실제 validation 데이터로 후보를 고정한 뒤, 독립적인 test 데이터에 `evaluation.run --split test --threshold 선택값`을 한 번 적용해 평가하고 사람이 적용을 판단해야 합니다. test 결과를 보고 같은 test에서 다시 조정하면 안 됩니다. 기본/고급 모드 및 모델 버전이 달라지면 별도 평가가 필요합니다. 출처 그룹을 분리하고 실제 검색 환경의 음성 비율을 반영해야 합니다.

현재 보유한 27쌍은 STEP 21의 합성 test fixture입니다. 이를 validation으로 바꿔 운영 튜닝에 사용하지 않았습니다. 실제 검토 데이터가 없어 운영 임계값은 미조정입니다. 이 도구는 매칭 cutoff만 비교하며 위험도 구간 또는 법적 침해 확률을 보정하지 않습니다. 서버 설정을 자동 변경하지 않습니다.

다음 개발 항목은 STEP 23 신고 워크플로우입니다. 실제 임계값 보정과 운영 인프라 검증은 별도 미완료 항목입니다.
