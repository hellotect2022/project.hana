# 알람 정책(Alarm Policy) 도메인 재설계 (설계안)

- 문서 유형: 아키텍처 설계안 (integration-architect)
- 작성일: 2026-07-01
- 대상 모듈: `hana-common` · `hana-gateway` · `hana-event` · `hana-digitaltwin` + 프론트(`05-06.hanadream_web_new`)
- 상태: **설계안(Draft)** — 구현 아님. 이 문서는 설계안이며 실제 구현/스키마 변경은 승인 기반 Bounded Loop로 진행한다(§9).

> 본 문서는 코드 근거를 `경로:라인`으로 명시한다. DDL은 확정 스키마가 아닌 **스케치**이며,
> 승인 후 database-agent가 마이그레이션으로 확정한다.

---

## 0. 핵심 설계 원칙 (서두)

1. **카테고리 정책은 규칙 1건만 저장하고, 평가 시점에 tagName으로 런타임 매칭한다.**
   장비별로 규칙을 복제하지 않는다. 장비 수와 무관하게 `(categoryId, tagName)` 인덱스 룩업으로
   확장한다. 장비가 매우 많은 환경에서 개별 디바이스 정책 등록이 비현실적이라는 요구를 흡수한다.

2. **pointType으로 조건 종류를 자동 분기한다.** `PointType`(hana-common) 기준으로
   `AI/AO/AV`=아날로그 → **THRESHOLD**(≥/≤/>/< 임계값), `BI/BO/BV/DI/DO/DV`=바이너리 →
   **TOGGLE**(on/off), `MSI/MSO/MSV`=멀티스테이트, `FUNCTION`=계산값.
   근거: `hana-common/src/main/java/com/mpole/hana/common/constant/PointType.java:3-24`.

3. **소분류 `schemaDefinitions`가 tagName·pointType의 원천이다.**
   `DeviceCategory.schemaDefinitions`(List<`PointSchemaDefinition`>) → 정책 폼의 tagName 드롭다운과
   조건종류 자동판별의 근거로 재사용한다.
   근거: `hana-digitaltwin/.../device/entity/DeviceCategory.java:72-77`,
   `hana-digitaltwin/.../device/schema/PointSchemaDefinition.java:5-11`.

4. **평가는 hana-event 실시간 스트림에서 수행한다.** gateway가 raw 토픽으로 발행 → hana-event가
   정책 평가 → alarm 토픽 emit → digitaltwin이 소비하여 EventInstance/SOP/이펙트로 확정.

---

## 1. 배경 / 요구사항 요약

- 장비가 매우 많아 개별 설정이 비현실적 → **카테고리 소분류 기준 + 특정 포인트(tagName) 기준
  임계값 정책**으로 하위 장비를 일괄 커버. 개별 디바이스 정책도 지원.
- `point_type`이 AI(아날로그)면 임계값(≥/≤), DI/바이너리면 on/off 토글 기준.
- `device_key = 대_중_소_디바이스이름`, `point_key = device_key_tagName`.
  근거: `hana-digitaltwin/.../device/util/DeviceKeyUtils.java:23-46`.
- 시안(WA-ALARM-POLICY): 정책 목록(정책명/대상/레벨/발생조건/경보표시/이펙트/활성) + 레벨별 상세
  (주의/경계/심각 각 임계값·아이콘·이펙트).
- `event` 디렉토리 하위는 보류 로직 → 삭제/재작성 가능(§E).

### 사용자 확정 결정
1. 평가 위치: **hana-event 실시간 스트림**(kafka raw 구독 → 정책 평가 → alarm 토픽 emit →
   digitaltwin 저장/알림).
2. 카테고리 정책 대상 깊이: **소분류 + 상위(대/중 선택 시 서브트리 소분류 전개)**.
   `resolveSmallCategoryIdsForFilter` 재활용.
3. 레벨: **주의/경계/심각 3단계 고정** enum. 레벨별 임계값·아이콘·이펙트.

---

## 2. 현재 구조 대비 (코드 근거 확인 결과)

검토 중 확인된 **사양 대비 실제 코드 차이**(문서 반영):

