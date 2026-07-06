# QA 검증 보고서 — Loop 11 알람 출력 Kafka→WebSocket 재작업 (11-A/11-B/11-C 통합)

- 작성일: 2026-07-03
- 검증자: qa-code-reviewer (검증 전용, 코드 미수정)
- 승인 요청서: `qa/change-requests/2026-07-03-loop11-alarm-websocket-rework.md`
- 작업 보고서: `docs/reports/2026-07-03-loop11A-alarm-websocket-report.md`, `.../loop11B-alarm-history-report.md`, `.../loop11C-yml-rollback-report.md`
- 대상: 11-A `hana-event/src`, 11-B `hana-digitaltwin` alarm 패키지, 11-C `hana-event` application.yml

---

## 전체 판정: **PASS (경미한 WARNING 후속 포함)**

핵심 2항목(컬럼 계약 정합 / FIRE_BELL 회귀 없음) 모두 **PASS**. 기능·컴파일·범위 정상.
git 위생(untracked 디렉터리) 및 무관 워킹카피 변경(SnapshotService)은 코드 결함이 아닌
후속 정리 항목으로 분류. 프론트(Phase5) 진행 **가능**.

---

## 1. 항목별 검증 결과

### [최중요] 1. 컬럼 계약 정합 — **PASS**
11-A `EventSnapshotService.insertAlarmHistory` R2DBC INSERT ↔ 11-B `AlarmHistory` 엔티티 @Column 1:1 대조.

- INSERT 컬럼(21개, `alarm_id` 제외): `policy_id, policy_name, level, scope, device_id, device_key, point_id, tag_name, point_type, condition_kind, value_raw, threshold_value, operator, trigger_value, display_type, sop_template_id, icon, effect, effect_enabled, occurred_at, created_at`
  - 근거: `EventSnapshotService.java:87-100`
- 엔티티 @Column: 위 21개 전부 동일 컬럼명으로 존재 + `alarm_id`(IDENTITY, `AlarmHistory.java:35-38`) + `updated_at`(DateEntity).
  - 근거: `AlarmHistory.java:40-100`, `DateEntity.java:14-18`
- `alarm_id`: `@GeneratedValue(IDENTITY)` → INSERT 제외 정확 (`AlarmHistory.java:36`).
- nullable 컬럼: `threshold_value`(Double), `operator`(String), `trigger_value`(String), `sop_template_id`(Long) — 엔티티에 `nullable=false` 미지정 → 생성 DDL nullable, 11-A는 `bindOrNull`으로 `bindNull(type)` 처리(`EventSnapshotService.java:116-120,137-139`). 일치.
- 타입: threshold_value=Double, sop_template_id=Long, effect_enabled=boolean(NOT NULL, `.bind`로 직접, `EventSnapshotService.java:123`), occurred_at/created_at=OffsetDateTime — 엔티티와 동일.
- bind 파라미터명↔AlarmEvent 접근자: 19개 `alarm.xxx()` 전부 `AlarmEvent` record 컴포넌트에 존재(`AlarmEvent.java:17-38`). 누락/오타 없음.
- `updated_at`은 INSERT에서 제외되나 엔티티 컬럼은 nullable → INSERT 실패 없음. 조회 응답엔 `updated_at` 포함(`AlarmHistoryResponse.java:35,62`).

결론: 런타임 INSERT 컬럼 불일치 위험 없음.

### [최중요] 2. FIRE_BELL 회귀 없음 — **PASS**
- `EventHistoryDto`는 record + `@Builder`. 신규 `AlarmEvent alarm` 컴포넌트 추가(`EventHistoryDto.java:17`)에도 기존 빌더 호출(EventService의 alarm 미지정)은 그대로 컴파일되고 `alarm=null`로 채워짐. 컴파일 통과가 이를 실증.
- `insertEventHistory`(FIRE_BELL/그 외) 경로 무변경 — 쿼리/바인딩 원본 유지(`EventSnapshotService.java:42-69`).
- 분기: `"ALARM".equals(event.eventType())` 일 때만 신규 경로, 그 외 전부 기존 `insertEventHistory`(`EventSnapshotService.java:33-36`). ALARM 이벤트는 `data=null`이지만 ALARM 브랜치에서만 처리되어 `event.data().deviceId()` 미도달 → NPE 없음.
- `EventService`/`ReactiveEventQueue`/`WebsocketBroadcastService`/`AlarmEvaluator` 미수정(git diff 및 보고서 확인).
- 현재값/스냅샷 파이프라인은 별도 sink(`deviceRawSink`)·별도 서비스(SnapshotService) — 알람 변경 무영향.

