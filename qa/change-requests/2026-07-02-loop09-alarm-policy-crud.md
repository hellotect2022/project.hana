# 코드 수정 승인 요청서

- 작성일: 2026-07-02
- Loop: 09 (알람 정책 구현 Phase 1+2)
- 작성: loop-orchestrator
- 설계 문서: docs/architecture/2026-07-01-alarm-policy-redesign.md

## 0. 배경 / 결정
- 알람 정책 재설계 문서 확정(평가=hana-event 스트림 / 카테고리=소분류+상위 서브트리 / 레벨=주의·경계·심각 3단 고정).
- 사용자 "만들어 보자" 승인. 백엔드부터 단계적 구현. 본 Loop = **신규 alarm 도메인 엔티티 + CRUD API**(Phase 1+2).

## 1. 범위 (이번 Loop)
- **신규 alarm 도메인만 추가**(com.mpole.hana.digitaltwin.alarm). 기존 event/device 코드·기존 EventRule은 **건드리지 않음**(레거시 정리는 후속 Phase). ddl-auto: update가 신규 테이블 자동 생성(별도 마이그레이션 스크립트 불필요).

## 2. 신규 파일 (spring-backend, hana-digitaltwin)
- 엔티티: `AlarmPolicy`(tbl_alarm_policy), `AlarmPolicyLevel`(tbl_alarm_policy_level, policy 1:N cascade)
- enum: `AlarmScope`(CATEGORY/DEVICE), `AlarmConditionKind`(THRESHOLD/TOGGLE), `AlarmLevel`(CAUTION/WARNING/CRITICAL), `AlarmOperator`(GTE/LTE/GT/LT)
- displayType: 기존 `event.entity.EventDisplayType`(SIMPLE/SOP) 재사용(프론트에서 팝업/팝업+SOP 라벨링)
- repo: `AlarmPolicyRepository`(+level은 cascade)
- DTO: `AlarmPolicyRequest`/`AlarmPolicyResponse`/`AlarmPolicyLevelItem`
- service: `AlarmPolicyService`(create/list/get/update/delete, 레벨 교체, conditionKind 검증)
- controller: `AlarmPolicyController` — `POST/GET/GET{id}/PUT{id}/DELETE{id} /api/alarm/policies`

## 3. 필드 계약 (요약)
- AlarmPolicy: policyName, description, scope, categoryId(nullable), deviceId(nullable), tagName, pointType, conditionKind, operator(THRESHOLD), triggerValue(TOGGLE), displayType, sopTemplateId(nullable), effectEnabled, sustainSec, cooldownSec, active + levels[].
- AlarmPolicyLevel: level, thresholdValue(AI, nullable), icon, effect, displayOrder.
- 검증: THRESHOLD→operator+레벨 임계값 필요, TOGGLE→triggerValue 필요, scope=CATEGORY→categoryId, scope=DEVICE→deviceId.

## 4. 위험도
LOW~MEDIUM
- 전부 신규 additive. 기존 로직 무변경. ddl-auto가 테이블 생성(운영 DB 반영 시점 인지). 소프트 참조(categoryId/deviceId Long)로 결합 최소화.
- 평가엔진/컨슈머/프론트는 후속 Phase(별도 승인).

## 5. 승인
사용자 승인(2026-07-02). 09(엔티티+CRUD) → QA → 이후 Phase 3(hana-event 평가+alarm 토픽) → Phase 4(alarm 소비) → Phase 5(프론트).