| 항목 | 사용자 지시/시안 | 실제 코드 | 처리 |
|---|---|---|---|
| DisplayType 값 | `POPUP` / `POPUP_SOP` | `SIMPLE` / `SOP` | 신규 `display_type`은 `POPUP/POPUP_SOP` 도입 제안, 기존 `EventDisplayType`(SIMPLE/SOP)와 **매핑** 필요(§E, §리스크) |
| DevicePoint.pointType 타입 | enum(PointType) | `String pointType` (`DevicePoint.java:60-61`) | 정책 폼은 schema의 `type`/pointType 문자열을 `PointType`으로 해석. 파싱 규칙 명시(§C) |
| raw 토픽명 | `hdt.kafka.topic.raw` | 기본값 `test`, event yml 실제값 `test2` | 값 자체는 설정(devops), 계약상 키는 `hdt.kafka.topic.raw` 유지 |
| digitaltwin Kafka/AMQP consumer | 존재 가정 | **현재 없음**(코드상 KafkaListener/RabbitListener 미존재) | alarm 소비 consumer 신규 구현 필요(§C, 리스크) |
| hana-event 평가 로직 | 정책 평가 | 현재는 `alarmYn=Y && valueRaw=1` 하드코딩 필터(`EventService.java:33-45`) | 정책 기반 평가엔진으로 대체(§E) |

> 위 차이는 구현 단계에서 spring-backend-agent / devops-agent가 승인 하에 해소한다.

---

## 3. 연결 구조도 (텍스트 다이어그램)

```
[SI/현장]                                  raw 토픽 (hdt.kafka.topic.raw, DeviceBundle)
   │                                                 │
   ▼                                                 ▼
┌──────────────┐   DeviceBundle    ┌───────────────────────────────┐
│ hana-gateway │ ────────────────▶ │   Kafka: raw (DeviceBundle)   │
│ WebFlux/R2DBC│  KafkaMessage     └───────────────┬───────────────┘
│              │  Publisher.publish                │ 구독 (KafkaReceiver)
└──────────────┘                                   ▼
                                    ┌───────────────────────────────┐
                                    │           hana-event           │
                                    │  InitKafkaConsumer → Queue     │
                                    │  ── AlarmEvaluationEngine ──   │  ◀── 정책+레벨 캐시
                                    │  (THRESHOLD / TOGGLE 평가)     │     (R2DBC 공유 Postgres)
                                    │  sustain·cooldown in-memory    │
                                    └───────────────┬───────────────┘
                                                    │ AlarmEvent emit
                                                    ▼
                                    ┌───────────────────────────────┐
                                    │   Kafka: alarm (AlarmEvent)   │  ◀── 신규 토픽
                                    └───────────────┬───────────────┘
                                                    │ 구독 (신규 consumer)
                                                    ▼
                        ┌──────────────────────────────────────────────┐
                        │                hana-digitaltwin                │
                        │  AlarmEventConsumer                            │
                        │   → EventInstanceService.create()  (JPA)      │
                        │   → SopRuntimeService (SOP 인스턴스)          │
                        │   → realtime/AMQP 3D 이펙트 푸시              │
                        │   → (후속) SMS                                 │
                        │                                                │
                        │  AlarmPolicy CRUD (정책/레벨) ──┐             │
                        │  폼지원 조회(카테고리 schema) ──┼─▶ 프론트   │
                        └─────────────────────────────────┼────────────┘
                                                          │ 정책 R2DBC 캐시 로드
                                                          ▼ (공유 Postgres)
                                    tbl_alarm_policy / tbl_alarm_policy_level
```

핵심 결합점:
- **raw 토픽**: `DeviceBundle`(deviceId, deviceKey, timestamp, points<XnPointData>) — 근거
  `hana-common/.../dto/DeviceBundle.java:9-14`, `XnPointData.java:8-21`.
  gateway 발행: `KafkaMessagePublisher.java:21-42`, event 구독: `KafkaConfig.java:48-59` +
  `InitKafkaConsumer.java:32-91`.
- **alarm 토픽**(신규): `AlarmEvent`(policyId, deviceId, pointId, tagName, value, level, occurredAt …).
- **공유 Postgres**: `tbl_alarm_policy(+level)`를 digitaltwin(JPA)가 소유·CRUD, hana-event(R2DBC)가
  읽기 전용 캐시로 소비.

---

## A. 데이터 모델 (DDL 스케치 + 필드표)

