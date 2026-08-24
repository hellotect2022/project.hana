package com.mpole.hana.digitaltwin.alarm.service;

import com.mpole.hana.digitaltwin.alarm.dto.AlarmPolicyLevelItem;
import com.mpole.hana.digitaltwin.alarm.dto.AlarmPolicyRequest;
import com.mpole.hana.digitaltwin.alarm.dto.AlarmPolicyResponse;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmConditionKind;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmPolicy;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmPolicyLevel;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmScope;
import com.mpole.hana.digitaltwin.alarm.repository.AlarmPolicyRepository;
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
public class AlarmPolicyService {

    private final AlarmPolicyRepository alarmPolicyRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;

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
                .sustainSec(req.sustainSec())
                .cooldownSec(req.cooldownSec())
                .active(req.active() != null ? req.active() : Boolean.TRUE)
                .build();

        applyLevels(policy, req.levels());

        AlarmPolicy saved = alarmPolicyRepository.save(policy);
        log.info("알람 정책 생성: policyId={}, name={}", saved.getPolicyId(), saved.getPolicyName());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AlarmPolicyResponse> list() {
        return alarmPolicyRepository.findAllByOrderByPolicyIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlarmPolicyResponse get(Long id) {
        return toResponse(findOrThrow(id));
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
        policy.setSustainSec(req.sustainSec());
        policy.setCooldownSec(req.cooldownSec());
        policy.setActive(req.active() != null ? req.active() : Boolean.TRUE);

        // 레벨 전체 교체 (orphanRemoval 로 기존 레벨 삭제)
        policy.clearLevels();
        applyLevels(policy, req.levels());

        AlarmPolicy saved = alarmPolicyRepository.save(policy);
        log.info("알람 정책 수정: policyId={}", saved.getPolicyId());
        return toResponse(saved);
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

    /**
     * 소프트 참조(deviceId/categoryId)를 표시용 이름/경로로 resolve 해 응답을 만든다.
     * 정책은 관리 화면용으로 건수가 적어 정책별 조회로 처리한다.
     */
    private AlarmPolicyResponse toResponse(AlarmPolicy p) {
        String deviceName = null;
        if (p.getDeviceId() != null) {
            deviceName = deviceRepository.findById(p.getDeviceId())
                    .map(Device::getDeviceName)
                    .orElse(null);
        }
        return AlarmPolicyResponse.from(p, deviceName, buildCategoryPath(p.getCategoryId()));
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

    private void applyLevels(AlarmPolicy policy, List<AlarmPolicyLevelItem> items) {
        if (items == null) return;
        for (AlarmPolicyLevelItem item : items) {
            AlarmPolicyLevel level = AlarmPolicyLevel.builder()
                    .level(item.level())
                    .thresholdValue(item.thresholdValue())
                    .icon(item.icon())
                    .effect(item.effect())
                    .displayOrder(item.displayOrder())
                    .outputs(item.outputs())
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
