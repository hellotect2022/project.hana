# QA 검증 보고서 — Loop 09 알람 정책 CRUD (Phase 1+2)

- 작성일: 2026-07-02
- 검증 AGENT: qa-code-reviewer
- 대상 모듈: hana-digitaltwin (WebMVC / JPA), 신규 패키지 `com.mpole.hana.digitaltwin.alarm`
- 근거 문서
  - 설계: docs/architecture/2026-07-01-alarm-policy-redesign.md
  - 승인 요청서: qa/change-requests/2026-07-02-loop09-alarm-policy-crud.md
  - 작업 보고서: docs/reports/2026-07-02-loop09-alarm-crud-report.md

## 전체 판정: PASS (WARNING 후속 항목 있음)

컴파일 통과, 범위 준수(alarm 도메인 추가만, 기존 event/device/EventRule/yml 무변경),
엔티티 매핑·검증 분기·update orphanRemoval·컨벤션 모두 설계 계약과 일치. 다음 Phase(hana-event
평가엔진) 진행 가능. 다만 운영 반영 및 정합성 관련 후속 항목(비차단)을 명시한다.

---

## 항목별 결과

### 1. 범위 준수 (최중요) — PASS

- 신규 alarm 파일: 실제 12개 확인. (엔티티 6 + repo 1 + dto 3 + service 1 + controller 1)
  - entity: AlarmScope, AlarmConditionKind, AlarmLevel, AlarmOperator, AlarmPolicy, AlarmPolicyLevel
  - repository: AlarmPolicyRepository / dto: AlarmPolicyLevelItem, AlarmPolicyRequest, AlarmPolicyResponse
  - service: AlarmPolicyService / api: AlarmPolicyController
  - 근거: `hana-digitaltwin/.../alarm/**` (find 결과 12파일)
  - 참고: 요청서·작업보고서·본 요청은 "11파일"로 표기하나 실제 신규는 12파일(enum 4개 중 하나 누락 표기 추정). 코드상 누락/과잉 없이 계약 필드 전부 구현되어 있어 기능적 문제 아님. **문서상 개수 표기만 정정 권장.**
- 기존 코드 무변경 확인(alarm 관련):
  - `event/`, `device/` 하위: alarm 관련 수정 없음. git diff --stat 상 event/device 변경은 alarm과 무관.
  - EventRule/EventRuleService/EventRuleController/EventRuleRepository: 무변경(설계 §E의 "대체/재작성"은 후속 Phase로 미착수 — 승인 범위와 일치).
  - EventDisplayType: 읽기 재사용만(`AlarmPolicy`, `AlarmPolicyRequest/Response`, `AlarmPolicyService`에서 import 후 참조). 파일 자체 무변경.
- 작업 트리 내 기타 변경 파일(build.gradle, SecurityConfig, SnapshotService, device/location/unityAsset 등)은 **Loop 09와 무관한 선행 작업 잔재**임을 diff로 확인:
  - `build.gradle`: `org.apache.poi:poi-ooxml` 추가(포인트 매핑 엑셀 기능) — alarm 무관.
  - `SecurityConfig`: `/api/monitor/**` ADMIN 제한 추가 — alarm 무관.
  - 따라서 "build.gradle/yml 무변경(alarm 기준)" 조건은 충족. (단, 이 선행 잔재들은 Loop 09 커밋에 섞이지 않도록 분리 커밋 권장 — 후속 항목 참조.)
- 판정 근거: `git status`, `git diff --stat`, `git diff -- build.gradle SecurityConfig.java`.

### 2. 엔티티 매핑 — PASS

- `AlarmPolicy extends DateEntity`(`AlarmPolicy.java:24`), `@Table("tbl_alarm_policy")`.
  - `AlarmPolicyLevel extends DateEntity`(`AlarmPolicyLevel.java:19`), `@Table("tbl_alarm_policy_level")`.
