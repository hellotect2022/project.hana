package com.mpole.hana.digitaltwin.event.service;

import com.mpole.hana.digitaltwin.event.dto.EventDisplayDtos.EventDisplayResponse;
import com.mpole.hana.digitaltwin.event.dto.EventDisplayDtos.SimpleAlarmPayload;
import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventInstanceCreateRequest;
import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventInstanceSummary;
import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;
import com.mpole.hana.digitaltwin.event.entity.EventInstance;
import com.mpole.hana.digitaltwin.event.entity.EventInstanceStatus;
import com.mpole.hana.digitaltwin.event.entity.EventRule;
import com.mpole.hana.digitaltwin.event.repository.EventInstanceRepository;
import com.mpole.hana.digitaltwin.event.repository.EventRuleRepository;
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
    // NOTE: 기존 SOP 도메인 삭제로 SOP 런타임 연동 일시 비활성. v2 SOP 재구축 시 재연결(TODO).

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
            rule = ruleRepository.findById(request.ruleId())
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

        // TODO(v2 SOP): 기존 SOP 도메인 삭제로 런타임 인스턴스 생성 일시 비활성.
        //  v2 SOP 런타임 구축 후 displayType==SOP 시 SOP 인스턴스 생성 재연결.

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

        Map<String, Object> cctv = event.getDisplayType() == EventDisplayType.SOP
                ? Map.of("layout", "GRID_8", "cameraIds", List.of())
                : null;

        SimpleAlarmPayload alarm = new SimpleAlarmPayload(
                event.getAlarmTitle() != null ? event.getAlarmTitle() : "알람",
                event.getEventName(),
                event.getAlarmDetail(),
                event.getErrorCode(),
                event.getRule() != null ? event.getRule().getSeverity() : null
        );

        // TODO(v2 SOP): SOP 표시일 때 v2 SOP 런타임 페이로드 연결. 현재 sop=null.
        return new EventDisplayResponse(
                eventId,
                event.getDisplayType(),
                eventMap,
                map,
                cctv,
                null,
                alarm
        );
    }

    private EventInstanceSummary toSummary(EventInstance event) {
        // TODO(v2 SOP): sopInstanceId/currentStepNo는 v2 런타임 재연결 시 채움.
        return new EventInstanceSummary(
                event.getEventId(),
                event.getEventName(),
                event.getDisplayType(),
                event.getLocationText(),
                event.getOccurredAt(),
                event.getStatus().name(),
                null,
                null
        );
    }
}
