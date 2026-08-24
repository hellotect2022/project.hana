package com.mpole.hana.digitaltwin.alarm.service;

import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.digitaltwin.alarm.dto.AlarmHistoryPageResponse;
import com.mpole.hana.digitaltwin.alarm.dto.AlarmHistoryResponse;
import com.mpole.hana.digitaltwin.alarm.dto.EventDisplayResponse;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmHistory;
import com.mpole.hana.digitaltwin.alarm.repository.AlarmHistoryRepository;
import com.mpole.hana.digitaltwin.device.dto.CctvInfoProjection;
import com.mpole.hana.digitaltwin.device.property.CctvProperty;
import com.mpole.hana.digitaltwin.device.repository.DeviceCctvMappingRepository;
import com.mpole.hana.digitaltwin.sop.entity.SopInstance;
import com.mpole.hana.digitaltwin.sop.service.SopRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 알람 발생 이력 조회(읽기 전용) 서비스.
 * 쓰기는 event-server(R2DBC)가 담당하며, 이 서비스는 조회만 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmHistoryService {

    private static final int DEFAULT_CCTV_LIMIT = 8;

    private final AlarmHistoryRepository alarmHistoryRepository;
    private final SopRuntimeService sopRuntimeService;
    private final DeviceCctvMappingRepository cctvMappingRepository;

    /**
     * level / deviceId / occurredAt(from~to) 선택 필터로 알람 이력을 페이징 조회한다.
     * 각 필터는 null 이면 조건에서 제외된다. 정렬(occurredAt desc)은 Pageable 에서 지정한다.
     */
    public AlarmHistoryPageResponse getHistory(
            String level,
            Long deviceId,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    ) {
        Page<AlarmHistoryResponse> page = alarmHistoryRepository
                .findByOptionalFilters(level, deviceId, from, to, pageable)
                .map(AlarmHistoryResponse::from);
        return AlarmHistoryPageResponse.from(page);
    }

    public AlarmHistoryResponse get(Long id) {
        AlarmHistory history = alarmHistoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("알람 이력을 찾을 수 없습니다. id=" + id));
        return AlarmHistoryResponse.from(history);
    }

    /**
     * 이벤트 화면 조립 — 정본(alarm_history)의 outputs 를 근거로 켜진 표시 채널만 조립한다.
     * <ul>
     *   <li>SOP: outputs.sop(또는 레거시 displayType==SOP) 이면 alarmId 로 SopInstance get-or-create.</li>
     *   <li>CCTV: outputs.cctv 이면 발생 장비(deviceId) 매핑 카메라 조립(개수로 layout 파생).</li>
     * </ul>
     * SopInstance get-or-create 로 쓰기가 발생하므로 readOnly 를 오버라이드한다.
     */
    @Transactional
    public EventDisplayResponse getDisplay(Long alarmId) {
        AlarmHistory h = alarmHistoryRepository.findById(alarmId)
                .orElseThrow(() -> new IllegalArgumentException("알람 이력을 찾을 수 없습니다. id=" + alarmId));

        OutputSpec out = h.getOutputs();
        List<String> channels = (out != null) ? out.displayChannels() : Collections.emptyList();

        EventDisplayResponse.SopPart sop = buildSop(alarmId, h, out);
        EventDisplayResponse.CctvPart cctv = buildCctv(h, out);

        return new EventDisplayResponse(alarmId, h.getDeviceId(), h.getLevel(), channels, sop, cctv);
    }

    /** outputs 없는 레거시 이벤트의 표시 채널: NOTIFY 항상 + displayType==SOP 이면 SOP. */
//    private List<String> legacyChannels(AlarmHistory h) {
//        List<String> ch = new ArrayList<>();
//        ch.add("NOTIFY");
//        if ("SOP".equals(h.getDisplayType())) ch.add("SOP");
//        return ch;
//    }

    private EventDisplayResponse.SopPart buildSop(Long alarmId, AlarmHistory h, OutputSpec out) {
        boolean sopOn = out != null && out.sopEnabled();
        Long templateId = (out != null && out.sop() != null) ? out.sop().templateId() : null;
        if (!sopOn || templateId == null) {
            return null;
        }
        SopInstance inst = sopRuntimeService.getOrCreateByEvent(alarmId, templateId);
        return new EventDisplayResponse.SopPart(
                inst.getInstanceId(), inst.getTemplateId(), inst.getTemplateCode(),
                inst.getCurrentStepNo(), inst.getStructureSnapshot());
    }

    private EventDisplayResponse.CctvPart buildCctv(AlarmHistory h, OutputSpec out) {
        boolean cctvOn = out != null && out.cctvEnabled();
        if (!cctvOn || h.getDeviceId() == null) {
            return null;
        }
        int limit = (out.cctv() != null && out.cctv().limit() != null && out.cctv().limit() > 0)
                ? out.cctv().limit() : DEFAULT_CCTV_LIMIT;

        List<CctvInfoProjection> rows = cctvMappingRepository.findByDeviceId(h.getDeviceId());
        List<EventDisplayResponse.Camera> cams = new ArrayList<>();
        int order = 1;
        for (CctvInfoProjection r : rows) {
            if (order > limit) break;
            CctvProperty prop = r.getDeviceProperty();
            String mainUrl = prop != null ? prop.getMainUrl() : null;
            String subUrl = prop != null ? prop.getSubUrl() : null;
            EventDisplayResponse.Credential cred =
                    (prop != null && (prop.getUserId() != null || prop.getPassword() != null))
                            ? new EventDisplayResponse.Credential(prop.getUserId(), prop.getPassword())
                            : null;
            cams.add(new EventDisplayResponse.Camera(
                    r.getDeviceId(), r.getDeviceName(), order, mainUrl, subUrl, cred,
                    r.getFloorId(), r.getFloorName()));
            order++;
        }
        return new EventDisplayResponse.CctvPart(deriveLayout(cams.size()), cams.size(), cams);
    }

    /** 카메라 개수 → 그리드 레이아웃 파생. */
    private String deriveLayout(int count) {
        if (count <= 1) return "GRID_1";
        if (count <= 4) return "GRID_4";
        if (count <= 6) return "GRID_6";
        if (count <= 8) return "GRID_8";
        return "GRID_9";
    }
}
