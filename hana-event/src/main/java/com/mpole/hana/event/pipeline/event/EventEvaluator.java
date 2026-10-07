package com.mpole.hana.event.pipeline.event;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.event.pipeline.event.dto.ActiveEvent;
import com.mpole.hana.event.pipeline.event.dto.EvalOutcome;
import com.mpole.hana.event.pipeline.event.dto.RuleIndex;
import com.mpole.hana.event.pipeline.event.dto.RuleView;
import com.mpole.hana.common.dto.EventClear;
import com.mpole.hana.common.dto.EventOccurrence;
import com.mpole.hana.common.dto.XnPointData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 규칙 매칭 + operator 평가(비교: GTE/LTE/GT/LT · 일치: EQ) + <b>Active 축</b> 상태 전이.
 *
 * <p>한 포인트 값에 대해 규칙마다 다음 넷 중 하나가 일어난다:</p>
 * <table border="1">
 *   <tr><th>조건</th><th>활성 이벤트</th><th>동작</th></tr>
 *   <tr><td>성립</td><td>없음</td><td><b>발생</b> — UUIDv7 생성 후 {@link EvalOutcome#occurred}</td></tr>
 *   <tr><td>성립</td><td>있음</td><td><b>무시</b> — 중복 억제(cooldown 불필요)</td></tr>
 *   <tr><td>해소</td><td>있음</td><td><b>해소</b> — {@link EvalOutcome#cleared}</td></tr>
 *   <tr><td>해소</td><td>없음</td><td>무시</td></tr>
 * </table>
 *
 * <p><b>deadband</b> — 활성 상태에서의 해소 판정에만 여유값을 적용한다. 임계값 바로 근처에서
 * 값이 흔들릴 때 발생/해소가 초당 반복되는 플래핑을 막는다. 예: 임계 80, deadband 2 →
 * 80 이상에서 발생하고, 78 아래로 떨어져야 해소된다.</p>
 *
 * <p>sustain 상태(firstBreachAt)는 인메모리이므로 프로세스 재기동 시 재계측된다. 반면 활성
 * 이벤트 자체는 {@link ActiveEventStore} 가 DB 에서 복원하므로 중복 발생은 생기지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventEvaluator {

    private final EventRuleCache cache;
    private final ActiveEventStore activeStore;

    /** UUIDv7 생성기. 스레드 세이프하므로 인스턴스를 재사용한다(호출마다 생성하면 낭비). */
    private static final TimeBasedEpochGenerator UUID_V7 = Generators.timeBasedEpochGenerator();

    /** key="{ruleId}|{deviceId}|{pointId}" → sustain 계측 상태. */
    private final Map<String, SustainState> sustainMap = new ConcurrentHashMap<>();

    private static final class SustainState {
        volatile Long firstBreachAtMs;  // 조건 최초 위반 시각(ms). 조건 해제 시 null.
    }

    /**
     * 한 번들의 모든 포인트를 평가해 발생/해소 결과 목록을 만든다.
     * 포인트마다 {@link #matchRules} 로 적용 규칙을 고르고, 규칙마다 {@link #evaluateRule} 을 돌린다.
     * (순차 스트림이어야 한다 — evaluateRule 이 sustainMap/activeStore 를 갱신한다.)
     */
    public List<EvalOutcome> evaluate(DeviceBundle bundle) {
        if (bundle == null || bundle.points() == null || bundle.points().isEmpty()) {
            return List.of();
        }
        RuleIndex idx = cache.current();
        long nowMs = System.currentTimeMillis();
        OffsetDateTime now = OffsetDateTime.now();

        return bundle.points().stream()
                .filter(point -> point.tagName() != null)
                .flatMap(point -> {
                    Long deviceId = point.deviceId() != null ? point.deviceId() : bundle.deviceId();
                    if (deviceId == null) return Stream.empty();
                    return matchRules(idx, deviceId, point.tagName())
                            .stream()
                            .map(rule -> evaluateRule(rule, point, deviceId, bundle.deviceKey(), nowMs, now))
                            ;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 포인트 하나에 적용할 규칙을 고른다 — 세 단계를 <b>순서대로</b> 거치며, 앞 단계가 결정하면 뒤는 보지 않는다.
     * <ol>
     *   <li><b>장비 마스터 스위치</b> — {@code tbl_device.alarm_state=false} 면 규칙 매칭 자체를 하지 않는다.</li>
     *   <li><b>개별 장비 규칙</b> — (deviceId, tag) 의 DEVICE 규칙이 있으면 <b>그것만</b> 쓴다(카테고리 규칙 override).</li>
     *   <li><b>카테고리 상속</b> — 없으면 장비 소분류(depth=2)의 CATEGORY 규칙을 쓴다.
     *       대/중분류 규칙은 캐시 빌드 때 하위 소분류로 미리 전개돼 있다({@code EventRuleCache.expandToSmallCategories}).</li>
     * </ol>
     * 세 단계의 조회 자체는 {@link RuleIndex} 가 O(1) 로 제공하고, 여기서는 <b>우선순위</b>만 정한다.
     */
    private static List<RuleView> matchRules(RuleIndex idx, Long deviceId, String tagName) {
        if (!idx.alarmEnabled(deviceId)) return List.of();                    // 1. 마스터 스위치

        List<RuleView> own = idx.deviceRules(deviceId, tagName);              // 2. 개별 장비 규칙
        if (!own.isEmpty()) return own;

        Long smallCat = idx.smallCategoryOf(deviceId);                         // 3. 소분류 카테고리 상속
        return smallCat == null ? List.of() : idx.categoryRules(smallCat, tagName);
    }

    private EvalOutcome evaluateRule(RuleView rule, XnPointData point, Long deviceId,
                                     String deviceKey, long nowMs, OffsetDateTime now) {
        String valueRaw = point.valueRaw();
        if (valueRaw == null) return null;

        // point_type 불일치 방어: 규칙이 특정 타입을 요구하고 포인트 타입이 있으면 비교, 다르면 skip.
        if (rule.pointType() != null && point.objectType() != null
                && !rule.pointType().isBlank()
                && !rule.pointType().equalsIgnoreCase(point.objectType())) {
            log.warn("[EventEvaluator] point_type 불일치 skip. ruleId={}, ruleType={}, pointType={}",
                    rule.ruleId(), rule.pointType(), point.objectType());
            return null;
        }

        Long pointId = point.pointId();
        // 사건형(suppress_mode=NONE)은 활성맵을 보지 않는다 — 버튼처럼 값이 거짓으로 돌아오지 않는 신호.
        boolean stateless = rule.isStateless();
        ActiveEvent entry = stateless ? null : activeStore.find(rule.ruleId(), deviceId, pointId);
        // 실제로 떠 있는 상태(해소 후 ack 대기는 조건 판정에서 활성으로 보지 않는다)
        boolean live = entry != null && !entry.awaitingAck();

        // 활성 중일 때만 deadband 를 적용한다 — 발생 임계는 그대로 두고 해소 임계만 늦춘다.
        double deadband = live ? rule.deadbandOrZero() : 0.0d;

        RuleView.RuleLevelView chosen;
        if (rule.isEquality()) {
            chosen = evaluateEquals(rule, valueRaw);
        } else if (rule.operator() != null) {
            Double value = parseDouble(valueRaw);
            if (value == null) return null; // 숫자 아님 skip
            chosen = evaluateThreshold(rule, value, deadband);
        } else {
            log.warn("[EventEvaluator] operator 없는 규칙 skip. ruleId={}", rule.ruleId());
            return null;
        }
        boolean conditionTrue = chosen != null;

        // ── 사건형: 활성·해소·sustain 없음. 성립한 메시지 1건 = 이벤트 1건(active=false 로 닫혀 기록) ──
        if (stateless) {
            return conditionTrue
                    ? EvalOutcome.occurredStateless(
                            toOccurrence(rule, chosen, point, deviceId, deviceKey, UUID_V7.generate(), valueRaw, now))
                    : null;
        }

        String stateKey = ActiveEvent.key(rule.ruleId(), deviceId, pointId);

        // ── 조건 해소 ────────────────────────────────────────────────
        if (!conditionTrue) {
            sustainMap.remove(stateKey);
            if (!live) {
                return null; // 원래 안 떠 있었음(또는 ack 대기 중) — 할 일 없음
            }
            // 미확인이면 맵에 "해소·미확인" 으로 남겨 확인 전까지 재발생을 억제하고, 확인됐으면 종료(제거).
            activeStore.clear(rule.ruleId(), deviceId, pointId);
            return EvalOutcome.cleared(EventClear.builder()
                    .eventId(entry.eventId())
                    .ruleId(rule.ruleId())
                    .eventCode(rule.eventCode())
                    .deviceId(deviceId)
                    .pointId(pointId)
                    .clearedValue(valueRaw)
                    .clearedAt(now)
                    .build());
        }

        // ── 조건 성립 + 맵에 항목 있음 ──────────────────────────────
        if (entry != null) {
            if (entry.awaitingAck()) {
                // 해소·미확인 상태에서 다시 성립 → 새 이벤트가 아니라 같은 eventId 재활성 (2026-10-02 B안).
                activeStore.reactivate(rule.ruleId(), deviceId, pointId);
                sustainMap.remove(stateKey);
                return EvalOutcome.reactivated(
                        toOccurrence(rule, chosen, point, deviceId, deviceKey, entry.eventId(), valueRaw, now));
            }
            return null; // 활성 중(확인 여부 무관) — 조건이 계속 성립하는 것이지 재발생이 아니다
        }

        // sustain(best-effort): 최초 위반 시각 기록 후 sustainSec 경과해야 발생 자격.
        SustainState st = sustainMap.computeIfAbsent(stateKey, k -> new SustainState());
        if (st.firstBreachAtMs == null) {
            st.firstBreachAtMs = nowMs;
        }
        if (rule.sustainSec() > 0 && (nowMs - st.firstBreachAtMs) / 1000L < rule.sustainSec()) {
            return null; // 지속시간 미달
        }

        // ── 발생 ─────────────────────────────────────────────────────
        // eventId 를 여기서 확정한다(UUIDv7). DB INSERT 응답을 기다릴 필요가 없고,
        // 웹소켓으로 나가는 id 와 정본 PK 가 같은 값이 된다.
        UUID eventId = UUID_V7.generate();
        ActiveEvent fresh = new ActiveEvent(eventId, rule.ruleId(), deviceId, pointId, chosen.level(), false,
                rule.ruleName(), rule.eventCode(), deviceKey, point.tagName(), point.pointDisplayName(), now, now);
        if (!activeStore.tryOccur(fresh)) {
            return null; // 동시 평가 경합 — 다른 스레드가 이미 발생시켰다
        }
        sustainMap.remove(stateKey);

        return EvalOutcome.occurred(toOccurrence(rule, chosen, point, deviceId, deviceKey, eventId, valueRaw, now));
    }

    /** 발생 스냅샷 — 상태형·사건형이 같은 모양으로 쓴다. */
    private static EventOccurrence toOccurrence(RuleView rule, RuleView.RuleLevelView chosen, XnPointData point,
                                                Long deviceId, String deviceKey, UUID eventId,
                                                String valueRaw, OffsetDateTime now) {
        return EventOccurrence.builder()
                .eventId(eventId)
                .ruleId(rule.ruleId())
                .ruleName(rule.ruleName())
                .eventCode(rule.eventCode())
                .level(chosen.level())
                .scope(rule.scope())
                .deviceId(deviceId)
                .deviceKey(deviceKey)
                .pointId(point.pointId())
                .tagName(point.tagName())
                .pointType(rule.pointType())
                // conditionKind 는 2026-10-01 폐기 — common DTO 필드만 남아 있고(null) tbl_event 컬럼은 DROP. operator 로 판별.
                .valueRaw(valueRaw)
                .thresholdValue(chosen.thresholdValue())
                .operator(rule.operator())
                .triggerValue(chosen.triggerValue())
                .outputs(chosen.outputs())
                .icon(chosen.icon())
                .effect(chosen.effect())
                .occurredAt(now)
                .occurrenceCount(1)           // 재활성이면 파이프라인이 DB 가 돌려준 값으로 덮어쓴다
                .build();
    }

    /**
     * THRESHOLD 평가: operator 로 각 레벨 threshold 를 비교, 만족 레벨 중 최고 심각도 선택.
     *
     * @param deadband 해소 판정 여유값. 0 이면 발생 판정(임계 그대로), 양수면 활성 상태의
     *                 유지 판정(임계를 완화해 쉽게 풀리지 않게 한다).
     */
    private RuleView.RuleLevelView evaluateThreshold(RuleView rule, double value, double deadband) {
        RuleView.RuleLevelView best = null;
        int bestSeverity = -1;
        for (RuleView.RuleLevelView lv : rule.levels()) {
            if (lv.thresholdValue() == null) continue;
            double threshold = relax(rule.operator(), lv.thresholdValue(), deadband);
            if (!compare(rule.operator(), value, threshold)) continue;
            int sev = severity(lv.level());
            if (sev > bestSeverity) {
                bestSeverity = sev;
                best = lv;
            }
        }
        return best;
    }

    /**
     * 활성 유지 판정용 임계값 완화. 상한 감시(GTE/GT)는 임계를 낮추고, 하한 감시(LTE/LT)는 높인다.
     * 예: 임계 80, deadband 2 → 78 아래로 내려가야 해소.
     */
    private static double relax(String operator, double threshold, double deadband) {
        if (deadband <= 0 || operator == null) return threshold;
        return switch (operator) {
            case "GTE", "GT" -> threshold - deadband;
            case "LTE", "LT" -> threshold + deadband;
            default -> threshold;
        };
    }

    /**
     * EQ(일치) 평가: 포인트 값이 어느 레벨의 triggerValue 집합(쉼표 구분, 예 "2,3") 에 속하면 그 레벨 성립.
     * 여러 레벨이 성립하면 최고 심각도 선택. (값이 이산적이라 deadband 개념이 없다.)
     *
     * <p>2026-10-01 이전의 TOGGLE(on/off 정규화, 규칙 단위 trigger_value)은 폐기했다 — gateway 가 보내는
     * "1.0" 도 문자열 상태값 "ok" 도 맞추지 못했고, 레벨별로 다른 값을 줄 수 없었다.
     * 비교 규칙은 {@link #valueMatches} 참고.</p>
     */
    private RuleView.RuleLevelView evaluateEquals(RuleView rule, String valueRaw) {
        RuleView.RuleLevelView best = null;
        int bestSeverity = -1;
        for (RuleView.RuleLevelView lv : rule.levels()) {
            if (lv.triggerValue() == null) continue;
            boolean matched = Stream.of(lv.triggerValue().split(","))
                    .anyMatch(trigger -> valueMatches(valueRaw, trigger));
            if (!matched) continue;
            int sev = severity(lv.level());
            if (sev > bestSeverity) {
                bestSeverity = sev;
                best = lv;
            }
        }
        return best;
    }

    private static boolean compare(String operator, double value, double threshold) {
        if (operator == null) return false;
        return switch (operator) {
            case "GTE" -> value >= threshold;
            case "LTE" -> value <= threshold;
            case "GT" -> value > threshold;
            case "LT" -> value < threshold;
            default -> false;
        };
    }

    /** CRITICAL > WARNING > CAUTION. 알 수 없는 값은 -1. */
    private static int severity(String level) {
        if (level == null) return -1;
        return switch (level) {
            case "CRITICAL" -> 3;
            case "WARNING" -> 2;
            case "CAUTION" -> 1;
            default -> 0;
        };
    }

    /**
     * 토글 값 일치 — 둘 다 숫자로 읽히면 숫자 동등 비교, 아니면 trim + 대소문자 무시 문자열 비교.
     * <p>숫자 비교가 먼저인 이유: gateway(View01ReadService)가 숫자 값을 {@code FORMAT(..., '0.0')} 으로
     * 보내 "3" 이 "3.0" 으로 도착한다. 문자열로만 비교하면 규칙 "3" 이 영원히 안 맞는다.</p>
     */
    private static boolean valueMatches(String actual, String trigger) {
        String a = actual.trim();
        String t = trigger.trim();
        if (t.isEmpty()) return false;
        Double an = parseDouble(a);
        Double tn = parseDouble(t);
        if (an != null && tn != null) return Double.compare(an, tn) == 0;
        return a.equalsIgnoreCase(t);
    }

    private static Double parseDouble(String raw) {
        if (raw == null) return null;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
