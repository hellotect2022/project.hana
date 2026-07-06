# QA 검증 보고서 — Loop 10 알람 Phase 3 (10-A / 10-B / 10-C)

- 작성일: 2026-07-03
- Loop: 10 (알람 정책 Phase 3 — hana-common 계약 / hana-event yml / hana-event 평가엔진)
- 검증자: qa-code-reviewer (검증 전용, 코드 수정 없음)
- 근거 문서:
  - 승인 요청서: qa/change-requests/2026-07-03-loop10-alarm-eval-engine.md
  - 설계: docs/architecture/2026-07-01-alarm-policy-redesign.md
  - 작업 보고서: docs/reports/2026-07-03-loop10A/B/C-*.md
- 검증 대상 커밋 상태: 작업 트리(uncommitted). 신규 8파일 + yml/HdtKafkaTopicProperties 최소수정.

---

## 0. 종합 판정: **PASS**

- **회귀 없음: PASS** (근거 §1)
- **블로킹 없음: PASS** (근거 §2)
- 컴파일: **PASS** — `./gradlew :hana-common:compileJava :hana-event:compileJava -q` EXIT=0.
- 계약/평가 정확성/캐시/상태 로직: 설계·10-A 계약과 정합. 세부 WARNING/후속 항목은 §9.

Phase 4(digitaltwin alarm 소비) 진행 **가능**. 재검증 불요(정보성 후속 항목만 존재).

---

## 1. 회귀 방지 (최중요) — PASS

### 1-1. 기존 파일 무수정 확인 (git diff)
`git diff --stat HEAD -- hana-event/ hana-common/` 결과, Loop 10 관련 변경은 다음 뿐이다.

| 파일 | 상태 | 판정 |
|---|---|---|
| hana-event/.../config/properties/HdtKafkaTopicProperties.java | +2/-1 (`String alarm` 필드 추가) | 허용 범위(최소수정) |
| hana-event/src/main/resources/application.yml | +4 (producer 직렬화 + alarm 토픽) | 10-B 승인 범위 |
| hana-common/.../dto/AlarmEvent.java | 신규(untracked) | 10-A 신규 |
| hana-event/.../event/alarm/ (7파일) | 신규(untracked) | 10-C 신규 |

- **EventService.java / SnapshotService.java(로직) / InitKafkaConsumer.java / KafkaConfig.java / DatabaseConfig.java / ReactiveEventQueue.java 는 diff 에 로직 변경 없음.** 원문 확인:
  - EventService: `getDeviceRawFlux()` 독립 구독, 하드코딩 필터(`alarmYn=Y && valueRaw=1`) 그대로 유지 — 알람 엔진이 이를 대체하지 않고 병렬 신설(승인 요청서의 "필터 대체"를 회귀 안전한 "병렬 신설"로 변경, 작업 보고서 §1 명시).
  - InitKafkaConsumer: 무수정(emit 로직 동일).
  - KafkaConfig: `reactiveKafkaSender` (`KafkaSender<String,Object>`) 무수정 → AlarmPublisher 가 그대로 재사용.
  - DatabaseConfig: `postgresDatabaseClient`(schema=core) 무수정 → AlarmPolicyReader 가 그대로 재사용.

> 참고(무관): `SnapshotService.java` 에 diff 1건 존재하나, 내용은 `tbl_device_snapshot_last`→`tbl_device_current_value` 테이블명 리네이밍으로 **Loop 10 범위 밖 선행 미커밋 변경**(최근 커밋 3542326 테마와 동일)이다. 알람 엔진과 무관하며 회귀 판정에 영향 없음. 다만 Loop 10 커밋 시 이 변경이 섞이지 않도록 분리 커밋 권고(§9-후속).

### 1-2. 파이프라인 독립성 — PASS
- `AlarmEvaluationPipeline`(alarm/AlarmEvaluationPipeline.java:38)이 `reactiveEventQueue.getDeviceRawFlux()` 를 **@PostConstruct 로 독립 구독**. `publishOn(Schedulers.newParallel("alarm-eval-thread"))` 로 기존 event/snapshot 스레드와 격리.
- `ReactiveEventQueue.deviceRawSink` 는 `multicast().onBackpressureBuffer(20000)` (ReactiveEventQueue.java:14). 멀티캐스트이므로 신규 구독자가 기존 구독자(EventService/SnapshotService)의 이벤트 흐름을 소비·소모하지 않는다 → **기존 WebSocket/스냅샷 파이프라인 데이터 손실 없음**.