> DDL은 스케치 수준. 확정은 database-agent 마이그레이션에서.

### A-1. tbl_alarm_policy (정책 헤더)

```sql
-- 스케치 (Postgres)
CREATE TABLE tbl_alarm_policy (
    policy_id        BIGSERIAL PRIMARY KEY,
    policy_name      VARCHAR(200) NOT NULL,
    description      TEXT,
    scope            VARCHAR(16)  NOT NULL,          -- CATEGORY | DEVICE
    category_id      BIGINT,                          -- scope=CATEGORY (nullable FK → tbl_device_category)
    device_id        BIGINT,                          -- scope=DEVICE   (nullable FK → tbl_device)
    tag_name         VARCHAR(200) NOT NULL,           -- schema tagName
    point_type       VARCHAR(16)  NOT NULL,           -- PointType (AI/DI/...)
    condition_kind   VARCHAR(16)  NOT NULL,           -- THRESHOLD | TOGGLE (point_type로 자동결정)
    operator         VARCHAR(4),                      -- >=,<=,>,< (THRESHOLD)
    trigger_value    VARCHAR(8),                      -- 0/1 (TOGGLE)
    display_type     VARCHAR(16)  NOT NULL DEFAULT 'POPUP', -- POPUP | POPUP_SOP
    sop_template_id  BIGINT,                          -- nullable FK → tbl_sop_template
    effect_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    sustain_sec      INT NOT NULL DEFAULT 0,          -- N초 지속시 발생
    cooldown_sec     INT NOT NULL DEFAULT 0,          -- M초 재발방지
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ,
    CONSTRAINT ck_alarm_policy_scope
        CHECK ( (scope='CATEGORY' AND category_id IS NOT NULL AND device_id IS NULL)
             OR (scope='DEVICE'   AND device_id  IS NOT NULL AND category_id IS NULL) )
);
```

| 필드 | 타입 | 설명 | 비고 |
|---|---|---|---|
| policy_id | BIGINT PK | 정책 ID | |
| policy_name | VARCHAR | 정책명 | 시안 "정책명" |
| description | TEXT | 설명 | |
| scope | ENUM | CATEGORY \| DEVICE | 대상 유형 |
| category_id | FK? | 대/중/소 카테고리 ID | scope=CATEGORY. 상위 선택 시 평가시 소분류 전개 |
| device_id | FK? | 개별 장비 ID | scope=DEVICE |
| tag_name | VARCHAR | 대상 포인트 태그명 | schema tagName 근거 |
| point_type | VARCHAR | PointType | 조건종류 자동분기 원천 |
| condition_kind | ENUM | THRESHOLD \| TOGGLE | point_type로 자동결정 |
| operator | VARCHAR | >=,<=,>,< | THRESHOLD 전용 |
| trigger_value | VARCHAR | 0 \| 1 | TOGGLE 전용 |
| display_type | ENUM | POPUP \| POPUP_SOP | 경보 표시 방식 |
| sop_template_id | FK? | SOP 템플릿 | POPUP_SOP시 필수 |
| effect_enabled | BOOL | 3D 이펙트 사용 | |
| sustain_sec | INT | 지속 시간(초) | |
| cooldown_sec | INT | 재발 방지(초) | |
| active | BOOL | 활성 | |

### A-2. tbl_alarm_policy_level (레벨 상세, policy 1:N)

```sql
CREATE TABLE tbl_alarm_policy_level (
    level_id        BIGSERIAL PRIMARY KEY,
    policy_id       BIGINT NOT NULL REFERENCES tbl_alarm_policy(policy_id) ON DELETE CASCADE,
    level           VARCHAR(16) NOT NULL,       -- CAUTION | WARNING | CRITICAL (주의/경계/심각)
    threshold_value NUMERIC,                     -- AI(THRESHOLD) 임계값. TOGGLE은 NULL
    icon            VARCHAR(100),                -- 아이콘 식별자
    effect          VARCHAR(100),               -- 3D 이펙트 식별자
    display_order   INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_policy_level UNIQUE (policy_id, level)
);
```

