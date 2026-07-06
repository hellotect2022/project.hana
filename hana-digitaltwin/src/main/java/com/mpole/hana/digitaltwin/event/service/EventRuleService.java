package com.mpole.hana.digitaltwin.event.service;

import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventRuleRequest;
import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventRuleResponse;
import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;
import com.mpole.hana.digitaltwin.event.entity.EventRule;
import com.mpole.hana.digitaltwin.event.repository.EventRuleRepository;
import com.mpole.hana.digitaltwin.sop.entity.SopTemplate;
import com.mpole.hana.digitaltwin.sop.repository.SopTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventRuleService {

    private final EventRuleRepository ruleRepository;
    private final SopTemplateRepository sopTemplateRepository;

    @Transactional(readOnly = true)
    public List<EventRuleResponse> findAll() {
        return ruleRepository.findAllByOrderByRuleNameAsc().stream()
                .map(EventRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventRuleResponse findById(Long ruleId) {
        return ruleRepository.findByIdWithSopTemplate(ruleId)
                .map(EventRuleResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("이벤트 규칙을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<EventRuleResponse> findByDevice(Long deviceId) {
        return ruleRepository.findByDeviceIdOrderByRuleNameAsc(deviceId).stream()
                .map(EventRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventRuleResponse> findBySeverity(String severity) {
        return ruleRepository.findBySeverityOrderByRuleNameAsc(severity).stream()
                .map(EventRuleResponse::from)
                .toList();
    }

    @Transactional
    public EventRuleResponse create(EventRuleRequest request) {
        EventRule rule = mapToEntity(new EventRule(), request);
        ruleRepository.save(rule);
        return EventRuleResponse.from(rule);
    }

    @Transactional
    public EventRuleResponse update(Long ruleId, EventRuleRequest request) {
        EventRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("이벤트 규칙을 찾을 수 없습니다."));
        mapToEntity(rule, request);
        return EventRuleResponse.from(rule);
    }

    @Transactional
    public EventRuleResponse toggleActive(Long ruleId) {
        EventRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("이벤트 규칙을 찾을 수 없습니다."));
        rule.setActive(!Boolean.TRUE.equals(rule.getActive()));
        return EventRuleResponse.from(rule);
    }

    @Transactional
    public void delete(Long ruleId) {
        ruleRepository.deleteById(ruleId);
    }

    private EventRule mapToEntity(EventRule rule, EventRuleRequest request) {
        rule.setRuleName(request.ruleName().trim());
        rule.setDescription(request.description());
        rule.setDeviceId(request.deviceId());
        rule.setTagName(request.tagName());
        rule.setConditions(request.conditions());
        rule.setConditionLogic(request.conditionLogic() != null ? request.conditionLogic() : "AND");
        rule.setSustainDurationSec(request.sustainDuration() != null ? request.sustainDuration() : 0);
        rule.setCooldownSec(request.cooldown() != null ? request.cooldown() : 0);
        rule.setSeverity(request.severity());
        rule.setActions(request.actions());
        EventDisplayType displayType = request.displayType() != null ? request.displayType() : EventDisplayType.SIMPLE;
        rule.setDisplayType(displayType);
        if (request.sopTemplateId() != null) {
            SopTemplate template = sopTemplateRepository.findById(request.sopTemplateId())
                    .orElseThrow(() -> new EntityNotFoundException("SOP 템플릿을 찾을 수 없습니다."));
            rule.setSopTemplate(template);
        } else {
            rule.setSopTemplate(null);
        }
        if (request.active() != null) {
            rule.setActive(request.active());
        }
        return rule;
    }
}
