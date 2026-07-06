package com.mpole.hana.digitaltwin.alarm.service;

import com.mpole.hana.digitaltwin.alarm.dto.AlarmPolicyLevelItem;
import com.mpole.hana.digitaltwin.alarm.dto.AlarmPolicyRequest;
import com.mpole.hana.digitaltwin.alarm.dto.AlarmPolicyResponse;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmConditionKind;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmPolicy;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmPolicyLevel;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmScope;
import com.mpole.hana.digitaltwin.alarm.repository.AlarmPolicyRepository;
import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AlarmPolicyService {

    private final AlarmPolicyRepository alarmPolicyRepository;

    public AlarmPolicyResponse create(AlarmPolicyRequest req) {
        validate(req);

        AlarmPolicy policy = AlarmPolicy.builder()
                .policyName(req.policyName())
                .description(req.description())
                .scope(req.scope())
                .categoryId(req.categoryId())
                .deviceId(req.deviceId())
                .tagName(req.tagName())
                .pointType(req.pointType())
                .conditionKind(req.conditionKind())
                .operator(req.operator())
                .triggerValue(req.triggerValue())
                .displayType(req.displayType() != null ? req.displayType() : EventDisplayType.SIMPLE)
                .sopTemplateId(req.sopTemplateId())
                .effectEnabled(req.effectEnabled())
                .sustainSec(req.sustainSec())
                .cooldownSec(req.cooldownSec())
                .active(req.active() != null ? req.active() : Boolean.TRUE)
                .build();

        applyLevels(policy, req.levels());

        AlarmPolicy saved = alarmPolicyRepository.save(policy);
        log.info("알람 정책 생성: policyId={}, name={}", saved.getPolicyId(), saved.getPolicyName());
        return AlarmPolicyResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AlarmPolicyResponse> list() {
        return alarmPolicyRepository.findAllByOrderByPolicyIdDesc().stream()
                .map(AlarmPolicyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlarmPolicyResponse get(Long id) {
        return AlarmPolicyResponse.from(findOrThrow(id));
    }

    public AlarmPolicyResponse update(Long id, AlarmPolicyRequest req) {
        validate(req);

        AlarmPolicy policy = findOrThrow(id);
        policy.setPolicyName(req.policyName());
        policy.setDescription(req.description());
        policy.setScope(req.scope());
        policy.setCategoryId(req.categoryId());
        policy.setDeviceId(req.deviceId());
        policy.setTagName(req.tagName());
        policy.setPointType(req.pointType());
        policy.setConditionKind(req.conditionKind());
        policy.setOperator(req.operator());
        policy.setTriggerValue(req.triggerValue());
        policy.setDisplayType(req.displayType() != null ? req.displayType() : EventDisplayType.SIMPLE);
        policy.setSopTemplateId(req.sopTemplateId());
        policy.setEffectEnabled(req.effectEnabled());
        policy.setSustainSec(req.sustainSec());
        policy.setCooldownSec(req.cooldownSec());
        policy.setActive(req.active() != null ? req.active() : Boolean.TRUE);

        // 레벨 전체 교체 (orphanRemoval 로 기존 레벨 삭제)
        policy.clearLevels();
        applyLevels(policy, req.levels());

        AlarmPolicy saved = alarmPolicyRepository.save(policy);
        log.info("알람 정책 수정: policyId={}", saved.getPolicyId());
        return AlarmPolicyResponse.from(saved);
    }

    public void delete(Long id) {
        AlarmPolicy policy = findOrThrow(id);
        alarmPolicyRepository.delete(policy);
        log.info("알람 정책 삭제: policyId={}", id);
    }

    private AlarmPolicy findOrThrow(Long id) {
        return alarmPolicyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("알람 정책을 찾을 수 없습니다. id=" + id));
    }

    private void applyLevels(AlarmPolicy policy, List<AlarmPolicyLevelItem> items) {
        if (items == null) return;
        for (AlarmPolicyLevelItem item : items) {
            AlarmPolicyLevel level = AlarmPolicyLevel.builder()
                    .level(item.level())
                    .thresholdValue(item.thresholdValue())
                    .icon(item.icon())
                    .effect(item.effect())
                    .displayOrder(item.displayOrder())
                    .build();
            policy.addLevel(level);
        }
    }

    private void validate(AlarmPolicyRequest req) {
        if (req.policyName() == null || req.policyName().isBlank()) {
            throw new IllegalArgumentException("정책명(policyName)은 필수입니다.");
        }
        if (req.scope() == null) {
            throw new IllegalArgumentException("적용 범위(scope)는 필수입니다.");
        }
        if (req.scope() == AlarmScope.CATEGORY && req.categoryId() == null) {
            throw new IllegalArgumentException("scope=CATEGORY 인 경우 categoryId 는 필수입니다.");
        }
        if (req.scope() == AlarmScope.DEVICE && req.deviceId() == null) {
            throw new IllegalArgumentException("scope=DEVICE 인 경우 deviceId 는 필수입니다.");
        }
        if (req.conditionKind() == null) {
            throw new IllegalArgumentException("조건 종류(conditionKind)는 필수입니다.");
        }
        if (req.conditionKind() == AlarmConditionKind.THRESHOLD) {
            if (req.operator() == null) {
                throw new IllegalArgumentException("conditionKind=THRESHOLD 인 경우 operator 는 필수입니다.");
            }
            boolean hasThreshold = req.levels() != null && req.levels().stream()
                    .anyMatch(l -> l.thresholdValue() != null);
            if (!hasThreshold) {
                throw new IllegalArgumentException("conditionKind=THRESHOLD 인 경우 하나 이상의 레벨에 thresholdValue 가 필요합니다.");
            }
        }
        if (req.conditionKind() == AlarmConditionKind.TOGGLE
                && (req.triggerValue() == null || req.triggerValue().isBlank())) {
            throw new IllegalArgumentException("conditionKind=TOGGLE 인 경우 triggerValue 는 필수입니다.");
        }
        if (req.levels() != null && req.levels().stream().anyMatch(l -> Objects.isNull(l.level()))) {
            throw new IllegalArgumentException("각 레벨 항목의 level 값은 필수입니다.");
        }
    }
}
