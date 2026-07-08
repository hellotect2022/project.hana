package com.mpole.hana.event.alarm;

import com.mpole.hana.common.dto.AlarmEvent;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.XnPointData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 정책 매칭 + THRESHOLD/TOGGLE 평가 + sustain/cooldown 상태 관리.
 *
 * <p>상태(firstBreachAt/lastFiredAt)는 인메모리 ConcurrentHashMap 이므로 프로세스 재기동 시
 * 소실된다(sustain 재계측, cooldown 초기화). 이는 best-effort 설계 한계다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmEvaluator {

    private final AlarmPolicyCache cache;

    /** key="{policyId}|{deviceId}" → 평가 상태. */
    private final Map<String, EvalState> stateMap = new ConcurrentHashMap<>();

    /** on 으로 해석하는 토글 정규화 값. */
    private static final Set<String> TOGGLE_ON = Set.of("1", "true", "on");
    /** off 로 해석하는 토글 정규화 값. */
    private static final Set<String> TOGGLE_OFF = Set.of("0", "false", "off");

    private static final class EvalState {
        volatile Long firstBreachAtMs;  // 조건 최초 위반 시각(ms). 조건 해제 시 null.
        volatile Long lastFiredAtMs;    // 마지막 발생 시각(ms). cooldown 계산용.
    }

    /**
     * 한 번들의 모든 포인트를 평가해 발생 알람 목록을 만든다.
     */
    public List<AlarmEvent> evaluate(DeviceBundle bundle) {
        if (bundle == null || bundle.points() == null || bundle.points().isEmpty()) {
            return List.of();
        }
        PolicyIndex idx = cache.current();
        long nowMs = System.currentTimeMillis();
        OffsetDateTime now = OffsetDateTime.now();

        List<AlarmEvent> out = new ArrayList<>();

        for (XnPointData point : bundle.points()) {
            String tagName = point.tagName();
            Long deviceId = point.deviceId() != null ? point.deviceId() : bundle.deviceId();
            if (tagName == null || deviceId == null) continue;

            // 우선순위: (device, tag) 개별 정책이 있으면 그것만 적용(카테고리 override),
            // 없으면 그 디바이스 소분류의 카테고리 정책을 상속. 태그별로 독립 해석.
            List<PolicyView> matched = new ArrayList<>(idx.devicePolicies(deviceId, tagName));
            if (matched.isEmpty()) {
                Long smallCat = idx.smallCategoryOf(deviceId);
                if (smallCat != null) {
                    matched.addAll(idx.categoryPolicies(smallCat, tagName));
                }
            }
            if (matched.isEmpty()) continue;

            for (PolicyView policy : matched) {
                AlarmEvent ev = evaluatePolicy(policy, point, deviceId, bundle.deviceKey(), nowMs, now);
                if (ev != null) out.add(ev);
            }
        }
        return out;
    }

    private AlarmEvent evaluatePolicy(PolicyView policy, XnPointData point, Long deviceId,
                                      String deviceKey, long nowMs, OffsetDateTime now) {
        String valueRaw = point.valueRaw();
        if (valueRaw == null) return null;

        // point_type 불일치 방어: 정책이 특정 타입을 요구하고 포인트 타입이 있으면 비교, 다르면 skip.
        if (policy.pointType() != null && point.objectType() != null
                && !policy.pointType().isBlank()
                && !policy.pointType().equalsIgnoreCase(point.objectType())) {
            log.debug("[AlarmEvaluator] point_type 불일치 skip. policyId={}, policyType={}, pointType={}",
                    policy.policyId(), policy.pointType(), point.objectType());
            return null;
        }

        PolicyView.PolicyLevelView chosen;
        Double thresholdOfChosen = null;

        if ("THRESHOLD".equals(policy.conditionKind())) {
            Double value = parseDouble(valueRaw);
            if (value == null) return null; // 숫자 아님 skip
            chosen = evaluateThreshold(policy, value);
            if (chosen != null) thresholdOfChosen = chosen.thresholdValue();
        } else if ("TOGGLE".equals(policy.conditionKind())) {
            chosen = evaluateToggle(policy, valueRaw);
        } else {
            log.warn("[AlarmEvaluator] 알 수 없는 conditionKind skip. policyId={}, kind={}",
                    policy.policyId(), policy.conditionKind());
            return null;
        }

        boolean conditionTrue = chosen != null;
        String stateKey = policy.policyId() + "|" + deviceId;
        EvalState st = stateMap.computeIfAbsent(stateKey, k -> new EvalState());

        if (!conditionTrue) {
            // 조건 해제 → sustain 리셋. cooldown(lastFiredAt)은 유지.
            st.firstBreachAtMs = null;
            return null;
        }

        // sustain(best-effort): 최초 위반 시각 기록 후 sustainSec 경과해야 발생 자격.
        if (st.firstBreachAtMs == null) {
            st.firstBreachAtMs = nowMs;
        }
        if (policy.sustainSec() > 0) {
            long elapsedSec = (nowMs - st.firstBreachAtMs) / 1000L;
            if (elapsedSec < policy.sustainSec()) {
                return null; // 지속시간 미달
            }
        }

        // cooldown: 최근 발생 후 cooldownSec 이내면 억제.
        if (policy.cooldownSec() > 0 && st.lastFiredAtMs != null) {
            long sinceFiredSec = (nowMs - st.lastFiredAtMs) / 1000L;
            if (sinceFiredSec < policy.cooldownSec()) {
                return null;
            }
        }

        st.lastFiredAtMs = nowMs;

        return AlarmEvent.builder()
                .policyId(policy.policyId())
                .policyName(policy.policyName())
                .level(chosen.level())
                .scope(policy.scope())
                .deviceId(deviceId)
                .deviceKey(deviceKey)
                .pointId(point.pointId())
                .tagName(point.tagName())
                .pointType(policy.pointType())
                .conditionKind(policy.conditionKind())
                .valueRaw(valueRaw)
                .thresholdValue(thresholdOfChosen)
                .operator(policy.operator())
                .triggerValue(policy.triggerValue())
                .displayType(policy.displayType())
                .sopTemplateId(policy.sopTemplateId())
                .icon(chosen.icon())
                .effect(chosen.effect())
                .effectEnabled(policy.effectEnabled())
                .occurredAt(now)
                .build();
    }

    /**
     * THRESHOLD 평가: operator 로 각 레벨 threshold 를 비교, 만족 레벨 중 최고 심각도 선택.
     */
    private PolicyView.PolicyLevelView evaluateThreshold(PolicyView policy, double value) {
        PolicyView.PolicyLevelView best = null;
        int bestSeverity = -1;
        for (PolicyView.PolicyLevelView lv : policy.levels()) {
            if (lv.thresholdValue() == null) continue;
            if (!compare(policy.operator(), value, lv.thresholdValue())) continue;
            int sev = severity(lv.level());
            if (sev > bestSeverity) {
                bestSeverity = sev;
                best = lv;
            }
        }
        return best;
    }

    /**
     * TOGGLE 평가: valueRaw 를 on/off 로 정규화 후 정책 triggerValue 와 비교.
     * 일치 시 정책 레벨(단일이면 그것, 여러 개면 최고 심각도) 선택.
     */
    private PolicyView.PolicyLevelView evaluateToggle(PolicyView policy, String valueRaw) {
        Boolean actual = normalizeToggle(valueRaw);
        Boolean trigger = normalizeToggle(policy.triggerValue());
        if (actual == null || trigger == null) return null;
        if (!actual.equals(trigger)) return null;

        PolicyView.PolicyLevelView best = null;
        int bestSeverity = -1;
        for (PolicyView.PolicyLevelView lv : policy.levels()) {
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

    private static Boolean normalizeToggle(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase();
        if (TOGGLE_ON.contains(v)) return Boolean.TRUE;
        if (TOGGLE_OFF.contains(v)) return Boolean.FALSE;
        return null;
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
