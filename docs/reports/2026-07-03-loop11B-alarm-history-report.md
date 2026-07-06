# Loop 11-B 작업 보고서 — Alarm History 스키마 + 조회 API (digitaltwin)

- 작성일: 2026-07-03
- AGENT: spring-backend-agent
- 승인 요청서: `qa/change-requests/2026-07-03-loop11-alarm-websocket-rework.md`
- 모듈: `hana-digitaltwin` (WebMVC / JPA, ddl-auto=update)
- 범위: `com.mpole.hana.digitaltwin.alarm` 하위 신규 파일만 추가. 기존 AlarmPolicy CRUD·타 도메인·yml·build.gradle 미변경, 신규 의존성 없음.

## 1. 작업 요약

알람 발생 이력 테이블(`tbl_alarm_history`)의 JPA 스키마(digitaltwin ddl-auto 로 생성)와 client 조회 전용 REST API를 신규 추가했다.
쓰기(INSERT)는 event-server(R2DBC, 11-A)가 담당하며, digitaltwin은 스키마 소유 + 읽기 전용 조회만 제공한다. 상태/ACK는 이번 범위 아님(후속).

## 2. 생성 파일 목록 (신규 5)

| 파일 | 역할 |
|---|---|
| `alarm/entity/AlarmHistory.java` | `@Entity @Table("tbl_alarm_history")`, `extends DateEntity`. 스키마 소유 |
| `alarm/repository/AlarmHistoryRepository.java` | `JpaRepository<AlarmHistory, Long>` + `findByOptionalFilters(level, deviceId, from, to, Pageable)` |
| `alarm/dto/AlarmHistoryResponse.java` | `record @Builder` + `static from(AlarmHistory)` |
| `alarm/dto/AlarmHistoryPageResponse.java` | 목록 페이징 응답 DTO (DevicePointMappingPageResponse 컨벤션) |
| `alarm/service/AlarmHistoryService.java` | `@Service @Transactional(readOnly=true)` — `getHistory(...)`, `get(id)` |
| `alarm/api/AlarmHistoryController.java` | `@RestController @RequestMapping("/api/alarm/history")` — 목록/단건 조회 |

## 3. 컬럼 계약 (event-server 11-A insertAlarmHistory 와 정합 필수)

| 필드 | 컬럼명 | 타입(JPA) | 비고 |
|---|---|---|---|
| alarmId | alarm_id | Long, `@Id @GeneratedValue(IDENTITY)` | PK |
| policyId | policy_id | Long | 소프트 참조 |
| policyName | policy_name | String | |
| level | level | String | AlarmLevel name(CAUTION/WARNING/CRITICAL) |
| scope | scope | String | AlarmScope name |
| deviceId | device_id | Long | |
| deviceKey | device_key | String | |
| pointId | point_id | Long | |
| tagName | tag_name | String | |
| pointType | point_type | String | |
| conditionKind | condition_kind | String | |
| valueRaw | value_raw | String | |
| thresholdValue | threshold_value | Double | nullable |
| operator | operator | String | nullable |
| triggerValue | trigger_value | String | nullable |
| displayType | display_type | String | |
| sopTemplateId | sop_template_id | Long | nullable |
| icon | icon | String | |
| effect | effect | String | |
| effectEnabled | effect_enabled | boolean | |
| occurredAt | occurred_at | OffsetDateTime | 발생시각 |
| createdAt | created_at | OffsetDateTime | DateEntity |
| updatedAt | updated_at | OffsetDateTime | DateEntity |

- level/scope/operator/conditionKind/pointType/displayType 등은 event-server가 문자열로 write 하므로 enum 매핑 강제하지 않고 `String` 저장.
- 인덱스 3종 추가: `occurred_at`, `level`, `device_id` (조회 필터/정렬 성능).

## 4. 조회 API 계약

### GET `/api/alarm/history` — 목록(페이징)
- 쿼리 파라미터(전부 선택, 미지정 시 조건 제외):
  - `level` (String)
  - `deviceId` (Long)
  - `from`, `to` (OffsetDateTime, ISO-8601 `DATE_TIME`) — occurredAt 범위
  - `page` (기본 0), `size` (기본 20)
- 정렬: `occurredAt DESC` 고정
- 응답: `ApiResponse<AlarmHistoryPageResponse>`
  - `AlarmHistoryPageResponse { content: AlarmHistoryResponse[], totalElements, totalPages, number, size, first, last }`

### GET `/api/alarm/history/{id}` — 단건
- 응답: `ApiResponse<AlarmHistoryResponse>`
- 미존재 시 `IllegalArgumentException` → GlobalExceptionHandler 처리

## 5. 기술 판단 근거

- **스키마 소유 = digitaltwin**: 계약 대로 JPA 엔티티로 정의, ddl-auto=update 로 생성. event-server(R2DBC)는 스키마를 만들지 않으므로 digitaltwin이 먼저 기동해 테이블이 생성되어야 event-server INSERT가 동작한다.
- **String 저장**: 두 서버가 서로 다른 enum 클래스를 갖는 상황에서 write/read 정합을 단순화하기 위해 코드값을 문자열로 저장. AlarmLevel 등 enum 강제 매핑 회피.
- **페이징**: 기존 `ApiResponse.success(msg, Page)` 오버로드 대신, 승인 요청서의 "목록 페이징 응답 DTO(기존 컨벤션)" 지시에 따라 `DevicePointMappingPageResponse` 스타일 전용 DTO를 만들어 프론트 계약을 명시적으로 노출.
- **읽기 전용**: 서비스 클래스 `@Transactional(readOnly=true)`. AlarmPolicy CRUD와 완전 분리.

## 6. 컴파일 검증

- `./gradlew :hana-digitaltwin:compileJava -q` → 오류 없이 성공.

## 7. 다른 AGENT 영향

- **11-A event-server (spring-backend-agent, R2DBC)**: `insertAlarmHistory`가 위 3절 컬럼명/타입으로 write 해야 함(정합 필수). 특히 `alarm_id`는 IDENTITY 자동생성이므로 INSERT 시 제외, `occurred_at`/nullable 컬럼 정합 확인 필요. 현재 hana-event 소스에 insertAlarmHistory·tbl_alarm_history 미존재(병렬 작업) — 구현 시 본 계약 준수.
- **기동 순서(devops-agent/운영)**: event-server R2DBC INSERT가 동작하려면 digitaltwin이 먼저 기동해 `tbl_alarm_history`를 생성해야 함.
- **Phase5 프론트 (react-frontend-agent)**: 알람 이력 조회 화면이 `GET /api/alarm/history` 및 `/{id}` 계약(4절)을 사용. 응답 래핑은 `ApiResponse<AlarmHistoryPageResponse>`.

## 8. QA 요청 항목

- 엔티티 컬럼명/타입이 승인 요청서 계약과 1:1 일치하는지 확인.
- 조회 필터 JPQL(null-safe optional filter) 정확성.
- alarm 패키지 외 파일 미변경 여부(범위 준수) 확인.
- ddl-auto 기동 순서 의존성 문서화 적절성.
