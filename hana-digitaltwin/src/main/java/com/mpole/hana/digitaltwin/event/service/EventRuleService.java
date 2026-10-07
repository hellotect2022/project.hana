package com.mpole.hana.digitaltwin.event.service;

import com.mpole.hana.common.constant.SuppressMode;
import com.mpole.hana.digitaltwin.event.dto.EventRuleLevelItem;
import com.mpole.hana.digitaltwin.event.dto.EventRuleRequest;
import com.mpole.hana.digitaltwin.event.dto.EventRuleResponse;
import com.mpole.hana.digitaltwin.event.entity.EventRule;
import com.mpole.hana.digitaltwin.event.entity.EventRuleLevel;
import com.mpole.hana.digitaltwin.event.entity.RuleScope;
import com.mpole.hana.digitaltwin.event.repository.EventRuleRepository;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventRuleService {

    private final EventRuleRepository eventRuleRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;

    public EventRuleResponse create(EventRuleRequest req) {
        validate(req);

        EventRule rule = EventRule.builder()
                .ruleName(req.ruleName())
                .eventCode(normalizeEventCode(req.eventCode()))
                .description(req.description())
                .scope(req.scope())
                .categoryId(req.categoryId())
                .deviceId(req.deviceId())
                .tagName(req.tagName())
                .pointType(req.pointType())
                .operator(req.operator())
                .sustainSec(req.sustainSec())
                .deadband(req.deadband())
                .suppressMode(normalizeSuppressMode(req.suppressMode()))
                .active(req.active() != null ? req.active() : Boolean.TRUE)
                .build();

        applyLevels(rule, req.levels());

        EventRule saved = eventRuleRepository.save(rule);
        log.info("이벤트 규칙 생성: ruleId={}, name={}", saved.getRuleId(), saved.getRuleName());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EventRuleResponse> list() {
        return eventRuleRepository.findAllByOrderByRuleIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventRuleResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    public EventRuleResponse update(Long id, EventRuleRequest req) {
        validate(req);

        EventRule rule = findOrThrow(id);
        rule.setRuleName(req.ruleName());
        rule.setEventCode(normalizeEventCode(req.eventCode()));
        rule.setDescription(req.description());
        rule.setScope(req.scope());
        rule.setCategoryId(req.categoryId());
        rule.setDeviceId(req.deviceId());
        rule.setTagName(req.tagName());
        rule.setPointType(req.pointType());
        rule.setOperator(req.operator());
        rule.setSustainSec(req.sustainSec());
        rule.setDeadband(req.deadband());
        rule.setSuppressMode(normalizeSuppressMode(req.suppressMode()));
        rule.setActive(req.active() != null ? req.active() : Boolean.TRUE);

        // 레벨 전체 교체 (orphanRemoval 로 기존 레벨 삭제)
        rule.clearLevels();
        applyLevels(rule, req.levels());

        EventRule saved = eventRuleRepository.save(rule);
        log.info("이벤트 규칙 수정: ruleId={}", saved.getRuleId());
        return toResponse(saved);
    }

    public void delete(Long id) {
        EventRule rule = findOrThrow(id);
        eventRuleRepository.delete(rule);
        log.info("이벤트 규칙 삭제: ruleId={}", id);
    }

    private EventRule findOrThrow(Long id) {
        return eventRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이벤트 규칙을 찾을 수 없습니다. id=" + id));
    }

    /**
     * 소프트 참조(deviceId/categoryId)를 표시용 이름/경로로 resolve 해 응답을 만든다.
     * 규칙은 관리 화면용으로 건수가 적어 규칙별 조회로 처리한다.
     */
    private EventRuleResponse toResponse(EventRule rule) {
        String deviceName = null;
        if (rule.getDeviceId() != null) {
            deviceName = deviceRepository.findById(rule.getDeviceId())
                    .map(Device::getDeviceName)
                    .orElse(null);
        }
        return EventRuleResponse.from(rule, deviceName, buildCategoryPath(rule.getCategoryId()));
    }

    /** categoryId 소분류에서 부모 체인을 거슬러 "대 › 중 › 소" 경로 문자열을 만든다. */
    private String buildCategoryPath(Long categoryId) {
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

    private void applyLevels(EventRule rule, List<EventRuleLevelItem> items) {
        if (items == null) return;
        for (EventRuleLevelItem item : items) {
            EventRuleLevel level = EventRuleLevel.builder()
                    .level(item.level())
                    .thresholdValue(item.thresholdValue())
                    .triggerValue(item.triggerValue())
                    .icon(item.icon())
                    .effect(item.effect())
                    .displayOrder(item.displayOrder())
                    .outputs(item.outputs())
                    .build();
            rule.addLevel(level);
        }
    }

    /** 알람 종류 정리 — 미지정이면 UNTIL_ACKED(상태형 기본). 2026-10-02 WHILE_ACTIVE 삭제. */
    private static SuppressMode normalizeSuppressMode(SuppressMode raw) {
        return raw == null ? SuppressMode.UNTIL_ACKED : raw;
    }

    /**
     * 이벤트 코드 정리 — 공백 제거, 대문자. 비어 있으면 null(웹소켓은 "EVENT" 로 나간다).
     * 클라이언트가 문자열로 분기하므로 대소문자·공백 차이로 어긋나지 않게 서버에서 한 번 맞춘다.
     */
    private static String normalizeEventCode(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase();
        return v.isEmpty() ? null : v;
    }

    private void validate(EventRuleRequest req) {
        if (req.ruleName() == null || req.ruleName().isBlank()) {
            throw new IllegalArgumentException("정책명(ruleName)은 필수입니다.");
        }
        if (req.scope() == null) {
            throw new IllegalArgumentException("적용 범위(scope)는 필수입니다.");
        }
        if (req.scope() == RuleScope.CATEGORY && req.categoryId() == null) {
            throw new IllegalArgumentException("scope=CATEGORY 인 경우 categoryId 는 필수입니다.");
        }
        if (req.scope() == RuleScope.DEVICE && req.deviceId() == null) {
            throw new IllegalArgumentException("scope=DEVICE 인 경우 deviceId 는 필수입니다.");
        }
        if (req.operator() == null) {
            throw new IllegalArgumentException("판정 연산자(operator)는 필수입니다.");
        }
        if (req.operator().isEquality()) {
            boolean hasTrigger = req.levels() != null && req.levels().stream()
                    .anyMatch(l -> l.triggerValue() != null && !l.triggerValue().replace(",", "").isBlank());
            if (!hasTrigger) {
                throw new IllegalArgumentException("operator=EQ 인 경우 하나 이상의 레벨에 triggerValue 가 필요합니다(여러 값은 쉼표 구분).");
            }
        } else {
            boolean hasThreshold = req.levels() != null && req.levels().stream()
                    .anyMatch(l -> l.thresholdValue() != null);
            if (!hasThreshold) {
                throw new IllegalArgumentException("비교 연산자(GTE/LTE/GT/LT)인 경우 하나 이상의 레벨에 thresholdValue 가 필요합니다.");
            }
        }
        if (req.levels() != null && req.levels().stream().anyMatch(l -> Objects.isNull(l.level()))) {
            throw new IllegalArgumentException("각 레벨 항목의 level 값은 필수입니다.");
        }
    }
}
