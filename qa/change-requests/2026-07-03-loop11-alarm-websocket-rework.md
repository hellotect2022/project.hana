# 코드 수정 승인 요청서

- 작성일: 2026-07-03
- Loop: 11 (알람 출력 경로 재작업 — kafka→websocket)
- 설계 문서: docs/architecture/2026-07-01-alarm-policy-redesign.md (출력 경로 정정)

## 0. 아키텍처 정정 (사용자 확정)
- event-server(hana-event): kafka 수신 → history 삽입/현재값 update → **event 판별 → websocket 전달**. **알람은 전부 event-server가 평가·전송.**
- digitaltwin: 외부 API·디바이스 제어(후속)·**client API**. 알람 파이프라인 미참여, **알람 이력 client 조회 API + 테이블 스키마**만 담당.
- 결정: (1) 알람을 **기존 이벤트 WebSocket 스트림에 통합**(eventType 구분), (2) **알람 전용 이력 테이블** 신규.

## 1. 이전 Loop 10 대비 정정
- **폐기**: alarm kafka 토픽(10-B) · AlarmPublisher(kafka, 10-C) · Phase4(digitaltwin consumer).
- **유지**: 평가엔진(AlarmPolicyReader/Cache/Evaluator, 10-C) · AlarmEvent DTO(10-A, 이제 websocket 페이로드 + 이력 컬럼 기준).

## 2. 수정된 흐름
```
gateway → kafka raw → hana-event [평가엔진] → ReactiveEventQueue.emitEventHistory(sink)
                                                 ├→ WebsocketBroadcastService → WebSocket 클라이언트
                                                 └→ EventSnapshotService → (ALARM) tbl_alarm_history / (그 외) tbl_device_event_history
```

## 3. 작업 분할
### 11-A (spring-backend, hana-event) — 출력 경로 교체
- `EventHistoryDto`: nullable `AlarmEvent alarm` 필드 추가(기존 data(DeviceBundle) 유지 → 기존 FIRE_BELL 흐름 무영향). eventType="ALARM"이면 alarm 세팅·data null.
- `AlarmEvaluationPipeline`: AlarmPublisher(kafka) 대신 EventHistoryDto(eventType="ALARM", alarm=AlarmEvent) 구성 → `reactiveEventQueue.emitEventHistory`/`WebsocketBroadcastService.publish`로 emit.
- `AlarmPublisher`(kafka) 삭제. HdtKafkaTopicProperties.alarm 필드 롤백(불필요) — 또는 미사용 유지.
- `EventSnapshotService`: eventType 분기 추가 — "ALARM"→insertAlarmHistory(tbl_alarm_history), 그 외→기존 insertEventHistory(tbl_device_event_history) 그대로. insertAlarmHistory(R2DBC INSERT) 신규.
- 기존 FIRE_BELL/현재값/스냅샷 경로 회귀 금지.

### 11-B (spring-backend, digitaltwin) — 이력 테이블 스키마 + client 조회 API
- `AlarmHistory` JPA 엔티티(tbl_alarm_history, ddl-auto 자동생성) — event-server R2DBC INSERT가 쓸 수 있게 테이블 확보.
- client 조회 API: GET /api/alarm/history (목록/필터: level/deviceId/기간/페이징), GET /{id}. (ACK/상태는 후속.)
- 컬럼 계약(양 모듈 정합): alarm_id PK, policy_id, policy_name, level, scope, device_id, device_key, point_id, tag_name, point_type, condition_kind, value_raw, threshold_value(Double), operator, trigger_value, display_type, sop_template_id, icon, effect, effect_enabled, occurred_at, created_at.

### 11-C (devops) — yml 롤백
- hana-event application.yml의 `hdt.kafka.topic.alarm`(10-B 추가분) 롤백(미사용). producer 직렬화는 유지 여부 무해(둬도 됨) — 최소 변경.

## 4. 위험도
MEDIUM
- 기존 EventHistoryDto/EventSnapshotService(작동 중 FIRE_BELL 경로) 수정 → 회귀 방지 필수(추가 필드/분기만, 기존 기본 동작 보존).
- 이력 테이블 스키마를 digitaltwin(JPA)이 생성하고 event-server(R2DBC)가 write → 컬럼명/타입 계약 정합 필수.
- ddl-auto 테이블 생성 순서(digitaltwin 먼저 기동) 인지.

## 5. 승인
사용자 아키텍처 정정·2결정(2026-07-03). 11-A + 11-B + 11-C → 통합 QA.
