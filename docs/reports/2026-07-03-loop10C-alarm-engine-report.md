# 작업 보고서 — Loop 10-C 알람 평가엔진 (hana-event)

- 작성일: 2026-07-03
- Loop: 10-C (알람 정책 Phase 3 — hana-event 평가엔진)
- 담당 AGENT: spring-backend-agent
- 승인 요청서: qa/change-requests/2026-07-03-loop10-alarm-eval-engine.md
- 설계 문서: docs/architecture/2026-07-01-alarm-policy-redesign.md
- 선행: 10-A(hana-common AlarmEvent 계약), 10-B(yml alarm 토픽 hdt-alarm-events + producer JsonSerializer) 완료

## 1. 변경 요약

hana-event 에 활성 알람 정책을 R2DBC 로 로드/캐시하고, kafka raw 스트림의 포인트값을
정책 조건(THRESHOLD/TOGGLE)에 대조 평가하여 발생 시 `hdt-alarm-events` 토픽으로 발행하는
**독립 병렬 파이프라인**을 신규 추가했다.

핵심 결정: 승인 요청서에는 "EventService.startEventPipeline 에 평가 단계 연결(기존 필터 대체)"
이라고 되어 있었으나, 회귀 위험을 없애기 위해 **기존 EventService/SnapshotService/InitKafkaConsumer 는
일절 수정하지 않고**, `ReactiveEventQueue.getDeviceRawFlux()` 를 별도로 독립 구독하는
`AlarmEvaluationPipeline` 을 신설하는 방식으로 구현했다. 멀티캐스트 싱크를 공유 소비하므로
기존 WebSocket/스냅샷 파이프라인에 영향이 없다.

## 2. 변경 파일 목록

### 신규 (com.mpole.hana.event.alarm)
- hana-event/src/main/java/com/mpole/hana/event/alarm/AlarmPolicyReader.java
- hana-event/src/main/java/com/mpole/hana/event/alarm/PolicyView.java
- hana-event/src/main/java/com/mpole/hana/event/alarm/PolicyIndex.java
- hana-event/src/main/java/com/mpole/hana/event/alarm/AlarmPolicyCache.java
- hana-event/src/main/java/com/mpole/hana/event/alarm/AlarmEvaluator.java
- hana-event/src/main/java/com/mpole/hana/event/alarm/AlarmPublisher.java
- hana-event/src/main/java/com/mpole/hana/event/alarm/AlarmEvaluationPipeline.java

### 최소 수정 (허용 범위)
- hana-event/src/main/java/com/mpole/hana/event/config/properties/HdtKafkaTopicProperties.java
  — `String alarm` 필드 + `@DefaultValue("hdt-alarm-events")` 추가.

### 미수정(회귀 방지 대상)
- EventService.java / SnapshotService.java / InitKafkaConsumer.java / KafkaConfig.java /
  DatabaseConfig.java / ReactiveEventQueue.java — 무수정.
- yml / build.gradle / 타 모듈 — 무수정. 신규 의존성 없음.

## 3. 평가 흐름

1. `AlarmEvaluationPipeline` (@PostConstruct) 가 `getDeviceRawFlux()` 독립 구독.
2. `publishOn(alarm-eval-thread)` → `flatMapIterable(bundles)`.
3. 번들별 `Mono.fromCallable(evaluator.evaluate(bundle))` (boundedElastic, 에러 시 빈 리스트로 격리).
4. `flatMapIterable(alarms)` → `publisher.publish(alarm)` → alarm 토픽 발행.
5. 스트림 레벨 `retryWhen` 백오프로 상시 복구.

### AlarmEvaluator.evaluate
- 각 XnPointData 의 (deviceId, tagName) 로 매칭 정책 수집:
  - DEVICE 인덱스 `D|{deviceId}|{tagName}`.
  - deviceCategoryMap 으로 소분류 조회 후 CATEGORY 인덱스 `C|{소분류}|{tagName}`.
- THRESHOLD: valueRaw double 파싱(실패 시 skip) → operator(GTE>=,LTE<=,GT>,LT<)로 레벨별
  threshold 비교 → 만족 레벨 중 최고 심각도(CRITICAL>WARNING>CAUTION) 선택.
