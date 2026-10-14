# DORA Dashboard

`reports/dora/latest.json`을 읽어 DORA 4대 지표와 주간 추이를 표시하는 정적 Chart.js 대시보드입니다.

저장소 루트에서 다음 명령을 실행한 뒤 `http://localhost:8000/dashboard/`로 접속합니다.

```bash
python -m http.server 8000
```

다른 JSON을 확인하려면 쿼리 문자열로 경로를 전달할 수 있습니다.

```text
http://localhost:8000/dashboard/?data=../reports/dora/latest.json
```

로컬 파일을 브라우저에서 직접 열면 `fetch()` 보안 정책으로 JSON 로딩이 차단될 수 있으므로 HTTP 서버를 사용해야 합니다.

## 검증

```bash
npm test
```

대시보드는 같은 origin의 `.json` 파일만 `?data=` 입력으로 허용합니다. JSON 스키마, 수치 범위, 주차 정렬과 차트 변환 로직은 Node.js 테스트로 검증합니다.
