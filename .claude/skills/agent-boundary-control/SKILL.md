# agent-boundary-control

## 목적
각 AGENT가 자기 소유 영역만 수정하도록 통제하고, 타 영역 변경은 요청서로 전달한다.

## 사용 시점
작업 중 다른 AGENT 소유 영역에 수정이 필요하다고 판단될 때.

## 적용 대상
모든 AGENT.

## 소유 영역 요약
| AGENT | 직접 수정 |
|---|---|
| loop-orchestrator | CLAUDE.md, docs/reports/, qa/change-requests/ |
| docs-architect | docs/, README.md |
| qa-code-reviewer | qa/ (검증 전용) |
| integration-architect | docs/integration/, docs/architecture/, docs/reports/ |
| skill-curator | .claude/skills/, docs/reports/ |
| spring-backend-agent | hana-*/src/ |
| database-agent | **/resources/db/, hana-digitaltwin/scripts/ |
| devops-agent | 빌드/yml/배포 (승인 게이트) |
| react-frontend-agent | 05-06.../src/ |

## 절차
1. 수정 대상 파일의 소유 AGENT를 확인한다.
2. 내 영역이면 `approval-gate` 절차로 진행한다.
3. 타 영역이면 **직접 수정하지 않고** `report-writing`의 "AGENT 변경 요청서"를
   `qa/change-requests/`에 작성한다.
4. 소유가 불명확하면 수정하지 말고 사용자에게 영역 분리안을 보고한다.

## 체크리스트
- [ ] 대상 파일 소유 AGENT 확인
- [ ] 타 영역은 변경 요청서로 전달
- [ ] 불명확 시 사용자 보고

## 금지 사항
- 타 AGENT 영역 직접 수정
- 소유 불명확 영역 임의 수정

## 출력 형식
내 영역: 승인 요청서. 타 영역: AGENT 변경 요청서.