- `levels` `@OneToMany(mappedBy="policy", cascade=ALL, orphanRemoval=true)` + `@Builder.Default = new ArrayList<>()` (`AlarmPolicy.java:86-88`). 정상.
- `@ManyToOne(fetch=LAZY) @JoinColumn(name="policy_id")` (`AlarmPolicyLevel.java:26-28`). 양방향 매핑 정확.
- enum 전부 `@Enumerated(EnumType.STRING)`: scope/conditionKind/operator/displayType(policy), level(levelEntity). 근거 `AlarmPolicy.java:37,53,57,63`, `AlarmPolicyLevel.java:30`.
- 소프트 참조: categoryId/deviceId/sopTemplateId를 `Long` 컬럼으로만 보관(FK 없음). 근거 `AlarmPolicy.java:41-45,68-69`. 설계 §A/§계약과 일치.
- `@Builder.Default` 적용: displayType(SIMPLE), effectEnabled(false), sustainSec(0), cooldownSec(0), active(true), levels(빈 리스트). 빌더 사용 시 default 소실 방지 정확.
- ddl-auto=update 자동 생성 적합성:
  - PK `GenerationType.IDENTITY` — Postgres BIGSERIAL/identity에 정합(`application.yml` ddl-auto=update 확인).
  - 순환/자기참조 없음. FK는 level→policy 단방향 1개뿐이라 제약 충돌 없음.
  - 기존 EventInstance/EventRule과 동일 패턴(extends DateEntity, IDENTITY, LAZY ManyToOne) — 동일 방식으로 무리 없이 테이블 생성됨.
  - 판정: 자동 생성 구조상 문제 없음. (단 설계 §A의 CHECK 제약/UNIQUE(policy_id,level)/조회 인덱스는 ddl-auto가 생성하지 않음 — 후속 항목.)

### 3. 검증 분기 정합성 — PASS

`AlarmPolicyService.validate()`(`:124-157`)가 설계 계약과 일치:
- policyName 필수(공백 불가) `:125-127`
- scope 필수 `:128-130`
- scope=CATEGORY → categoryId 필수 `:131-133`
- scope=DEVICE → deviceId 필수 `:134-136`
- conditionKind 필수 `:137-139`
- THRESHOLD → operator 필수 AND 레벨 중 thresholdValue 최소 1개 `:140-149`
- TOGGLE → triggerValue 필수(공백 불가) `:150-153`
- 각 레벨 항목 level 필수 `:154-156`
- 위반 → `IllegalArgumentException` → GlobalExceptionHandler `:61-67`에서 400 INVALID_REQUEST. 확인 완료.
- create/update 양쪽 진입부에서 validate 호출(`:29`, `:70`). 일관.

### 4. update orphanRemoval / 트랜잭션 — PASS (경미 주의)

- update: `policy.clearLevels()`(`:91`) 후 `applyLevels()`(`:92`)로 재구성 → orphanRemoval=true로 기존 레벨 delete, 신규 insert. 관리 엔티티(findOrThrow로 로드)에 대한 컬렉션 조작이라 dirty checking으로 반영됨.
- 양방향 동기화: `applyLevels`→`policy.addLevel(level)`이 `level.setPolicy(this)` 설정(`AlarmPolicy.java:90-93`). FK 세팅 정확.
- 트랜잭션 경계: 클래스 `@Transactional`(`:23`), 조회는 `@Transactional(readOnly=true)`(list/get). create/update/delete 쓰기 트랜잭션. 적절.
- 경미 주의(비차단): 동일 트랜잭션에서 `clear()`(orphan delete) 후 동일 level 값으로 재삽입 시, DB에 UNIQUE(policy_id, level) 제약이 존재하면 delete-before-insert 순서(Hibernate flush 순서상 insert가 먼저 실행될 수 있음)로 제약 위반 가능성이 이론상 존재. 현재 ddl-auto는 해당 UNIQUE를 생성하지 않으므로 지금은 발생하지 않으나, 후속에 UNIQUE 도입 시 재검증 필요(후속 항목).

### 5. 컨벤션 — PASS

- DTO: record + `@Builder` (Request/Response/LevelItem 모두). ApiResponse 래핑(`AlarmPolicyController` 전 메서드 `ApiResponse.success`).
- 컨트롤러 경로 `/api/alarm/policies`, POST/GET/GET{id}/PUT{id}/DELETE{id} + `@Operation`/`@Tag`/`@Parameter` 부착. 설계·작업보고서 계약과 일치.
- `AlarmPolicyResponse.from()`(`:36-71`): 정책 전 필드 + levels 매핑 + createdAt/updatedAt(DateEntity getter, OffsetDateTime) 정확. null-safe(`p==null → null`).
- 서비스 default 보정(displayType null→SIMPLE, active null→TRUE) create/update 동일 적용.
- 기존 EventInstance/ApiResponse/DateEntity/EventDisplayType 등 참조 대상 시그니처와 정합(getCreatedAt/getUpdatedAt는 DateEntity `@Data` 제공).

