# 코드 수정 승인 요청서

- 작성일: 2026-07-03
- Loop: 10 (알람 정책 Phase 3 — hana-event 평가엔진)
- 설계 문서: docs/architecture/2026-07-01-alarm-policy-redesign.md
- 조사 근거: hana-event 구조 조사(EventService/KafkaConfig/DatabaseConfig/InitKafkaConsumer)

## 0. 목표
hana-event가 활성 알람 정책을 R2DBC로 로드·캐시하고, kafka raw 스트림의 포인트값을 정책과 대조 평가(THRESHOLD/TOGGLE)해 알람 발생 시 alarm 토픽으로 emit. (digitaltwin 소비는 Phase 4)

## 1. 서브 분할
### 10-A (spring-backend, hana-common) — kafka 메시지 계약
- 신규 `com.mpole.hana.common.dto.AlarmEvent` record(모듈 간 계약이므로 enum이 아닌 String 필드):
  policyId, policyName, level, scope, deviceId, deviceKey, pointId, tagName, pointType, conditionKind, valueRaw, thresholdValue(Double), operator, triggerValue, displayType, sopTemplateId(Long), icon, effect, effectEnabled, occurredAt(OffsetDateTime).
- hana-common은 순수 record(기존 DeviceBundle 스타일). 새 의존성 없음.

### 10-B (devops, hana-event application.yml) — alarm 토픽
- `hdt.kafka.topic.alarm: <토픽명>` 추가(기본 raw는 유지). 필요 시 producer 직렬화 설정 확인(이미 JsonSerializer 구성됨).

### 10-C (spring-backend, hana-event) — 평가엔진
- `AlarmPolicyReader`: DatabaseClient로 tbl_alarm_policy(+level LEFT JOIN, active=true), tbl_device(device_id,category_id), tbl_device_category(category_id,parent,depth) 로드.
- `AlarmPolicyCache`: 인메모리 캐시(정책 인덱스 + device→소분류 map + 카테고리 서브트리 전개). 주기 리프레시(스케줄). 인덱스: CATEGORY 정책은 category_id를 하위 소분류 집합으로 전개해 (소분류categoryId, tagName)→정책, DEVICE 정책은 (deviceId, tagName)→정책.
- `AlarmEvaluator`: 포인트별 매칭 정책 평가. THRESHOLD(valueRaw 파싱→operator로 레벨별 thresholdValue 비교, 최고 심각도 산출), TOGGLE(valueRaw==triggerValue→해당 레벨). cooldown(재발방지, (policyId,deviceId,level)별 마지막 발생), sustain(best-effort: 최초 위반시각 추적, sustainSec 경과 시 발생, 해제 시 리셋). 상태는 인메모리(재기동 시 소실 — 한계 명시).
- `AlarmPublisher`: 기존 KafkaSender Bean 재사용 → alarm 토픽 발행.
- EventService.startEventPipeline에 평가 단계 연결(기존 하드코딩 필터 대체, 단 기존 WebSocket/스냅샷 파이프라인 회귀 없게).

## 2. 위험도
MEDIUM~HIGH (리액티브·상태·정합성)
- 정책/디바이스/카테고리 캐시 일관성(변경 반영 지연 = 리프레시 주기).
- device→category 매핑 규모(대량 device 로드) → 메모리/리프레시 비용.
- valueRaw 파싱(숫자 아님/단위)·DI trigger 표현.
- sustain/cooldown 인메모리 상태 재기동 내구성 없음(한계).
- 알람 스톰 억제(쿨다운으로 1차 방어).
- 기존 EventService 스냅샷/WebSocket 파이프라인 회귀 방지.

## 3. 승인
사용자 "phase3 착수" 승인(2026-07-03). 10-A + 10-B(병렬) → 10-C → QA. Phase 4(digitaltwin alarm 소비)는 별도.
