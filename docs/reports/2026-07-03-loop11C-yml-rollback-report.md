# Loop 11-C 작업 보고서 — hana-event 미사용 Kafka alarm 토픽 설정 제거

- 작성일: 2026-07-03
- AGENT: devops-agent
- Loop: 11-C (재실행)
- 승인 요청서: `qa/change-requests/2026-07-03-loop11-alarm-websocket-rework.md`
- 선행: Loop 11-A 완료 (hana-event `AlarmPublisher` 삭제, 알람은 WebSocket 전송으로 전환)

---

## 1. 설정 변경 요약

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| `hdt.kafka.topic.alarm` | `hdt-alarm-events` | **제거** |
| `hdt.kafka.topic.raw` | `test2` | `test2` (유지) |
| `spring.kafka.producer` (key/value-serializer) | 존재 | 유지 (KafkaSender 빈 참조) |
| `spring.kafka.consumer` | 존재 | 불변 |

수정 파일: **`hana-event/src/main/resources/application.yml` (1개 파일만)**

변경 라인 (제거):
```
    topic:
      raw: test2
-     alarm: hdt-alarm-events
```

---

## 2. 전제 재검증 (읽기)

승인 요청서의 전제 조건을 grep으로 재확인했다.

- `AlarmPublisher.java` 삭제 확인
  - `grep AlarmPublisher hana-event` → **No files found** (Kafka 발행 클래스 삭제됨)
- `HdtKafkaTopicProperties.alarm()` 접근자 참조 활성 코드 없음
  - `grep '\.alarm\(\)' hana-event` 매치는 `EventSnapshotService.java:80 event.alarm()` 1건뿐이며, 이는 도메인 `EventHistoryDto`의 `alarm` 접근자(AlarmEvent 필드)로 Kafka 토픽 프로퍼티와 무관.
  - `AlarmEvaluationPipeline`은 `EventHistoryDto` 빌더의 `.alarm(alarm)` setter만 사용 → WebSocket/이벤트 싱크 경로. Kafka alarm 토픽 미참조.
- 결론: `hdt.kafka.topic.alarm` 는 **진짜 미사용 상태** → 제거 안전.

---

## 3. 영향 모듈

- **hana-event** (단일): alarm 토픽 설정 키만 제거. 알람 전송 경로는 이미 WebSocket으로 전환되어 있어 런타임 동작 변화 없음.
- 타 모듈(hana-digitaltwin, hana-gateway, hana-common) 영향 없음 — 본 키는 hana-event application.yml 로컬 설정.

---

## 4. 빌드/배포 영향

- YAML 파싱 유효성: **OK** (`yaml.safe_load` 통과).
- `spring.kafka.producer` 블록 유지로 `KafkaSender` 빈 생성 부수효과 없음.
- `HdtKafkaTopicProperties` 는 여전히 `hdt.kafka.topic` 바인딩 (raw 유효). `alarm` 필드는 기본값(null)로 바인딩되며 참조 코드가 없어 런타임 오류 없음.
- 재빌드/재기동 시 alarm 토픽 관련 프로듀서 동작 자체가 이미 없으므로 배포 영향 무해.

---

## 5. 위험도

**낮음(LOW).**
- 제거 대상이 미참조 설정 키 1개.
- producer/consumer/raw 등 활성 설정 불변.
- 단, 워킹카피(작업 전 git M 상태)에 이미 `spring.kafka.producer` 블록 추가가 미커밋으로 존재했음 → HEAD 대비 diff에는 producer 추가 + alarm 미포함이 함께 나타남. 이는 승인 요청서상 producer "유지" 방침과 일치하며 본 작업이 유발한 변경 아님.

---

## 6. 롤백 방안

```bash
# 파일 단위 롤백 (워킹카피 미커밋 상태 전체 되돌림 주의)
git checkout -- hana-event/src/main/resources/application.yml

# 또는 alarm 키만 수동 복구
#   hdt.kafka.topic 아래에 다음 라인 재추가
#       alarm: hdt-alarm-events
```

주의: 파일 단위 `git checkout`은 작업 전부터 존재하던 producer 블록 미커밋 변경까지 되돌린다. alarm 키만 복원하려면 수동 편집 권장.

---

## 7. 다른 AGENT 영향 (후속)

- hana-event `HdtKafkaTopicProperties.alarm` **필드(코드)** 가 미사용으로 남아 있다. 해당 record 필드 정리는 **spring-backend-agent 후속** 대상이며 이번 devops 범위 아님(코드 수정 금지 영역).
  - 대상 파일: `hana-event/src/main/java/com/mpole/hana/event/config/properties/HdtKafkaTopicProperties.java`
  - 권장: `alarm` 필드 제거 시 `KafkaConfig` 및 바인딩 영향 재검토 필요.

---

## 8. QA 요청

qa-code-reviewer 검증 요청. 체크 포인트:
- alarm 키 제거만 반영, raw/consumer/producer 불변 여부
- YAML 유효성
- alarm 토픽 미참조 전제 유지 여부