### 6. 컴파일 — PASS

- `./gradlew :hana-digitaltwin:compileJava -q` → EXIT 0, 오류/경고 없음. 작업보고서의 "IDE 일시 진단"은 실제 컴파일로 해소됨을 재확인.

---

## 다음 Phase 진행 가능 여부

**진행 가능 (PASS).** Phase 3(hana-event 리액티브 평가엔진 + 정책 R2DBC 읽기 캐시 + alarm 토픽 emit)
착수 가능. 단 아래 후속 항목을 Phase 3 착수 전/중 반영 권장.

## 후속 항목 (비차단)

1. **ddl-auto 운영 반영 / 명시적 마이그레이션 필요성 (권장 강도: 중)**
   - 현재 tbl_alarm_policy(+level)은 ddl-auto=update로만 생성됨. 설계 §A의 CHECK(ck_alarm_policy_scope),
     UNIQUE(policy_id, level), 조회 인덱스((scope,category_id,tag_name) 등), active 부분 인덱스는 자동 생성되지 않는다.
   - 운영 DB 정합·성능(평가 캐시 룩업)을 위해 database-agent가 `db/migration` 명시적 스크립트로 제약/인덱스를 확정할 것을 권장. (기존 V1~V3 마이그레이션 관례 존재.)

2. **레거시 EventRule 정리 시점 (권장 강도: 중)**
   - 설계 §E는 EventRule/EventRuleService/Controller/Repository/DTO를 alarm으로 대체(재작성/삭제)하도록 규정하나 이번 Loop 미착수(승인 범위 밖, 정상).
   - 특히 `DeviceCategoryCascadeService`의 `eventRuleRepository.deleteByDeviceId(In)` 참조(설계 §E)가 남아 있어, alarm 정책 도입 후 장비/카테고리 삭제-정책 연동 정책과 충돌하지 않도록 별도 변경 요청서로 정리 시점을 명시할 것.

3. **소프트 참조 정합성 검증 위치 (권장 강도: 중)**
   - categoryId/deviceId/sopTemplateId가 FK 없는 Long이라 존재하지 않는 id도 저장 가능. 현재 서비스는 존재 검증을 하지 않음(설계상 소프트 참조로 의도됨).
   - 정합성 검증 위치를 확정 필요: (a) 정책 저장 시 digitaltwin에서 category/device/sopTemplate 존재 검증 추가할지, (b) 평가 시점(hana-event) 매칭 실패로 무시할지. displayType=SOP인데 sopTemplateId null 검증도 현재 없음(설계 §A "POPUP_SOP시 필수") — Phase 4(alarm 소비/ SOP 연동) 전 결정 권장.

4. **문서 파일 개수 표기 정정 (권장 강도: 하)**
   - 승인 요청서/작업 보고서/QA 요청의 "신규 11파일" 표기를 실제 "12파일"로 정정(docs-architect/loop-orchestrator 소관).

5. **선행 미커밋 변경 분리 (권장 강도: 하)**
   - 작업 트리에 alarm과 무관한 선행 변경(build.gradle POI, SecurityConfig monitor, device/location/unityAsset/hana-event 등)이 섞여 있음. Loop 09 커밋 시 alarm 파일만 분리 커밋하여 범위 추적성 확보 권장.

## 재검증 필요 여부

- 이번 Loop 결과물에 대한 재검증: **불필요**(PASS).
- 후속 항목 1(마이그레이션 스크립트) 및 3(정합성 검증)이 실제 코드/스키마로 반영되면 그 변경에 대한 재검증 필요.

## 수정 요청 대상 AGENT

- 이번 Loop 결과물에 대한 필수 수정: **없음**.
- 후속 항목 담당(신규 Loop/변경 요청서로 진행):
  - 후속 1: database-agent (마이그레이션 제약/인덱스)
  - 후속 2: spring-backend-agent (EventRule 정리 + DeviceCategoryCascadeService 참조 정리, 변경 요청서 필요)
  - 후속 3: spring-backend-agent (정합성 검증 위치 확정)
  - 후속 4/5: docs-architect / loop-orchestrator (문서 정정·커밋 분리)