### 1-3. 멀티캐스트 배압/버퍼(20000) 영향 평가 — PASS(주의 첨부)
- Reactor `Sinks.many().multicast().onBackpressureBuffer(N)` 는 **구독자 공유 단일 버퍼**로, 버퍼는 **가장 느린 구독자** 기준으로 소모된다. 구독자가 2→3개로 늘면, 알람 파이프라인이 느릴 경우 공유 버퍼 점유가 증가해 `tryEmitNext` 실패(오버플로우 드롭) 확률이 이론상 커질 수 있다.
- 완화 요소: 알람 파이프라인이 `publishOn(newParallel)` + `flatMap`(비동기) + boundedElastic 평가로 소비를 빠르게 흘려보내며, 상류가 `List<DeviceBundle>` 배치 단위(초당 수천 배치가 아닌 배치 묶음)라 요소 개수 기준 버퍼 소모는 크지 않다. 평가는 순수 인메모리 맵 룩업이라 지연이 작다.
- 결론: **현재 구조에서 회귀(기존 파이프라인 정지/손실)를 유발하지 않음**. 단, 실기동 시 알람 파이프라인 소비 지연이 공유 버퍼에 미치는 영향은 부하 테스트로 확인 권고(§9-후속, 정보성).

---

## 2. 블로킹 금지 — PASS

- **AlarmPolicyReader**: 모든 메서드가 `Flux`/`Mono` 반환. `DatabaseClient.sql(...).map(...).all()/collectMap()` 만 사용, `.block()` 없음.
- **AlarmPolicyCache**:
  - `@PostConstruct init()` 에서 `refresh().subscribeOn(boundedElastic()).subscribe(...)` — 논블로킹 구독.
  - 주기 리프레시 `Flux.interval(30s).concatMap(refresh()...).subscribeOn(boundedElastic()).subscribe()` — 논블로킹.
  - `refresh()` 는 `Mono.zip(...).map(buildIndex).doOnNext(스왑).then()` — 리액티브 체인. `buildIndex`(동기 CPU 작업)는 zip 결과 콜백 내부이며 boundedElastic 스레드에서 실행.
- **AlarmEvaluationPipeline**: 동기 평가 `evaluator.evaluate(bundle)` 를 `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` 로 **격리** (AlarmEvaluationPipeline.java:42-44). 파이프라인 전체에 `.block()` 없음.
- **AlarmEvaluator.evaluate**: 순수 인메모리 로직(맵 룩업/파싱/상태). 블로킹 I/O 없음.
- **AlarmPublisher.publish**: `reactiveKafkaSender.send(Mono.just(...)).then()` — 논블로킹.
- 전 7파일 `.block(` / `.toFuture().get(` / `Thread.sleep` 미검출.

**판정: 파이프라인·리더·캐시·발행 모두 논블로킹. PASS.**

---

## 3. 평가 정확성 — PASS

### 3-1. THRESHOLD (AlarmEvaluator.java:165-178, 202-211)
- operator 매핑 정확: `GTE`→`>=`, `LTE`→`<=`, `GT`→`>`, `LT`→`<`. 미지정/알수없음 → `false`(미발생). digitaltwin `AlarmOperator` enum(GTE/LTE/GT/LT, `@Enumerated(STRING)`)와 문자열 일치.
- 레벨별 threshold 비교 후 **최고 심각도 선택**: `severity()` CRITICAL(3)>WARNING(2)>CAUTION(1), 만족 레벨 중 max 채택 — 설계 §B-2 정합.
- `thresholdValue==null` 레벨 skip. `valueRaw` 숫자 파싱 실패 시 `parseDouble` → null → 정책 skip(방어). PASS.

### 3-2. TOGGLE (AlarmEvaluator.java:184-200, 224-230)
- 정규화: TOGGLE_ON=`{1,true,on}`, TOGGLE_OFF=`{0,false,off}`, trim+lowercase. 그 외 → null(미매칭).
- actual/trigger 둘 다 정규화 후 `equals` 비교 → 일치 시 레벨 중 최고 심각도 선택.
- digitaltwin 은 triggerValue 를 "0"/"1" 로 저장(AlarmConditionKind 주석) → 정규화 집합에 포함, 정합.
- 유의: TOGGLE 은 통상 단일 레벨(설계 §A-2 note)이나 다레벨이어도 최고 심각도 채택으로 안전.

### 3-3. enum name 문자열 처리 — PASS
- DB 저장값이 enum name 문자열(scope=CATEGORY/DEVICE, operator=GTE..., level=CAUTION..., conditionKind=THRESHOLD/TOGGLE, displayType=SIMPLE/SOP)임을 digitaltwin 엔티티 `@Enumerated(EnumType.STRING)` 로 확인.
  - AlarmPolicy.java:37-66, AlarmPolicyLevel.java:30, AlarmOperator/AlarmLevel/AlarmConditionKind enum.
