# Loop 09 작업 보고서 — 알람 정책 CRUD (Phase 1+2)

- 작성일: 2026-07-02
- 담당 AGENT: spring-backend-agent
- 대상 모듈: hana-digitaltwin (WebMVC / JPA)
- 설계 문서: docs/architecture/2026-07-01-alarm-policy-redesign.md
- 승인 요청서: qa/change-requests/2026-07-02-loop09-alarm-policy-crud.md

## 1. 작업 개요

신규 패키지 `com.mpole.hana.digitaltwin.alarm` 하위에 알람 정책(AlarmPolicy) 및 심각도
레벨(AlarmPolicyLevel)의 CRUD 백엔드를 신규 구현했다. 기존 event/device 도메인 코드,
EventRule, yml, build.gradle 은 일절 수정하지 않았다. `EventDisplayType`(SIMPLE/SOP)만
읽기 참조로 재사용했다.

## 2. 변경(신규 생성) 파일 목록

엔티티 / 열거형 (entity/)
- alarm/entity/AlarmScope.java — CATEGORY, DEVICE
- alarm/entity/AlarmConditionKind.java — THRESHOLD, TOGGLE
- alarm/entity/AlarmLevel.java — CAUTION, WARNING, CRITICAL
- alarm/entity/AlarmOperator.java — GTE/LTE/GT/LT (symbol 필드 + getSymbol())
- alarm/entity/AlarmPolicy.java — @Entity tbl_alarm_policy, DateEntity 상속, levels @OneToMany(cascade ALL, orphanRemoval)
- alarm/entity/AlarmPolicyLevel.java — @Entity tbl_alarm_policy_level, DateEntity 상속, policy @ManyToOne(LAZY)

리포지토리 (repository/)
- alarm/repository/AlarmPolicyRepository.java — JpaRepository<AlarmPolicy, Long>
  - findAllByOrderByPolicyIdDesc, findByScopeOrderByPolicyIdDesc, findByActiveTrueOrderByPolicyIdDesc

DTO (dto/) — record + @Builder
- alarm/dto/AlarmPolicyLevelItem.java
- alarm/dto/AlarmPolicyRequest.java
- alarm/dto/AlarmPolicyResponse.java (static from(AlarmPolicy))

서비스 (service/)
- alarm/service/AlarmPolicyService.java — @Service @Transactional, create/list/get/update/delete + 검증

컨트롤러 (api/)
- alarm/api/AlarmPolicyController.java — @RestController /api/alarm/policies

총 11개 파일 신규 생성. 기존 파일 수정 0건.

## 3. 엔드포인트 계약 요약

베이스: `/api/alarm/policies` — 모든 응답은 `ApiResponse<T>` 래핑.

| 메서드 | 경로 | 요청 | 응답 data |
|---|---|---|---|
| POST | `/api/alarm/policies` | AlarmPolicyRequest | AlarmPolicyResponse |
| GET | `/api/alarm/policies` | - | List<AlarmPolicyResponse> (policyId desc) |
| GET | `/api/alarm/policies/{id}` | - | AlarmPolicyResponse |
| PUT | `/api/alarm/policies/{id}` | AlarmPolicyRequest | AlarmPolicyResponse (레벨 전체 교체) |
| DELETE | `/api/alarm/policies/{id}` | - | Void |

### AlarmPolicyRequest
policyName, description, scope(CATEGORY|DEVICE), categoryId, deviceId, tagName, pointType,
conditionKind(THRESHOLD|TOGGLE), operator(GTE|LTE|GT|LT), triggerValue, displayType(SIMPLE|SOP),
sopTemplateId, effectEnabled, sustainSec, cooldownSec, active, levels[]

### AlarmPolicyLevelItem
level(CAUTION|WARNING|CRITICAL), thresholdValue(Double), icon, effect, displayOrder(int)

### AlarmPolicyResponse
policyId + 위 요청 필드 전체 + levels[] + createdAt/updatedAt

## 4. 검증 규칙 (위반 시 IllegalArgumentException → GlobalExceptionHandler 400 INVALID_REQUEST)

- policyName 필수(공백 불가)
- scope 필수
- scope=CATEGORY → categoryId 필수
- scope=DEVICE → deviceId 필수
- conditionKind 필수
- conditionKind=THRESHOLD → operator 필수 AND 레벨 중 최소 1개 thresholdValue 존재
- conditionKind=TOGGLE → triggerValue 필수(공백 불가)
- 각 레벨 항목 level 값 필수

update/get/delete 시 미존재 id → IllegalArgumentException("알람 정책을 찾을 수 없습니다").

## 5. 기술 판단 근거

- 블로킹(JPA/WebMVC) 모듈에만 구현 — 리액티브 혼용 없음.
- DateEntity(@MappedSuperclass, created_at/updated_at) 상속으로 감사 컬럼 일관 처리.
- category/device/sopTemplate 는 설계대로 소프트 참조(FK 없이 id Long 보관) — 레거시
  정리/실제 조인은 후속 Phase.
- 레벨은 cascade ALL + orphanRemoval, update 시 clearLevels() 후 재구성으로 전체 교체.
- displayType 기본 SIMPLE, active 기본 true(요청 null 시 서비스에서 보정).
- record DTO + @Builder, ApiResponse.success 래핑, @Operation/@Tag 등 기존 컨벤션 준수.

## 6. 빌드 검증

`./gradlew :hana-digitaltwin:compileJava -q` — 성공(오류 없음).
(IDE 인덱싱 순서로 인한 일시적 진단은 컴파일 성공으로 해소됨.)

## 7. 다른 AGENT 영향 / 후속 Phase

- DB(database-agent): ddl-auto=update 로 `tbl_alarm_policy`, `tbl_alarm_policy_level` 이
  기동 시 자동 생성됨. 별도 마이그레이션 스크립트 미작성.
- Phase 3 (hana-event): 리액티브 평가엔진이 이 정책/레벨을 R2DBC로 읽어 조건 평가.
  (동일 테이블을 R2DBC 매핑으로 별도 read 모델 정의 예정 — JPA 엔티티 직접 재사용 아님.)
- Phase 4: alarm 소비 → EventInstance 생성 연동.
- Phase 5 (react-frontend-agent): 정책 폼에서 tagName 은 카테고리 schemaDefinitions 기반
  선택, pointType 은 스키마에서 자동 결정.

## 8. QA 요청 항목

- 신규 패키지 범위 준수 여부(기존 코드 무변경) 확인
- 검증 규칙 분기(THRESHOLD/TOGGLE, CATEGORY/DEVICE) 정합성
- 레벨 전체 교체(update) 시 orphanRemoval 동작 및 트랜잭션 경계
- ApiResponse 래핑/네이밍/컨벤션 일치
- 컴파일 통과 재확인