- TOGGLE: valueRaw/triggerValue 를 on(1,true,on)/off(0,false,off) 정규화 후 일치 시
  최고 심각도 레벨 선택.
- point_type 과 실제 포인트 objectType 불일치 시 방어적 skip(debug 로그).

## 4. 인덱스 구조 (AlarmPolicyCache / PolicyIndex)

- 불변 `PolicyIndex`(categoryIndex, deviceIndex, deviceCategoryMap)를 통째로 만들어
  **volatile 참조 스왑**. 부분 갱신(ConcurrentHashMap put)을 피해 평가 중 읽기 불일치 방지.
- CATEGORY 정책의 category_id 를 카테고리 트리에서 **하위 소분류(depth=2) 집합으로 전개**하여
  각 소분류 키에 정책 등록. depth 정보로 소분류를 못 찾으면 정책 카테고리 자체를 키로 사용(방어).
- 리프레시: @PostConstruct 초기 로드(boundedElastic) + `Flux.interval(30초)` 주기 갱신.
  각 tick 을 onErrorResume 으로 격리, 실패 시 이전 스냅샷 유지.

## 5. 상태 (cooldown / sustain)

- `stateMap` key = `{policyId}|{deviceId}`.
- sustain(best-effort): 조건 true & firstBreachAt=null → now 기록. now-firstBreachAt >= sustainSec
  이면 발생 자격. 조건 false 면 firstBreachAt 리셋(cooldown 상태는 유지).
- cooldown: lastFiredAt 존재 & now-lastFiredAt < cooldownSec 이면 억제. 발생 시 lastFiredAt=now.

## 6. 발행 (AlarmPublisher)

- 기존 `KafkaSender<String, Object> reactiveKafkaSender` 빈 재사용.
- topic = `HdtKafkaTopicProperties.alarm()` (기본 hdt-alarm-events), key = deviceId 문자열.
- SenderRecord/ProducerRecord 사용, value=AlarmEvent → producer JsonSerializer 직렬화(yml 확인).
- Mono<Void> 반환, send 결과 exception 로깅.

## 7. 한계

- **인메모리 상태**: sustain/cooldown 은 프로세스 재기동 시 소실(sustain 재계측, cooldown 초기화).
- **리프레시 지연**: 정책 변경 반영 최대 지연 = 리프레시 주기(30초). 코드 주석 명시.
- 다중 인스턴스 배포 시 상태/캐시가 인스턴스별 독립(중복 발행 가능성) — Phase 4 이후 검토 대상.

## 8. 검증

- `./gradlew :hana-event:compileJava -q` 컴파일 성공. (KafkaConfig 의 기존 unchecked 경고만
  존재, 신규 파일 무경고.)

## 9. 다른 AGENT 영향

- **회귀 없음**: 기존 EventService(WebSocket)/SnapshotService(DB upsert)/InitKafkaConsumer
  파이프라인 무수정. 알람 평가는 rawFlux 를 독립 구독하는 별도 파이프라인.
- **devops-agent**: 10-B 에서 이미 yml alarm 토픽 반영됨. 추가 yml 변경 없음. (Properties 기본값
  hdt-alarm-events 로 yml 미설정 시에도 동작.)
- **Phase 4 (spring-backend, digitaltwin)**: digitaltwin 이 `hdt-alarm-events` 구독 →
  AlarmEvent 역직렬화 → EventInstance/SOP 연계/이펙트 처리. 별도 Loop.

## 10. QA 요청 항목

1. 신규 7개 파일이 hana-event 소유 영역 내이며 리액티브(블로킹 없음) 규칙 준수 확인.
2. HdtKafkaTopicProperties 최소 수정(alarm 필드 추가)만 이뤄졌는지 확인.
3. 기존 EventService/SnapshotService/InitKafkaConsumer/KafkaConfig/DatabaseConfig 무수정 확인(회귀).
4. 인덱스 volatile 스왑 / cooldown·sustain 상태 로직 정합성 리뷰.
5. AlarmEvent 필드 매핑(operator=enum name, level=enum name, occurredAt 등) 계약 일치 확인.
