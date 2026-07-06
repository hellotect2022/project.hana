# Loop 11-A 작업 보고서 — 알람 출력 Kafka → event WebSocket 스트림 통합

- 작성일: 2026-07-03
- 담당 AGENT: spring-backend-agent
- 승인 요청서: `qa/change-requests/2026-07-03-loop11-alarm-websocket-rework.md`
- 대상 모듈: `hana-event` (src 하위만)

---

## 1. 작업 목표

알람 출력을 기존 Kafka(alarm 토픽) 발행에서 **event-server 내부 WebSocket 이벤트 스트림
통합**으로 교체한다. 알람은 event-server 가 평가·WebSocket 전송·DB 저장까지 담당하며
digitaltwin 은 발행 경로에서 제외된다. 기존 FIRE_BELL 이벤트/현재값/스냅샷 경로는 회귀
없이 보존한다(추가 필드/분기만).

## 2. 변경 요약

1. `EventHistoryDto` 에 nullable `AlarmEvent alarm` 컴포넌트를 추가(record + @Builder).
   기존 필드(eventType/eventId/isProcessed/data) 및 사용처는 무영향. FIRE_BELL 은 alarm
   미지정 → null.
2. `AlarmEvaluationPipeline` 이 `AlarmPublisher`(Kafka) 의존/호출을 제거하고, 평가된
   `AlarmEvent` 를 `EventHistoryDto(eventType="ALARM")` 로 감싸
   `WebsocketBroadcastService.publish(dto)` 로 emit. 기존 이벤트와 동일 싱크
   (`ReactiveEventQueue.eventHistorySink`)를 공유하므로 WebSocket 전송(streamJson)과
   스냅샷 파이프라인(EventSnapshotService)으로 자동 전파. 리액티브 유지(블로킹 없음),
   기존 스케줄러 격리/retry 구조 유지.
3. `AlarmPublisher.java` 삭제(Kafka 발행 클래스). 참조처는 `AlarmEvaluationPipeline`
   한 곳뿐이었고 정리 완료. `KafkaSender` 빈/`HdtKafkaTopicProperties.alarm` 필드는
   미변경(11-C yml 롤백에서 처리).
4. `EventSnapshotService` 의 히스토리 소비 파이프라인에 eventType 분기 추가.
   `"ALARM"` → 신규 `insertAlarmHistory(event)` (R2DBC INSERT INTO tbl_alarm_history),
   그 외(FIRE_BELL 등) → 기존 `insertEventHistory` 그대로. alarm 페이로드 null 이면 스킵,
   nullable 값은 `bindNull(type)` 로 타입 안전 바인딩, 실패 시 doOnError 로깅 후
   onErrorResume(Mono.empty)로 스트림 보호.

## 3. 변경 파일 목록

| 구분 | 파일 |
|---|---|
| 수정 | `hana-event/src/main/java/com/mpole/hana/event/dto/EventHistoryDto.java` |
| 수정 | `hana-event/src/main/java/com/mpole/hana/event/alarm/AlarmEvaluationPipeline.java` |
| 수정 | `hana-event/src/main/java/com/mpole/hana/event/service/EventSnapshotService.java` |
| 삭제 | `hana-event/src/main/java/com/mpole/hana/event/alarm/AlarmPublisher.java` |

미변경(확인만): `EventService.java`, `ReactiveEventQueue.java`,
`WebsocketBroadcastService.java`, `AlarmEvaluator.java`, `hana-common AlarmEvent.java`.

## 4. 알람 emit 경로 · 분기 저장 요약

```
deviceRawFlux (멀티캐스트 싱크)
  ├─ EventService            → FIRE_BELL 필터 → EventHistoryDto(data) → publish  (기존, 무변경)
  └─ AlarmEvaluationPipeline → evaluate → AlarmEvent → EventHistoryDto(eventType="ALARM", alarm)
                                                          → websocketBroadcastService.publish(dto)
                                                          → reactiveEventQueue.emitEventHistory(dto)
                                                                └─ eventHistorySink (공유)
                                                                     ├─ WebsocketBroadcastService.streamJson → WebSocket 전송
                                                                     └─ EventSnapshotService.getEventHistoryFlux
                                                                          ├─ eventType="ALARM" → insertAlarmHistory → tbl_alarm_history
                                                                          └─ 그 외              → insertEventHistory → tbl_device_event_history (기존)
```

