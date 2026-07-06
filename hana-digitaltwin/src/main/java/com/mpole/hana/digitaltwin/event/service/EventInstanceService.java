package com.mpole.hana.digitaltwin.event.service;

import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventInstanceCreateRequest;
import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventInstanceSummary;
import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;
import com.mpole.hana.digitaltwin.event.entity.EventInstance;
import com.mpole.hana.digitaltwin.event.entity.EventInstanceStatus;
import com.mpole.hana.digitaltwin.event.entity.EventRule;
import com.mpole.hana.digitaltwin.event.repository.EventInstanceRepository;
import com.mpole.hana.digitaltwin.event.repository.EventRuleRepository;
import com.mpole.hana.digitaltwin.sop.dto.SopRuntimeDtos.*;
import com.mpole.hana.digitaltwin.sop.entity.SopInstance;
import com.mpole.hana.digitaltwin.sop.entity.SopTemplate;
import com.mpole.hana.digitaltwin.sop.repository.SopInstanceRepository;
import com.mpole.hana.digitaltwin.sop.service.SopRuntimeService;
import com.mpole.hana.digitaltwin.sop.service.SopTemplateService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventInstanceService {

    private final EventInstanceRepository instanceRepository;
    private final EventRuleRepository ruleRepository;
    private final SopTemplateService sopTemplateService;
    private final SopRuntimeService sopRuntimeService;
    private final SopInstanceRepository sopInstanceRepository;

    @Transactional(readOnly = true)
    public List<EventInstanceSummary> listOpen(int limit) {
        List<EventInstanceStatus> statuses = List.of(
                EventInstanceStatus.OPEN,
                EventInstanceStatus.ACK,
                EventInstanceStatus.IN_PROGRESS
        );
        return instanceRepository.findByStatusInOrderByOccurredAtDesc(statuses)
                .stream()
                .limit(limit)
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public EventInstanceSummary create(EventInstanceCreateRequest request) {
        EventDisplayType displayType = request.displayType() != null
                ? request.displayType()
                : EventDisplayType.SIMPLE;

        EventRule rule = null;
        if (request.ruleId() != null) {
            rule = ruleRepository.findByIdWithSopTemplate(request.ruleId())
                    .orElseThrow(() -> new EntityNotFoundException("이벤트 규칙을 찾을 수 없습니다."));
            if (rule.getDisplayType() != null) {
                displayType = rule.getDisplayType();
            }
        }

        EventInstance instance = EventInstance.builder()
                .rule(rule)
                .eventName(request.eventName())
                .displayType(displayType)
                .deviceId(request.deviceId())
                .pointId(request.pointId())
                .locationText(request.locationText())
                .buildingCode(request.buildingCode())
                .floorCode(request.floorCode())
                .zoneCode(request.zoneCode())
                .context(request.context())
                .status(EventInstanceStatus.OPEN)
                .occurredAt(OffsetDateTime.now())
                .alarmTitle(request.alarmTitle())
                .alarmDetail(request.alarmDetail())
                .errorCode(request.errorCode())
                .build();
        instanceRepository.save(instance);

        if (displayType == EventDisplayType.SOP) {
            Long templateId = rule != null && rule.getSopTemplate() != null
                    ? rule.getSopTemplate().getTemplateId()
                    : null;
            if (templateId == null) {
                throw new IllegalStateException("SOP 표시에는 sopTemplateId가 필요합니다.");
            }
            SopTemplate template = sopTemplateService.getTemplateForRuntime(templateId);
            instance.setStatus(EventInstanceStatus.IN_PROGRESS);
            sopRuntimeService.createInstance(instance.getEventId(), template);
        }

        return toSummary(instance);
    }

    @Transactional(readOnly = true)
    public EventDisplayResponse getDisplay(Long eventId) {
        EventInstance event = instanceRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("이벤트를 찾을 수 없습니다."));

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("eventName", event.getEventName());
        eventMap.put("locationText", event.getLocationText());
        eventMap.put("occurredAt", event.getOccurredAt());
        eventMap.put("status", event.getStatus().name());
        eventMap.put("deviceId", event.getDeviceId());

        Map<String, Object> map = new HashMap<>();
        map.put("buildingCode", event.getBuildingCode());
        map.put("floorCode", event.getFloorCode());
        map.put("zoneCode", event.getZoneCode());

        if (event.getDisplayType() == EventDisplayType.SOP) {
            SopInstance sop = sopInstanceRepository.findByEventId(eventId)
                    .orElseThrow(() -> new EntityNotFoundException("SOP 인스턴스를 찾을 수 없습니다."));
            SopRuntimePayload sopPayload = sopRuntimeService.buildRuntimePayload(sop.getInstanceId());
            return new EventDisplayResponse(
                    eventId,
                    EventDisplayType.SOP,
                    eventMap,
                    map,
                    Map.of("layout", "GRID_8", "cameraIds", List.of()),
                    sopPayload,
                    null
            );
        }

        SimpleAlarmPayload alarm = new SimpleAlarmPayload(
                event.getAlarmTitle() != null ? event.getAlarmTitle() : "알람",
                event.getEventName(),
                event.getAlarmDetail(),
                event.getErrorCode(),
                event.getRule() != null ? event.getRule().getSeverity() : null
        );
        return new EventDisplayResponse(
                eventId,
                EventDisplayType.SIMPLE,
                eventMap,
                map,
                null,
                null,
                alarm
        );
    }

    private EventInstanceSummary toSummary(EventInstance event) {
        Long sopInstanceId = null;
        Integer currentStepNo = null;
        if (event.getDisplayType() == EventDisplayType.SOP) {
            var sop = sopInstanceRepository.findByEventId(event.getEventId()).orElse(null);
            if (sop != null) {
                sopInstanceId = sop.getInstanceId();
                currentStepNo = sop.getCurrentStepNo();
            }
        }
        return new EventInstanceSummary(
                event.getEventId(),
                event.getEventName(),
                event.getDisplayType(),
                event.getLocationText(),
                event.getOccurredAt(),
                event.getStatus().name(),
                sopInstanceId,
                currentStepNo
        );
    }
}