| 필드 | 타입 | 설명 |
|---|---|---|
| level_id | BIGINT PK | 레벨 ID |
| policy_id | FK | 소속 정책 |
| level | ENUM(고정3) | CAUTION(주의) / WARNING(경계) / CRITICAL(심각) |
| threshold_value | NUMERIC | AI 임계값 (operator와 결합해 판정). TOGGLE은 단일 레벨·NULL 통상 |
| icon | VARCHAR | 레벨별 아이콘 |
| effect | VARCHAR | 레벨별 3D 이펙트 |
| display_order | INT | 표시 순서 |

> 규칙: THRESHOLD 정책은 레벨별 `threshold_value`를 두고 최고 심각도를 산출.
> TOGGLE 정책은 통상 **단일 레벨**만 구성(on/off 발생 → 지정 레벨 alarm).

### A-3. 재사용 자산 (변경 없음)

| 자산 | 근거 | 역할 |
|---|---|---|
| `tbl_event_instance` / `EventInstance` | `event/entity/EventInstance.java:12-78` | 발생 이력 저장(재사용). `rule_id` FK는 정책 대체에 맞춰 재정의 필요(§E) |
| `EventInstanceStatus` | `event/entity/EventInstanceStatus.java:3-8` | OPEN/ACK/IN_PROGRESS/CLOSED (유지) |
| `EventDisplayType` | `event/entity/EventDisplayType.java:3-6` | 현재 SIMPLE/SOP → POPUP/POPUP_SOP 매핑/확장 검토(§E) |
| SOP(`SopTemplate`/`SopInstance`) | `sop/entity/SopTemplate.java:24`, `sop/entity/SopInstance.java` | SOP 연계(재사용) |

### A-4. 인덱스/제약 제안

- 조회 인덱스: `(scope, category_id, tag_name)`, `(scope, device_id, tag_name)`.
- `active` 부분 인덱스: `WHERE active = TRUE` (평가 캐시 로드 대상 최소화).
- `ck_alarm_policy_scope` CHECK로 scope-대상 정합성 강제.
- `tbl_alarm_policy_level(policy_id, level)` UNIQUE로 레벨 중복 방지.

---

## B. 평가 파이프라인 (시퀀스)

### B-1. 흐름

```
1) hana-gateway: 수집값 → KafkaMessagePublisher.publish(DeviceBundle)
   → raw 토픽(hdt.kafka.topic.raw). (KafkaMessagePublisher.java:21-42)

2) hana-event: KafkaReceiver.receive() (KafkaConfig.java:48-59)
   → InitKafkaConsumer 배치 수집 (InitKafkaConsumer.java:32-91)
   → ReactiveEventQueue → AlarmEvaluationEngine (EventService 필터 대체)

3) AlarmEvaluationEngine (per XnPointData):
   a. 정책 캐시 로드: 활성 정책+레벨을 R2DBC(공유 Postgres)에서 로드,
      (categoryId,tagName)·(deviceId,tagName) 인덱스로 in-memory 맵 구성.
      주기적/신호 기반 리프레시.
   b. bundle.deviceKey / deviceId → 소분류 category_id 해석.
      (deviceKey 파싱 또는 device→category 조회; §카테고리 전개)
   c. 각 point(tagName, valueRaw)에 대해:
        - (deviceId, tagName) DEVICE 정책 룩업
        - (categoryId, tagName) CATEGORY 정책 룩업 (소분류 전개 반영)
   d. condition_kind 분기:
        THRESHOLD: valueRaw → double 파싱, operator로 레벨별 threshold_value 비교,
                   교차한 최고 심각도(CRITICAL>WARNING>CAUTION) 산출.
        TOGGLE:    valueRaw == trigger_value → 발생.
   e. sustain(N초 연속 충족)·cooldown(M초 내 재발 억제) in-memory 상태머신 적용.
   f. 충족 시 AlarmEvent emit(policyId, deviceId, pointId, tagName, value, level, occurredAt).

4) alarm 토픽 → digitaltwin (§C).
```

### B-2. AI 레벨 판정 규칙 (예: ≥ 방향)

```
operator '>=' , 레벨 임계값: CAUTION=70, WARNING=85, CRITICAL=95 일 때
  value=97 → CRITICAL (>=95, >=85, >=70 모두 충족 → 최고 심각도)
  value=88 → WARNING
  value=72 → CAUTION
  value=60 → 미발생
'<=' 방향은 반대(낮을수록 심각)로 대칭 판정.
```
- 히스테리시스: `sustain_sec` 동안 조건 연속 충족 시 확정, `cooldown_sec` 내 동일
  (policy,device,point) 재발 억제. 상태는 in-memory(재기동 내구성은 리스크로 명시).

