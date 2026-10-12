package com.mpole.hana.digitaltwin.event.service;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.device.repository.DevicePointRepository;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.device.service.DeviceCategoryCascadeService;
import com.mpole.hana.digitaltwin.event.dto.EventResponse;
import com.mpole.hana.digitaltwin.event.dto.EventRuleLevelItem;
import com.mpole.hana.digitaltwin.event.dto.EventSimulationDtos.AckResponse;
import com.mpole.hana.digitaltwin.event.dto.EventSimulationDtos.ScenarioOption;
import com.mpole.hana.digitaltwin.event.dto.EventSimulationDtos.SimulateRequest;
import com.mpole.hana.digitaltwin.event.dto.EventSimulationDtos.SimulateResponse;
import com.mpole.hana.digitaltwin.event.entity.Event;
import com.mpole.hana.digitaltwin.event.entity.EventLevel;
import com.mpole.hana.digitaltwin.event.entity.EventRule;
import com.mpole.hana.digitaltwin.event.entity.EventRuleLevel;
import com.mpole.hana.digitaltwin.event.entity.RuleScope;
import com.mpole.hana.digitaltwin.event.repository.EventRepository;
import com.mpole.hana.digitaltwin.event.repository.EventRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.mpole.hana.digitaltwin.event.service.EventRuleService.SCENARIO_PREFIX;