- Reader/Evaluator 가 String 그대로 읽어 비교 → **모듈 간 enum 비의존 계약** 준수(10-A 원칙과 일치).

---

## 4. 캐시 / 인덱스 정합 — PASS

- **volatile 스왑**: `AlarmPolicyCache.index`(volatile) 를 `buildIndex` 로 새 불변 `PolicyIndex` 생성 후 통째 대입(AlarmPolicyCache.java:49,88). `PolicyIndex` 필드는 `Map.copyOf`(불변). **부분 읽기 불일치 방지** 정확.
- **CATEGORY→소분류(depth=2) 전개**: `expandToSmallCategories`(:157-185) DFS 로 서브트리 depth==2 수집. 정책 카테고리가 대/중분류면 하위 소분류로 펼침. depth 매핑은 DeviceCategory(0/1/2 주석, :42)와 일치. `visited` 로 사이클 방어.
  - 방어 로직: 소분류 미발견 시 정책 카테고리 자체를 키로 사용(:181-183). tbl_device.category_id 가 소분류 직접 저장이라는 전제와 정합.
- **deviceCategoryMap**: `SELECT device_id, category_id FROM tbl_device` (Reader:143-154), null 필터 후 collectMap. 컬럼명 tbl_device 엔티티(:29,43)와 일치.
- **리프레시 실패 시 이전 스냅샷 유지**: 초기 로드 실패 → 빈 인덱스 유지(로그). 주기 tick 실패 → `onErrorResume(Mono.empty())` 로 스킵, `index` 미변경 → 이전 스냅샷 유지(:70-71). PASS.
- **초기 로드 전 안전**: `index` 초기값 `PolicyIndex.empty()`(불변 빈 맵) → 매칭 0, 무발행. PASS.

컬럼 계약 교차검증(전부 일치):
| Reader SQL | digitaltwin 엔티티 근거 |
|---|---|
| tbl_alarm_policy(policy_id,policy_name,scope,category_id,device_id,tag_name,point_type,condition_kind,operator,trigger_value,display_type,sop_template_id,effect_enabled,sustain_sec,cooldown_sec,active) | AlarmPolicy.java:28-84 |
| tbl_alarm_policy_level(level_id,policy_id,level,threshold_value,icon,effect,display_order) | AlarmPolicyLevel.java:23-42 |
| tbl_device(device_id,category_id) | Device.java:29,43 |
| tbl_device_category(category_id,category_parent_id,depth) | DeviceCategory.java:28,35,42 |

---

## 5. 상태 로직 (cooldown / sustain) — PASS

- 상태키 `{policyId}|{deviceId}`, `ConcurrentHashMap`(AlarmEvaluator.java:31,108).
- **sustain**: 조건 true & firstBreachAt==null → now 기록. `sustainSec>0` 이면 경과<sustainSec 동안 미발생(:118-126). 조건 false → firstBreachAt=null 리셋(:112-114). 해제 시 재계측 정합.
- **cooldown**: `cooldownSec>0` & lastFiredAt!=null & 경과<cooldownSec → 억제(:129-134). 발생 시 lastFiredAt=now(:136). 조건 해제 시 cooldown(lastFiredAt) 유지 → 재발생 억제 지속. 정합.
- 순서: sustain 통과 → cooldown 통과 → 발행. 논리 순서 적절.
- 인메모리 한계 코드 주석 명시(:19-21, :38-41). PASS. (재기동 소실은 §9 후속.)

---

## 6. 계약 일치 — PASS

- **AlarmEvent(10-A) 20필드 ↔ AlarmEvaluator 생성 매핑**: policyId/policyName/level(chosen.level)/scope/deviceId/deviceKey/pointId/tagName/pointType/conditionKind/valueRaw/thresholdValue(chosen)/operator/triggerValue/displayType/sopTemplateId/icon(chosen)/effect(chosen)/effectEnabled/occurredAt — 20필드 전부 채움(AlarmEvaluator.java:138-159). 빌더 필드명 record 컴포넌트명과 일치(컴파일 통과로 확증).
- **토픽명 문자열**: `HdtKafkaTopicProperties.alarm()` 기본값 `hdt-alarm-events`(Properties:9) = yml `hdt.kafka.topic.alarm: hdt-alarm-events`(application.yml:47) 일치. Publisher 가 `topicProperties.alarm()` 사용(:32).
- **KafkaSender 재사용 + producer 직렬화**: `reactiveKafkaSender`(KafkaConfig.java:36, `<String,Object>`) 재사용. yml producer value-serializer=JsonSerializer, key-serializer=StringSerializer(:40-42). value=AlarmEvent(POJO record) → JsonSerializer 직렬화 정합. key=deviceId 문자열. consumer 설정 불변 확인.