### B-3. TOGGLE(DI/바이너리) 규칙

- `valueRaw == trigger_value`(예 "1") 이면 발생. 통상 단일 레벨.
- (현행 `EventService.java:33-34`의 `alarmYn=Y && valueRaw=1` 하드코딩을 정책 기반으로 대체.)

### B-4. 카테고리 서브트리 전개

- 정책의 `scope=CATEGORY`, `category_id`가 대/중분류일 수 있음.
- 전개 방식(택1, 성능에 따라 선택):
  1. **캐시 구성 시 전개**: `resolveSmallCategoryIdsForFilter(categoryId)`로 상위→소분류 확장하여
     `(소분류 category_id, tagName)` 다중 인덱스로 펼침.
     근거: `device/service/DeviceCategoryCascadeService.java:116-127`(및 collect 로직 :311-362).
  2. **매칭 시 조회**: device의 소분류 `category_id`로 정책 조회 후 상위 정책 여부 판단.
- hana-event는 JPA가 없으므로(카테고리 트리는 digitaltwin 소유), 전개 결과를 **정책 캐시 로드 시
  digitaltwin API 또는 R2DBC 조인**으로 확보하는 방식을 구현 단계에서 확정한다(리스크: deviceKey→category
  매핑 비용).

---

## C. 서버 로직 (모듈별 책임)

### C-1. hana-digitaltwin (신규 `alarm` 패키지 제안)

- **AlarmPolicy CRUD**: 정책 헤더 + 레벨(1:N) 등록/수정/토글/삭제.
  (기존 `EventRuleService`/`EventRuleController` 패턴 재작성 — §E)
- **폼 지원 조회**:
  - 카테고리 스키마 tagName 목록·type: `DeviceCategory.schemaDefinitions` 노출.
    근거 조회 경로 `DeviceCategoryResponse.schemaDefinitions`(`device/dto/DeviceCategoryResponse.java:24,41`),
    카테고리 트리 API `DeviceByCategoryController.java:39-56`.
  - pointType 자동 판별: schema `type` → `PointType` 해석 → condition_kind 도출.
- **EventInstance 생성/상태전이/SOP 연결**: `EventInstanceService.create()` 재사용
  (`event/service/EventInstanceService.java:51-98`). displayType이 SOP면
  `SopRuntimeService.createInstance()` 호출(:92-94).
- **AlarmEventConsumer(신규)**: alarm 토픽 구독(현재 digitaltwin에 Kafka consumer 미존재 → 신규).
  AlarmEvent → `EventInstanceCreateRequest`로 변환하여 `create()` 호출 + 3D 이펙트 푸시(realtime/AMQP).

pointType→condition_kind 파싱 규칙(문서 계약):
```
AI, AO, AV           → THRESHOLD  (operator + level thresholds)
BI, BO, BV, DI, DO, DV → TOGGLE   (trigger_value)
MSI, MSO, MSV        → (MVP 제외 / 후속: 상태값 매칭)
FUNCTION             → (MVP 제외)
```
근거: `hana-common/.../constant/PointType.java:3-24`.

### C-2. hana-event (평가 엔진)

- **정책 캐시**: 활성 정책+레벨을 R2DBC로 로드(공유 Postgres), `(scope,key,tagName)` 인덱스 맵.
  주기적/신호 기반 리프레시.
- **스트림 평가 엔진**: `EventService`의 하드코딩 필터(`EventService.java:33-45`)를 대체.
  DeviceBundle → point별 평가 → sustain/cooldown 상태머신 → AlarmEvent emit.
- **AlarmEvent emit**: `KafkaSender`(event `KafkaConfig.java:35-45`의 `reactiveKafkaSender`)로
  alarm 토픽 발행.

### C-3. 평가 규칙 상세

- AI: §B-2 (방향별 최고 심각도 크로스).
- DI: §B-3 (trigger 일치).
- 히스테리시스/쿨다운: §B-2 말미.

---

## D. 프론트 UI (05-06.hanadream_web_new)

