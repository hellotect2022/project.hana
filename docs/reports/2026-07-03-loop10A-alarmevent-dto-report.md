# AGENT 작업 보고서

## 1. 작업 AGENT
spring-backend-agent

## 2. 작업 일시
2026-07-03

## 3. 작업 목표
Loop 10-A. hana-event(발행)와 hana-digitaltwin(소비, Phase4)이 공유할 kafka 알람 메시지
계약 DTO를 hana-common에 신규 추가한다. 승인 요청서: `qa/change-requests/2026-07-03-loop10-alarm-eval-engine.md`.

## 4. 수정한 파일
없음. 기존 파일/build.gradle/타 모듈 무수정. 새 의존성 추가 없음.

## 5. 생성한 파일
- `hana-common/src/main/java/com/mpole/hana/common/dto/AlarmEvent.java` (신규 1개)

## 6. 주요 작업 내용
- `com.mpole.hana.common.dto.AlarmEvent` record 신규 생성 (record + lombok `@Builder`).
- 기존 `DeviceBundle`/`XnPointData` 스타일(record, `@Builder`, `OffsetDateTime`, 한 줄 필드 주석)을 그대로 따름.
- 필드 목록 (모두 String/기본타입/OffsetDateTime, enum 미참조):
  - `Long policyId` — 알람 정책 ID
  - `String policyName` — 알람 정책 명
  - `String level` — AlarmLevel 이름 (CAUTION/WARNING/CRITICAL)
  - `String scope` — 정책 적용 범위 (CATEGORY/DEVICE)
  - `Long deviceId` — 디바이스 ID
  - `String deviceKey` — 디바이스 식별 키
  - `Long pointId` — 포인트 ID
  - `String tagName` — 포인트 태그명
  - `String pointType` — 포인트 타입 (AI/DI 등)
  - `String conditionKind` — 조건 종류 (THRESHOLD/TOGGLE)
  - `String valueRaw` — 발생 시점 포인트 원시값
  - `Double thresholdValue` — AI 임계값 (없으면 null)
  - `String operator` — 비교 연산자 (>=,<= 등, 없으면 null)
  - `String triggerValue` — TOGGLE 트리거값 (없으면 null)
  - `String displayType` — 표시 방식 (SIMPLE/SOP)
  - `Long sopTemplateId` — SOP 템플릿 ID (없으면 null)
  - `String icon` — 알람 표시 아이콘
  - `String effect` — 알람 표시 효과
  - `boolean effectEnabled` — 효과 사용 여부
  - `OffsetDateTime occurredAt` — 알람 발생 시각
- 클래스 Javadoc: hana-event 평가엔진이 발행하고 digitaltwin이 소비하는 알람 이벤트 계약임을 명시.

## 7. 기술적 판단 근거
- **enum 미참조 / String·기본타입만 사용**: 모듈 간 kafka 메시지 계약이 digitaltwin의 alarm
  도메인 enum(AlarmLevel, Scope, ConditionKind 등)에 컴파일 의존하면 hana-common이 도메인
  모듈에 역결합된다. 계약을 String으로 유지해 발행측/소비측이 독립적으로 enum 매핑하도록 한다.
- **record + `@Builder`**: 불변 계약 + 선택 필드(null 허용) 조립 편의를 위해 기존 공통 DTO
  컨벤션과 동일하게 채택.
- **`OffsetDateTime occurredAt`**: 기존 DTO(`DeviceBundle.timestamp`, `XnPointData.updateDatetime`)와
  동일 타입으로 타임존 정보를 보존하며 직렬화 일관성 유지.
- 검증: `./gradlew :hana-common:compileJava -q` → EXIT 0 (성공).

## 8. 다른 AGENT 영역 영향 여부
- **영향 있음(후속 Loop 예정, 이번 Loop에서는 미수정)**:
  - **Loop 10-C (hana-event)**: 알람 평가엔진이 `AlarmEvent` 타입으로 alarm 토픽에 발행하는 코드를 추가할 예정. hana-event가 이 계약을 producer로 사용.
  - **Phase4 (hana-digitaltwin)**: alarm 토픽 컨슈머가 `AlarmEvent`로 역직렬화하여 소비하는 코드를 추가할 예정.
  - 두 소비/발행 코드는 각각의 후속 Loop에서 승인 후 작성. 이번 작업은 계약 타입만 선반영이며 타 모듈 코드 변경 없음.
- **build.gradle / application.yml / DB**: 변경 요청 없음(신규 의존성/설정/스키마 불필요).

## 9. 남은 이슈
- 없음. 계약 필드 확정 후 10-C/Phase4에서 필드명 불일치가 없도록 이 보고서의 필드 목록을 단일 기준으로 사용.

## 10. QA 요청 사항
- 신규 파일이 hana-common/src 하위 1개인지, 기존 파일/빌드/타 모듈 무수정인지 확인.
- record 필드 20개가 승인 요청서 정의와 일치하는지 확인.
- enum/외부 도메인 의존이 없는지(String·기본타입·OffsetDateTime만) 확인.
- `:hana-common:compileJava` 컴파일 통과 확인.

## 11. 다음 Loop 제안
- Loop 10-C: hana-event 알람 평가엔진에서 `AlarmEvent` 발행 로직 구현.
- Phase4: hana-digitaltwin alarm 컨슈머에서 `AlarmEvent` 역직렬화/소비 구현.