### 3. WebSocket 통합 경로 — **PASS**
- `AlarmEvaluationPipeline.emit`이 `EventHistoryDto(eventType="ALARM", alarm)` 구성 후 `websocketBroadcastService.publish(dto)` 단일 호출(`AlarmEvaluationPipeline.java:82-89`).
- `publish`는 내부에서 `reactiveEventQueue.emitEventHistory`로 `eventHistorySink`에 emit(`WebsocketBroadcastService.java:25-39`). 별도 `emitEventHistory` 직접 호출 없음 → 이중 emit 없음.
- `eventHistorySink`는 multicast → `streamJson()`(WebSocket 전송)과 `EventSnapshotService`(DB) 두 구독자 모두 동일 이벤트 수신(`ReactiveEventQueue.java:18-19,46-48`).
- 직렬화: `EventHistoryDto` record → `eventType`, `alarm` 포함(`WebsocketBroadcastService.toJson`, `EventHistoryDto.java`). 프론트가 `eventType`="ALARM"으로 구분 가능. FIRE_BELL은 `alarm=null` 직렬화.

### 4. 리액티브 준수 — **PASS**
- `insertAlarmHistory`는 순수 R2DBC `bind`/`bindNull`/`.then()`, 블로킹(`.block`/`sleep`) 없음(`EventSnapshotService.java:79-134`).
- `AlarmEvaluationPipeline`은 별도 스케줄러(`alarm-eval-thread`)에서 rawFlux 독립 구독, `onErrorResume`/`retryWhen` 격리 유지(`AlarmEvaluationPipeline.java:43-72`). 기존 구독자 무영향.
- 에러 보호: insertAlarmHistory `doOnError`+`onErrorResume(Mono.empty)`로 스트림 보존(`EventSnapshotService.java:132-133`).

### 5. AlarmPublisher 삭제 — **PASS (git 위생 WARNING)**
- `grep AlarmPublisher hana-event` → 매치 0. 파일 부재(glob 0). 잔여 참조 없음.
- 3개 모듈 컴파일 성공(아래 8번).
- `HdtKafkaTopicProperties.alarm` 필드는 `@DefaultValue("hdt-alarm-events")`로 잔존하나 미사용(`grep topic.alarm|hdt-alarm-events` 활성 참조 0). → 후속 정리 항목(코드 결함 아님).
- WARNING(위생): `hana-event/.../event/alarm/` 디렉터리 전체가 git untracked(`git ls-files` 결과 없음). AlarmPublisher.java는 애초 커밋된 적 없어 "추적된 삭제"로 남지 않음. 기능상 문제 없으나 커밋 시 alarm 패키지 전체(AlarmEvaluationPipeline 포함)를 함께 스테이징해야 이력 정합.

### 6. 11-C yml — **PASS**
- `hdt.kafka.topic.alarm` 제거 확인, `raw: test2` 유지(`application.yml:43-46`).
- `spring.kafka.consumer`/`producer` 블록 불변(`application.yml:23-42`). YAML 구조 유효(컴파일·기동 대상 파싱 문제 없음).

### 7. 범위 준수 — **부분 WARNING**
- 11-B: `hana-digitaltwin/.../alarm/` 신규 6파일만(entity/repository/dto×2/service/api). 타 도메인 무변경 — PASS.
- 11-A: `EventHistoryDto`, `AlarmEvaluationPipeline`, `EventSnapshotService`, AlarmPublisher 삭제 — 계획 일치.
- WARNING(무관 변경): 워킹카피에 Loop 11과 **무관한** 변경 존재.
  - `hana-event/.../service/SnapshotService.java`: `tbl_device_snapshot_last`→`tbl_device_current_value` 테이블명 변경(`git diff` 확인). 이는 최근 커밋 3542326 맥락의 선행 워킹카피 변경으로 Loop 11 산출물 아님. 커밋 분리 필요.
  - `hana-event/bin/` untracked(빌드 산출물) — .gitignore 대상.