/**
 * 모의 이벤트(SOP 시나리오 훈련) — Unity 가 REST 로 띄우고 REST 로 확인하는, event 서버와 분리된 세계. 2026-10-07.
 *
 * <h3>왜 digitaltwin 이 직접 쓰나</h3>
 * 실제 이벤트의 쓰기는 event 서버(활성맵 + 웹소켓)만 한다. 모의는 실시간성이 없고 트리거한 화면만 보면 되므로
 * event 서버를 거치지 않고 {@code tbl_event} 에 {@code simulated=true} 로 직접 넣는다. event 서버는 복원·재동기화 쿼리에서
 * simulated 행을 읽지 않아(활성맵에 안 오름) 실제 값이 들어와도 모의가 꺼지지 않고, 훈련 중 진짜 화재는 별도 행으로 정상 발생한다.
 * 유니크 인덱스도 {@code uk_event_open(… AND NOT simulated)} / {@code uk_event_sim_open(… AND simulated)} 로 나뉘어 서로 막지 않는다.
 *
 * <h3>시나리오 → 규칙 해석</h3>
 * <pre>
 *   요청 eventCode = "SCENARIO_FIRE_DETECT"
 *     → tbl_event_rule WHERE event_code = 'SCENARIO_FIRE_DETECT' AND simulated = true   (정확 일치. 실제 규칙으로 떨어지지 않음)
 *     → 장비에 적용되는지(DEVICE 범위 일치 / CATEGORY 범위의 하위 소분류에 장비 소분류 포함)  — 평가기와 같은 기준
 *     → 규칙 tag_name 의 장비 포인트 → 레벨(요청값 or 최고 심각도) → 값(EQ 면 trigger_value 첫 값, 비교형이면 threshold_value)
 *     → 그 레벨의 outputs·icon·effect 스냅샷 → INSERT(UUIDv7, simulated=true)
 * </pre>
 * 모의 전용 규칙은 SOP·알람 정책 화면에서 "모의 시나리오 전용" 토글로 등록한다. 평가기는 그 규칙을 아예 안 읽으므로
 * {@code active} 는 의미가 없고, 여기서도 보지 않는다. 끝은 확인(ack) 하나다 — 해소 단계가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventSimulationService {

    private static final TimeBasedEpochGenerator UUID_V7 = Generators.timeBasedEpochGenerator();

    private final EventRuleRepository eventRuleRepository;
    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final DevicePointRepository devicePointRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceCategoryCascadeService categoryCascadeService;

    /** 드롭다운 — 모의 전용 규칙 전부(활성 여부 무관). 코드가 비어 있는 규칙은 고를 수 없으므로 제외. */
    public List<ScenarioOption> scenarios() {
        return eventRuleRepository.findBySimulatedTrueOrderByRuleIdDesc().stream()
                .filter(r -> r.getEventCode() != null && !r.getEventCode().isBlank())
                .map(r -> new ScenarioOption(
                        r.getEventCode(),
                        r.getRuleId(), r.getRuleName(), r.getDescription(),
                        r.getScope() == null ? null : r.getScope().name(),
                        r.getCategoryId(), categoryPath(r.getCategoryId()),
                        r.getDeviceId(), deviceName(r.getDeviceId()),
                        r.getTagName(),
                        sortedLevels(r).stream().map(EventSimulationService::toLevelItem).toList()))
                .toList();
    }

    /** 규칙 API 응답(EventRuleResponse)과 같은 레벨 항목 — outputs(팝업/SOP 템플릿/CCTV)까지 그대로. */
    private static EventRuleLevelItem toLevelItem(EventRuleLevel l) {
        return EventRuleLevelItem.builder()
                .level(l.getLevel())
                .thresholdValue(l.getThresholdValue())
                .triggerValue(l.getTriggerValue())
                .icon(l.getIcon())
                .effect(l.getEffect())
                .displayOrder(l.getDisplayOrder())
                .outputs(l.getOutputs())
                .build();
    }

    /** 시나리오가 적용되는 장비 id — Unity 가 클릭 가능한 장비만 켜는 용도. */
    public List<Long> applicableDevices(String eventCode) {
        EventRule rule = resolveRule(eventCode);
        if (rule.getScope() == RuleScope.DEVICE) {
            return rule.getDeviceId() == null ? List.of() : List.of(rule.getDeviceId());
        }
        List<Long> smallIds = categoryCascadeService.resolveSmallCategoryIdsForFilter(rule.getCategoryId());
        if (smallIds == null || smallIds.isEmpty()) return List.of();
        return deviceRepository.findByCategory_CategoryIdIn(smallIds).stream()
                .map(Device::getDeviceId)
                .sorted()
                .toList();
    }

    /** 발생. 같은 규칙+장비+포인트에 열린 모의 행이 있으면 새로 만들지 않고 그 행을 {@code alreadyActive=true} 로 돌려준다. */
    @Transactional
    public SimulateResponse simulate(SimulateRequest req, Long userId) {
        if (req == null || req.deviceId() == null) {
            throw new IllegalArgumentException("deviceId 는 필수입니다.");
        }
        EventRule rule = resolveRule(req.eventCode());
        String eventCode = rule.getEventCode();

        Device device = deviceRepository.findById(req.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("장비를 찾을 수 없습니다. deviceId=" + req.deviceId()));
        assertApplies(rule, device, eventCode);

        if (rule.getTagName() == null || rule.getTagName().isBlank()) {
            throw new IllegalArgumentException("규칙에 태그가 없어 포인트를 정할 수 없습니다. ruleId=" + rule.getRuleId());
        }
        DevicePoint point = devicePointRepository.findByDevice_DeviceIdAndTagName(device.getDeviceId(), rule.getTagName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "장비에 규칙 태그의 포인트가 없습니다. deviceId=" + device.getDeviceId() + ", tag=" + rule.getTagName()));

        EventRuleLevel chosen = chooseLevel(rule, req.level());
        String valueRaw = triggerValueOf(rule, chosen);

        Optional<Event> open = eventRepository.findOpenSimulated(rule.getRuleId(), device.getDeviceId(), point.getPointId());
        if (open.isPresent()) {
            log.info("[EventSimulation] 이미 열린 모의 이벤트 반환. eventId={}, eventCode={}, deviceId={}",
                    open.get().getEventId(), eventCode, device.getDeviceId());
            return SimulateResponse.of(open.get(), device.getDeviceName(), true);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Event e = Event.builder()
                .eventId(UUID_V7.generate())
                .ruleId(rule.getRuleId())
                .ruleName(rule.getRuleName())
                .eventCode(eventCode)                      // SCENARIO_… 그대로 — 이력에서 실제 코드와 처음부터 구분된다
                .level(chosen.getLevel().name())
                .scope(rule.getScope() == null ? null : rule.getScope().name())
                .deviceId(device.getDeviceId())
                .deviceKey(device.getDeviceKey())
                .pointId(point.getPointId())
                .tagName(point.getTagName())
                .pointType(point.getPointType())
                .valueRaw(valueRaw)
                .thresholdValue(chosen.getThresholdValue())
                .operator(rule.getOperator() == null ? null : rule.getOperator().name())
                .triggerValue(chosen.getTriggerValue())
                .outputs(chosen.getOutputs())
                .icon(chosen.getIcon())
                .effect(chosen.getEffect())
                .occurredAt(now)
                .lastOccurredAt(now)
                .occurrenceCount(1)
                .simulated(true)
                .simulatedBy(userId)
                .build();
        eventRepository.save(e);
        log.info("[EventSimulation] 모의 이벤트 발생. eventId={}, eventCode={}, ruleId={}, deviceId={}, pointId={}, level={}, by={}",
                e.getEventId(), eventCode, rule.getRuleId(), device.getDeviceId(), point.getPointId(), e.getLevel(), userId);
        return SimulateResponse.of(e, device.getDeviceName(), false);
    }

    /** 확인 = 종료. 모의 행만 대상이라 실제 이벤트는 건드리지 못한다. 이미 확인된 건은 기존 값 그대로 반환. */
    @Transactional
    public AckResponse ack(UUID eventId, Long userId) {
        Event before = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다. id=" + eventId));
        if (!before.isSimulated()) {
            throw new IllegalArgumentException("모의 이벤트가 아닙니다. 실제 이벤트 확인은 웹소켓 ACK 로 보내세요. id=" + eventId);
        }
        int updated = eventRepository.ackSimulated(eventId, userId, OffsetDateTime.now());
        Event after = eventRepository.findById(eventId).orElse(before);
        if (updated > 0) {
            log.info("[EventSimulation] 모의 이벤트 확인(종료). eventId={}, by={}", eventId, userId);
        }
        return new AckResponse(eventId, after.getAckedAt(), after.getAckedBy(), after.getClearedAt(), updated == 0);
    }

    /** 지금 열린 모의 이벤트 — Unity 재접속 복구용. */
    public List<EventResponse> active() {
        return eventRepository.findBySimulatedTrueAndClearedAtIsNullOrderByOccurredAtDesc().stream()
                .map(EventResponse::from)
                .toList();
    }

    // ───────────────────────── 해석 ─────────────────────────

    /** 모의 전용 규칙을 코드로 정확히 찾는다. 같은 코드가 여럿이면 최신(ruleId 큰) 것. */
    private EventRule resolveRule(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("eventCode 는 필수입니다. 예: " + SCENARIO_PREFIX + "FIRE_DETECT");
        }
        String eventCode = raw.trim().toUpperCase();
        if (!eventCode.startsWith(SCENARIO_PREFIX)) {
            throw new IllegalArgumentException("eventCode 는 " + SCENARIO_PREFIX + " 로 시작하는 모의 전용 규칙 코드여야 합니다. 받은 값=" + raw);
        }
        List<EventRule> rules = eventRuleRepository.findByEventCodeAndSimulatedTrueOrderByRuleIdDesc(eventCode);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("시나리오 규칙이 없습니다. SOP·알람 정책에서 '모의 시나리오 전용' 으로 코드 "
                    + eventCode + " 규칙을 등록하세요.");
        }
        if (rules.size() > 1) {
            log.warn("[EventSimulation] 같은 코드의 모의 규칙이 {}개 — 최신 ruleId={} 사용. eventCode={}",
                    rules.size(), rules.get(0).getRuleId(), eventCode);
        }
        return rules.get(0);
    }

    /** 평가기와 같은 기준 — DEVICE 범위면 장비 일치, CATEGORY 범위면 규칙 분류의 하위 소분류에 장비 소분류가 포함. */
    private void assertApplies(EventRule rule, Device device, String eventCode) {
        if (rule.getScope() == RuleScope.DEVICE) {
            if (!Objects.equals(rule.getDeviceId(), device.getDeviceId())) {
                throw new IllegalArgumentException("이 장비에 적용되는 규칙이 아닙니다(DEVICE 범위 규칙은 장비 "
                        + rule.getDeviceId() + " 전용). eventCode=" + eventCode + ", deviceId=" + device.getDeviceId());
            }
            return;
        }
        Long deviceCategoryId = device.getCategory() == null ? null : device.getCategory().getCategoryId();
        List<Long> smallIds = categoryCascadeService.resolveSmallCategoryIdsForFilter(rule.getCategoryId());
        if (deviceCategoryId == null || smallIds == null || !smallIds.contains(deviceCategoryId)) {
            throw new IllegalArgumentException("이 장비에 적용되는 규칙이 아닙니다(장비 분류가 규칙 분류 밖). eventCode="
                    + eventCode + ", deviceId=" + device.getDeviceId() + ", ruleCategoryId=" + rule.getCategoryId());
        }
    }

    private static List<EventRuleLevel> sortedLevels(EventRule rule) {
        return rule.getLevels().stream()
                .filter(l -> l.getLevel() != null)
                .sorted(Comparator.comparingInt(l -> l.getLevel().ordinal()))   // EventLevel 선언 순서 = 심각도 오름차순
                .toList();
    }

    private static EventRuleLevel chooseLevel(EventRule rule, String requested) {
        List<EventRuleLevel> levels = sortedLevels(rule);
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("규칙에 레벨이 없습니다. ruleId=" + rule.getRuleId());
        }
        if (requested == null || requested.isBlank()) {
            return levels.get(levels.size() - 1);                               // 최고 심각도
        }
        EventLevel want;
        try {
            want = EventLevel.valueOf(requested.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("level 은 CAUTION/WARNING/CRITICAL 중 하나입니다. 받은 값=" + requested);
        }
        return levels.stream().filter(l -> l.getLevel() == want).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("규칙에 " + want + " 레벨이 없습니다. ruleId=" + rule.getRuleId()
                        + ", 가능한 레벨=" + levels.stream().map(l -> l.getLevel().name()).toList()));
    }

    /** 발생 순간의 "값" — 실제라면 포인트에서 왔을 값. EQ 면 매칭값 첫 항목, 비교형이면 임계값 자체. */
    private static String triggerValueOf(EventRule rule, EventRuleLevel level) {
        if (rule.getOperator() != null && rule.getOperator().isEquality()) {
            String tv = level.getTriggerValue();
            if (tv == null || tv.isBlank()) return null;
            return tv.split(",")[0].trim();
        }
        return level.getThresholdValue() == null ? null : String.valueOf(level.getThresholdValue());
    }

    private String deviceName(Long deviceId) {
        if (deviceId == null) return null;
        return deviceRepository.findById(deviceId).map(Device::getDeviceName).orElse(null);
    }

    /** "대 › 중 › 소" 경로 — EventRuleService.buildCategoryPath 와 같은 방식(드롭다운 표시용). */
    private String categoryPath(Long categoryId) {
        if (categoryId == null) return null;
        DeviceCategory cat = deviceCategoryRepository.findById(categoryId).orElse(null);
        if (cat == null) return null;
        Deque<String> parts = new ArrayDeque<>();
        DeviceCategory cur = cat;
        int guard = 0;
        while (cur != null && guard++ < 8) {
            parts.addFirst(cur.getCategoryName());
            cur = cur.getParent();
        }
        return String.join(" › ", parts);
    }
}