### D-1. 화면

- **알람 정책 목록**: 테이블(정책명/대상/레벨/발생조건/경보표시/이펙트/활성) + "정책 등록" 버튼.
- **정책 등록/편집 폼**:
  ```
  scope 선택 (CATEGORY | DEVICE)
    ├ CATEGORY → 카테고리 트리/피커에서 대/중/소 선택
    └ DEVICE   → 장비 선택
  → tagName 드롭다운 (선택 소분류 schemaDefinitions 기반)
  → pointType 자동 표시 (schema type)
  → 조건 분기:
       THRESHOLD(AI) → operator + 레벨별(주의/경계/심각) threshold·icon·effect
       TOGGLE(DI)    → trigger_value(0/1) + 단일 레벨 icon·effect
  → 경보표시(display_type: POPUP/POPUP_SOP) + (SOP시)sopTemplate 선택
  → effect_enabled + sustain_sec + cooldown_sec + active
  ```
- **선택 정책 상세**: 헤더 + 레벨 리스트.

### D-2. 재활용 컴포넌트/서비스

| 재활용 | 위치 |
|---|---|
| 카테고리 트리/피커 | `components/device/DeviceHierarchyFilter.tsx`, `components/device/DeviceCategoryPicker.tsx` |
| device 스키마 조회 | `services/deviceService.ts` (카테고리 트리 → schemaDefinitions) |
| 기존 이벤트 서비스 참고 | `services/eventService.ts` (`/event/rules` CRUD 패턴 → `/alarm/policies`로 대체 제안) |
| 기존 event 페이지 | `pages/event/` (EventThresholdPage 등, 재작성/이관 검토) |

> 프론트 `services/`의 신규 계약 제안: `alarmPolicyService.ts` — `/alarm/policies` CRUD.
> 기존 `eventService.ts`의 `/event/rules`는 정책 API로 대체(§E, react-frontend-agent 소관).

---

## E. event 디렉토리 처리표

| 파일 (`hana-digitaltwin/.../event/`) | 근거 | 판단 |
|---|---|---|
| `entity/EventRule.java` | `entity/EventRule.java:20-74` | **대체**: `tbl_alarm_policy(+level)`로 재설계. device 단위·jsonb conditions 구조 폐기 |
| `repository/EventRuleRepository.java` | `repository/EventRuleRepository.java:12-30` | **재작성/삭제**: AlarmPolicyRepository로 대체. `deleteByDeviceId(In)`는 CascadeService 참조 정리 필요 |
| `service/EventRuleService.java` | `service/EventRuleService.java:19-105` | **재작성**: AlarmPolicyService |
| `api/EventRuleController.java` | `api/EventRuleController.java:14-64` | **재작성**: `/alarm/policies` 컨트롤러 |
| `dto/EventRuleDtos.java`(EventRule* records) | `dto/EventRuleDtos.java:17-74` | **재작성**: AlarmPolicy DTO |
| `entity/EventInstance.java` | `entity/EventInstance.java:12-78` | **유지**: `rule` FK(`:26-28`)를 정책 참조로 재정의(nullable 유지 가능) |
| `entity/EventInstanceStatus.java` | :3-8 | **유지** |
| `entity/EventDisplayType.java` | :3-6 | **유지 + 확장/매핑**: SIMPLE/SOP → POPUP/POPUP_SOP 매핑(값 추가 또는 표시계층 매핑) |
| `service/EventInstanceService.java` | :29-171 | **유지·재활용**: alarm consumer가 `create()` 호출. `rule` 의존부(:57-64,:86-88) 정책 참조로 조정 |
| `api/EventInstanceController.java` | :15-40 | **유지** |
| `dto/EventRuleDtos`(EventInstance* records) | :76-104 | **유지** (EventInstanceCreateRequest/Summary) |
| `DeviceCategoryCascadeService.eventRuleRepository` | `device/service/DeviceCategoryCascadeService.java:12,35,91,105` | **정리 필요**: 카테고리/장비 삭제 시 `eventRuleRepository.deleteByDeviceId(In)` 호출을 정책 삭제 로직으로 대체(정책은 카테고리 스코프이므로 장비 삭제와 결합 완화). spring-backend-agent 변경 요청 대상 |
| hana-event `EventService.java` | `hana-event/.../service/EventService.java:33-45` | **대체**: 하드코딩 필터 → AlarmEvaluationEngine |