### 8. 컴파일 — **PASS**
```
./gradlew :hana-common:compileJava :hana-event:compileJava :hana-digitaltwin:compileJava -q
→ EXIT=0 (에러/경고 없음)
```

---

## 2. 추가 확인(11-B 조회 API)
- `findByOptionalFilters` JPQL null-safe optional 필터 정확(`:level IS NULL OR ...` 4조건, `AlarmHistoryRepository.java:18-31`).
- 정렬 `occurredAt DESC` 고정(`AlarmHistoryController.java:58`), 페이징 응답 DTO 정합(`AlarmHistoryPageResponse.java`).
- `ApiResponse.success(String, T)` 오버로드 존재 → 컨트롤러 사용 유효.
- 단건 미존재 시 `IllegalArgumentException`(`AlarmHistoryService.java:47`) → GlobalExceptionHandler 처리 전제.

---

## 3. 프론트(Phase5) 진행 가능 여부: **가능**
- 조회 계약 확정: `GET /api/alarm/history`(level/deviceId/from/to/page/size, `ApiResponse<AlarmHistoryPageResponse>`), `GET /api/alarm/history/{id}`(`ApiResponse<AlarmHistoryResponse>`).
- WebSocket 실시간: `EventHistoryDto` 직렬화에 `eventType`="ALARM" + `alarm`(AlarmEvent) 포함 → 프론트 구분·렌더 가능.
- 단, 실기동 통합테스트(아래 후속) 미완이므로 프론트는 계약 기준 개발 진행하되 실데이터 검증은 통합테스트 후 확정 권장.

---

## 4. 후속 항목
1. **HdtKafkaTopicProperties.alarm 미사용 필드 정리**: `hana-event/.../config/properties/HdtKafkaTopicProperties.java`의 `alarm` 필드 제거(담당: spring-backend-agent). KafkaConfig 바인딩 영향 재검토.
2. **ddl-auto 기동 순서**: `tbl_alarm_history`는 digitaltwin(JPA)이 생성, event-server(R2DBC)가 INSERT → digitaltwin **선기동** 필수. 미기동 시 event INSERT는 테이블 부재로 실패(단, onErrorResume로 스트림은 보호). 운영 문서/배포 순서 명시 필요(devops-agent).
3. **tbl_alarm_history 인덱스**: 엔티티에 occurred_at/level/device_id 인덱스 정의됨(`AlarmHistory.java:22-26`). ddl-auto=update가 인덱스를 실제 생성하는지 실DB 확인(대량 이력 조회 성능).
4. **실기동 통합테스트**: kafka raw→평가→publish→WebSocket 전송 + tbl_alarm_history INSERT + `/api/alarm/history` 조회 E2E 미실시. 컬럼 계약은 정적 검증만 통과(런타임 INSERT 미실증).
5. **커밋 위생**:
   - alarm 패키지 untracked 전체(AlarmEvaluationPipeline 등)를 Loop 11 커밋에 포함.
   - 무관 변경(SnapshotService 테이블 리네임, HdtKafkaTopicProperties)은 별도 커밋 분리.
   - `hana-event/bin/` .gitignore 처리.

---

## 5. 수정 요청 대상 AGENT / 재검증 필요 여부
- **FAIL 없음 → 즉시 수정 승인 요청 불필요.** 코드 결함으로 인한 차단 항목 없음.
- 후속 1(spring-backend-agent), 후속 2(devops-agent)는 **다음 Loop**에서 처리 권장(현 Loop PASS 유지).
- 재검증 필요: **후속 4(실기동 통합테스트) 수행 시 재검증 권장.** 정적 계약은 PASS이나 런타임 INSERT/DDL 생성은 미실증.
