# 지원 운영 자동화 초안

## 결정 사항 및 배경

Issue 접수 누락을 줄이고 첫 응답 SLA와 주간 흐름을 같은 기준으로 확인하기 위해 GitHub Actions 기반 초안을 둡니다. 실제 운영 전 알림 문구, 24시간 기준, 담당자와 휴일 정책을 팀이 승인해야 합니다.

## 아키텍처 설계

```text
support 라벨 부여 ──> 자동 접수 댓글 + sla:active 라벨
                    │
6시간 Schedule ────┴──> 첫 팀 응답 검사 ──> sla:breached 라벨
                    │
월요일 Schedule ───┴──> 지난 7일 통계 ──> 주간 요약 Issue
```

워크플로우는 최소 권한인 `issues: write`, `contents: read`만 사용합니다. 저장소 Secret을 읽거나 출력하지 않습니다.

## API 명세

GitHub REST API의 Issue, Comment, Label 인터페이스만 사용합니다.

| 입력/상태          | 처리                                   | 출력                                     |
| ------------------ | -------------------------------------- | ---------------------------------------- |
| support 라벨 Issue | 접수 안내, 중복 댓글 방지              | 댓글, `sla:active`                       |
| 열린 support Issue | OWNER/MEMBER/COLLABORATOR 첫 댓글 확인 | 활성 라벨 제거 또는 위반 라벨 추가       |
| 최근 7일 Issue     | 생성·종료·SLA 위반 집계                | `지원 운영 주간 요약 · YYYY-MM-DD` Issue |

SLA 시계는 Issue 생성 시각부터 기본 24시간이며 봇 댓글은 첫 팀 응답으로 세지 않습니다. 저장소 변수 `SUPPORT_SLA_HOURS`에 1~720 사이의 시간을 지정할 수 있고, 유효하지 않은 값은 24시간으로 대체합니다.

## 트러블슈팅 가이드

- 권한 오류: 저장소 Actions의 Workflow permissions와 워크플로우 `permissions`를 확인합니다.
- 라벨 오류: 워크플로우가 라벨을 만들 권한이 있는지 확인합니다.
- 중복 요약: 같은 날짜 제목을 조회하는 멱등성 검사가 동작하는지 확인합니다.
- 잘못된 SLA 위반: 응답자의 `author_association`과 Issue 생성 시각을 확인합니다.

민감정보가 포함된 Issue는 자동화 문제가 아니라 공개 범위 문제이므로 즉시 내용을 정리하고 저장소 보안 절차를 따릅니다.

## 회의 노트 및 액션 항목

- [ ] SLA 24시간과 운영 시간·휴일 포함 여부 승인
- [ ] 자동 응답 문구와 담당 채널 승인
- [ ] 2주간 시험 운영 후 오탐·누락 검토
- [ ] Discussions RFC 운영 방식 확정

## FAQ & Onboarding

**왜 Issue로 주간 요약을 만드나요?** 별도 외부 서비스나 Secret 없이 검색·라벨·마일스톤과 연결할 수 있기 때문입니다.

**자동 댓글이 SLA 응답인가요?** 아닙니다. 팀 권한이 있는 사람의 첫 댓글만 응답으로 계산합니다.

**바로 운영해도 되나요?** 초안이므로 PR 검토와 정책 승인을 거친 뒤 기본 브랜치에 병합해야 Schedule과 Issue 이벤트가 활성화됩니다.

## 관련 문서

- [ADR 목록](adr/README.md)
- [Wiki Troubleshooting](wiki/Troubleshooting.md)
- [리뷰 가이드](REVIEW_GUIDE.md)