## 5. 기존 FIRE_BELL 경로 무영향 근거

- `EventHistoryDto` 는 record + @Builder 이므로 신규 컴포넌트(alarm) 추가에도 기존 빌더
  호출부(EventService: alarm 미지정)가 그대로 컴파일되며 alarm=null 로 채워진다.
- `EventService`, `WebsocketBroadcastService`, `ReactiveEventQueue`, `AlarmEvaluator` 는
  수정하지 않았다.
- 스냅샷 분기는 `"ALARM".equals(eventType)` 일 때만 신규 경로로 진입하고, FIRE_BELL 등
  기존 이벤트는 `insertEventHistory` 원본 로직을 그대로 탄다(쿼리/바인딩 무변경).
- 알람 파이프라인은 기존과 동일하게 rawFlux 를 **독립 구독**(멀티캐스트)하므로 다른
  구독자에 영향을 주지 않는다.

## 6. 컴파일 검증

```
./gradlew :hana-event:compileJava -q
→ 에러/경고 없이 성공(빈 출력).
```

IDE diagnostics 도 최종 0건.

## 7. 다른 AGENT 영향 (인계 사항)

### 11-B (digitaltwin) — 컬럼 계약 정합 (필수)
`tbl_alarm_history` 는 digitaltwin 의 `AlarmHistory` 엔티티가 ddl-auto 로 생성한다.
event-server 는 **INSERT 만** 하므로, 아래 컬럼명이 11-B 엔티티와 **정확히 일치**해야
한다. (현재 11-B 엔티티 미존재 — 11-B 작성 시 본 계약을 기준으로 정의 필요.)

INSERT 대상 컬럼(순서/이름):
`policy_id, policy_name, level, scope, device_id, device_key, point_id, tag_name,
point_type, condition_kind, value_raw, threshold_value, operator, trigger_value,
display_type, sop_template_id, icon, effect, effect_enabled, occurred_at, created_at`

타입 매핑(AlarmEvent → 컬럼):
- policy_id: Long / policy_name: String / level: String / scope: String
- device_id: Long / device_key: String / point_id: Long / tag_name: String / point_type: String
- condition_kind: String / value_raw: String / threshold_value: Double(nullable)
- operator: String(nullable) / trigger_value: String(nullable)
- display_type: String / sop_template_id: Long(nullable) / icon: String / effect: String
- effect_enabled: boolean(NOT NULL) / occurred_at: OffsetDateTime / created_at: OffsetDateTime(now)

주의: nullable 컬럼(threshold_value, operator, trigger_value, sop_template_id 등)은
INSERT 시 NULL 바인딩되므로 엔티티에서 nullable 허용해야 한다. 또한 조회 API(client)도
이 컬럼 계약을 따른다.

### 11-C (yml) — alarm 토픽 롤백
Kafka alarm 발행이 제거되었으므로 `HdtKafkaTopicProperties.alarm` 및 yml 의 alarm 토픽
설정은 더 이상 사용되지 않는다(11-C 에서 롤백). 본 작업에서는 yml/properties 미수정.

## 8. QA 요청 항목

1. FIRE_BELL 경로 무회귀 확인(EventHistoryDto 빌더 호환, insertEventHistory 무변경).
2. 리액티브 블로킹 호출 부재 확인(insertAlarmHistory 는 R2DBC bind, 블로킹 없음).
3. AlarmPublisher 삭제 후 잔여 참조/컴파일 문제 없음 확인.
4. `tbl_alarm_history` 컬럼 계약이 11-B AlarmHistory 엔티티와 정합되는지(11-B 착수 시
   교차 검증 필요) 표기 확인.
5. 범위 준수(hana-event/src 하위만, build.gradle/yml/신규 의존성 무변경) 확인.