---

## 7. 범위 준수 — PASS

- 10-C 신규 7파일 전부 `com.mpole.hana.event.alarm` (hana-event 소유 영역).
- HdtKafkaTopicProperties: `alarm` 필드 1개 추가만(최소수정). 승인 요청서 §1 허용 범위.
- build.gradle / settings.gradle / 타 모듈(hana-digitaltwin 등) 무변경. 신규 의존성 없음(기존 reactor/r2dbc/kafka만 사용).
- hana-common: AlarmEvent 신규 record 1개(lombok @Builder만, 기존 의존). 순수 계약.

---

## 8. 컴파일 검증

```
./gradlew :hana-common:compileJava :hana-event:compileJava -q
→ EXIT=0 (성공)
```
신규 파일 컴파일 오류·타입 불일치 없음. (작업 보고서의 KafkaConfig 기존 unchecked 경고는 -q 로 미출력, 신규 파일 무관.)

---

## 9. 후속 항목 (정보성 — Phase 4 이후 / 운영 고려)

승인 요청서 §2 위험도 및 본 검증에서 확인된 한계. **모두 설계·코드 주석에 명시되어 있어 판정을 낮추지 않음(문서화된 MVP 한계).**

1. **인메모리 상태 재기동 소실**: sustain/cooldown 상태(stateMap)가 프로세스 재기동 시 소실 → 재기동 직후 cooldown 무시·sustain 재계측. 코드 주석 명시(AlarmEvaluator:19-21). 후속: 외부 상태저장(Redis 등) 검토.
2. **다중 인스턴스 중복 발행**: hana-event 다중 배포 시 캐시·상태가 인스턴스별 독립 → 동일 알람 중복 발행 가능. cooldown 은 인스턴스 로컬. 후속: 파티션 분할 또는 digitaltwin 소비측(Phase 4) 멱등 처리.
3. **리프레시 지연 30초**: 정책 변경 반영 최대 지연 = REFRESH_INTERVAL(30초). 즉시성 필요 시 신호 기반 리프레시(변경 이벤트) 검토. 코드 주석 명시.
4. **대량 device 캐시 메모리**: `loadDeviceCategoryMap` 이 tbl_device 전량 로드 → device 규모 클수록 메모리·리프레시 비용 증가. 카테고리 인덱스도 소분류 전개로 팽창 가능. 부하/규모 산정 후 필요 시 부분 로드/캐시 TTL 조정.
5. **valueRaw 단위/타입**: THRESHOLD 는 `Double.parseDouble` 만. 단위 정합성(schema unit vs 임계값 단위)은 미검증 → 정책 등록측(digitaltwin/프론트)에서 단위 일치 보장 필요. 숫자 아닌 값은 skip(방어됨).
6. **point_type 불일치 skip 조건**: `objectType` 이 null 이면 타입검사 건너뛰고 평가 진행(:83). objectType 항상 존재하는 데이터라면 무영향이나, 부재 시 타입 무관 매칭됨. 데이터 계약상 objectType 채워지는지 확인 권고.
7. **멀티캐스트 공유 버퍼(20000) 부하 영향**: §1-3. 3구독자 공유 버퍼의 실부하 소모는 부하 테스트로 확인 권고.
8. **실기동 통합테스트 필요**: 본 QA 는 정적 리뷰 + 컴파일. 실제 DB(core.tbl_alarm_policy 존재)·kafka 연동 하 end-to-end(정책 로드→평가→alarm 토픽 발행→디짓털트윈 소비) 통합테스트는 Phase 4 연계 시점에 필요.
9. **커밋 위생(무관 변경 분리)**: SnapshotService 테이블 리네이밍(Loop 10 범위 밖)이 작업 트리에 혼재. Loop 10 커밋 시 알람 관련 파일만 분리 커밋 권고.

---

## 10. 수정 요청 대상 AGENT

- **없음(FAIL 항목 없음).** 코드 수정 승인 요청서 불요.
- 후속 항목(§9)은 Phase 4 설계/운영 검토 사항으로 loop-orchestrator 가 다음 Loop 계획에 반영.

## 11. 재검증 필요 여부

- **불요.** 종합 PASS. Phase 4(digitaltwin alarm 소비) 진행 가능.
- 단, Phase 4 완료 후 §9-8 실기동 통합테스트 시점에 end-to-end 재검증 권고.