---

## F. 구현 단계(Loop 제안) + 미결/리스크

### F-1. 단계 (승인 기반 Loop)

| 단계 | 내용 | 담당 |
|---|---|---|
| 1 | DB/엔티티: `tbl_alarm_policy` + `tbl_alarm_policy_level` 마이그레이션 + JPA 엔티티 | database-agent, spring-backend-agent |
| 2 | digitaltwin AlarmPolicy CRUD API + 폼 지원 조회(카테고리 schema tagName/pointType) | spring-backend-agent |
| 3 | hana-event 평가엔진 + 정책 캐시 + alarm 토픽 emit | spring-backend-agent |
| 4 | digitaltwin alarm 소비 consumer → EventInstance/SOP/이펙트(realtime/AMQP) | spring-backend-agent |
| 5 | 프론트 알람 정책 UI(목록/폼/상세) | react-frontend-agent |
| 6 | (후속) 알람 메시지(SMS)·시나리오 연계 | 후속 Loop |

### F-2. 미결/리스크

| 리스크 | 내용 | 완화 |
|---|---|---|
| 정책 캐시 일관성 | 정책 변경→hana-event 평가 반영 지연 | 신호 기반 리프레시(변경 이벤트) + 짧은 TTL 폴백 |
| deviceKey→category 매핑 비용 | 평가 시 소분류 전개/조회 부하 | 캐시 구성 시 전개(§B-4-1) + device→category 사전 인덱스 |
| valueRaw 파싱/단위 | `XnPointData.valueRaw`는 String(`XnPointData.java:17`), 단위 상이 | THRESHOLD 파싱 실패 방어, unit 정합성은 schema 기준 검증 |
| DI trigger 표현 | 0/1 문자열 vs 상태값 | `trigger_value` VARCHAR, 명시적 문자열 비교 |
| sustain/cooldown 재기동 내구성 | in-memory 상태 → 재기동 시 소실 | MVP는 in-memory 허용, 후속 외부 상태저장 검토 |
| 알람 폭주(스톰) | 대량 동시 발생 | cooldown + (후속) 정책별 rate-limit / 집계 |
| displayType 값 불일치 | SIMPLE/SOP ↔ POPUP/POPUP_SOP | enum 확장 또는 표시계층 매핑 결정 필요(§2, §E) |
| digitaltwin consumer 부재 | 현재 Kafka/AMQP 리스너 없음 | 신규 consumer 구현(devops-agent yml 승인 포함) |

---

## G. 계약/흐름 요약표

| 결합점 | 계약 | 소유/생산 | 소비 |
|---|---|---|---|
| raw 토픽 | `DeviceBundle{deviceId,deviceKey,timestamp,points[XnPointData]}` | gateway `KafkaMessagePublisher` | hana-event `KafkaReceiver` |
| alarm 토픽(신규) | `AlarmEvent{policyId,deviceId,pointId,tagName,value,level,occurredAt,...}` | hana-event 평가엔진 | digitaltwin AlarmEventConsumer |
| 정책 저장소 | `tbl_alarm_policy(+level)` (공유 Postgres) | digitaltwin(JPA, CRUD) | hana-event(R2DBC, 읽기 캐시) |
| 폼 스키마 | `DeviceCategory.schemaDefinitions[PointSchemaDefinition{tagName,type,unit,tagDesc,isDisplay}]` | digitaltwin | 프론트 폼 |
| 발생 이력 | `tbl_event_instance` / `EventInstance` | digitaltwin | 프론트 이벤트 목록/표시 |
| SOP 연계 | `SopTemplate`/`SopInstance` | digitaltwin SOP 도메인 | EventInstance(SOP 표시) |

---

## 결어

이 문서는 **설계안**이며, 실제 코드/스키마 구현은 CLAUDE.md의 승인 기반 Bounded Loop
(§4)로 진행한다. 각 단계는 담당 AGENT가 자기 소유 영역만 수정하며, event 디렉토리 재작성·
DeviceCategoryCascadeService 참조 정리·digitaltwin Kafka consumer 신규 도입·yml(토픽) 변경은
각각 변경 요청서/승인 게이트를 거친다.
