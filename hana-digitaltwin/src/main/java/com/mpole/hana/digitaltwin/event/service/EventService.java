package com.mpole.hana.digitaltwin.event.service;

import com.mpole.hana.common.constant.ActionStatus;
import com.mpole.hana.common.constant.NotificationChannel;
import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.digitaltwin.device.service.DeviceCategoryCascadeService;
import com.mpole.hana.digitaltwin.event.dto.AlarmHistoryRow;
import com.mpole.hana.digitaltwin.event.dto.AlarmRuleOption;
import com.mpole.hana.digitaltwin.event.dto.EventPageResponse;
import com.mpole.hana.digitaltwin.event.dto.EventResponse;
import com.mpole.hana.digitaltwin.event.dto.EventDisplayResponse;
import com.mpole.hana.digitaltwin.event.entity.Event;
import com.mpole.hana.digitaltwin.event.entity.EventAction;
import com.mpole.hana.digitaltwin.event.repository.EventActionRepository;
import com.mpole.hana.digitaltwin.event.repository.EventRepository;
import com.mpole.hana.digitaltwin.device.dto.CctvInfoProjection;
import com.mpole.hana.digitaltwin.device.property.CctvProperty;
import com.mpole.hana.digitaltwin.device.repository.DeviceCctvMappingRepository;
import com.mpole.hana.digitaltwin.sop.entity.SopTemplate;
import com.mpole.hana.digitaltwin.sop.repository.SopTemplateRepository;
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
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 조회 서비스.
 *
 * <p>발생·해소 쓰기는 event-server(R2DBC)가 담당한다. 여기서 하는 쓰기는 사람이 만드는 두 가지
 * — 확인(ack)과 채널 액션 완료 보고 — 뿐이라 클래스 기본값은 readOnly 다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private static final int DEFAULT_CCTV_LIMIT = 8;

    private final EventRepository eventRepository;
    private final DeviceCategoryCascadeService deviceCategoryCascadeService;
    private final EventActionRepository eventActionRepository;
    private final SopTemplateRepository sopTemplateRepository;
    private final DeviceCctvMappingRepository cctvMappingRepository;

    /**
     * level / deviceId / occurredAt(from~to) 선택 필터로 알람 이력을 페이징 조회한다.
     * 각 필터는 null 이면 조건에서 제외된다. 정렬(occurredAt desc)은 Pageable 에서 지정한다.
     */
    public EventPageResponse getHistory(
            String level,
            Long deviceId,
            Boolean active,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    ) {
        Page<EventResponse> page = eventRepository
                .findByOptionalFilters(level, deviceId, active, from, to, pageable)
                .map(EventResponse::from);
        return EventPageResponse.from(page);
    }

    /**
     * 알람 이력 화면 조회 — 명칭(장비명)·위치(층)·알람명·완료여부·발생일시.
     *
     * <p>전력감시·엘리베이터·소방방재·태양광·출입통제·화장실 물리버튼 화면이 모두 이 하나를 쓴다.
     * 화면마다 다른 건 제목과 고정 필터뿐이다.</p>
     *
     * @param categoryId 소분류가 아니어도 된다 — 대·중분류를 넘기면 하위 소분류 전체로 확장한다
     * @param completed  true=조치완료, false=미조치, null=전체 (판정 기준은 {@code ackedAt})
     */
    public Page<AlarmHistoryRow> getAlarmHistory(
            String level,
            Long ruleId,
            Long deviceId,
            Long floorId,
            Long categoryId,
            Boolean completed,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    ) {
        List<Long> categoryIds = (categoryId == null)
                ? null
                : deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId);

        // 하위 소분류가 하나도 없으면 결과도 없다. 빈 목록을 IN 절에 넘기면 SQL 문법 오류가 난다.
        if (categoryIds != null && categoryIds.isEmpty()) {
            return Page.empty(pageable);
        }

        return eventRepository.searchAlarmHistory(
                level, ruleId, deviceId, floorId, categoryIds, completed, from, to, pageable);
    }

    /**
     * 알람명 드롭다운("데이터 선택") 선택지 — 실제로 발생한 적 있는 규칙만.
     *
     * @param from 이 시각 이후 발생분만. null 이면 전체 기간
     */
    public List<AlarmRuleOption> getAlarmRuleOptions(OffsetDateTime from) {
        List<AlarmRuleOption> options = new ArrayList<>();
        for (Object[] row : eventRepository.findDistinctRules(from)) {
            Long id = row[0] == null ? null : ((Number) row[0]).longValue();
            options.add(new AlarmRuleOption(id, (String) row[1]));
        }
        return options;
    }

    public EventResponse get(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다. id=" + id));
        return EventResponse.from(event);
    }

    /** 현재 활성(조건 성립 중) 이벤트 목록 — Active 축 도입으로 가능해진 "지금 떠 있는 알람". */
    public List<EventResponse> getActive() {
        return eventRepository.findByActiveTrueOrderByOccurredAtDesc().stream()
                .map(EventResponse::from)
                .toList();
    }

    /**
     * 팝업 확인(ack). 최초 확인자·시각만 남기고 이후 호출은 무시한다.
     * <p>POPUP 채널 액션도 함께 DONE 으로 올린다 — 팝업은 "확인"이 곧 완료다.</p>
     *
     * @return 실제로 확인 처리됐으면 true, 이미 확인된 건이면 false
     */
    @Transactional
    public boolean ack(UUID eventId, Long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        int updated = eventRepository.ack(eventId, userId, now);
        if (updated > 0) {
            eventActionRepository.findByEventIdAndChannel(eventId, NotificationChannel.POPUP)
                    .ifPresent(a -> {
                        a.setStatus(ActionStatus.DONE);
                        a.setCompletedAt(now);
                        a.setCompletedBy(userId);
                    });
        }
        return updated > 0;
    }

    /**
     * 채널 액션 완료 보고 — 클라이언트(Unity/Web)가 SOP 등을 끝냈을 때 호출한다.
     * 서버는 진행을 주관하지 않고 <b>보고를 기록</b>할 뿐이다.
     */
    @Transactional
    public void completeAction(UUID eventId, NotificationChannel channel,
                               Map<String, Object> result, Long userId) {
        EventAction action = eventActionRepository.findByEventIdAndChannel(eventId, channel)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 이벤트에 " + channel + " 액션이 없습니다. eventId=" + eventId));
        action.setStatus(ActionStatus.DONE);
        action.setResult(result);
        action.setCompletedAt(OffsetDateTime.now());
        action.setCompletedBy(userId);
    }

    /** 이벤트의 채널별 액션 목록(발행/완료 상태). */
    public List<EventAction> getActions(UUID eventId) {
        return eventActionRepository.findByEventIdOrderByIssuedAtAsc(eventId);
    }

    /**
     * 이벤트 화면 조립 — 정본({@code tbl_event})의 outputs 를 근거로 켜진 표시 채널만 조립한다.
     * <ul>
     *   <li>SOP: outputs.sop 이면 템플릿 규격을 그대로 공급(진행·완료는 클라이언트 담당).</li>
     *   <li>CCTV: outputs.cctv 이면 발생 장비(deviceId) 매핑 카메라 조립(개수로 layout 파생).</li>
     * </ul>
     * 조회만 하므로 클래스 기본값인 readOnly 트랜잭션을 그대로 쓴다.
     */
    public EventDisplayResponse getDisplay(UUID eventId) {
        Event h = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다. id=" + eventId));

        OutputSpec out = h.getOutputs();
        List<String> channels = (out != null) ? out.displayChannels() : Collections.emptyList();

        EventDisplayResponse.SopPart sop = buildSop(eventId, h, out);
        EventDisplayResponse.CctvPart cctv = buildCctv(h, out);

        return new EventDisplayResponse(eventId, h.getDeviceId(), h.getLevel(), channels, sop, cctv);
    }

    /**
     * SOP 표출 — 서버는 {@code tbl_sop_template} 의 규격(JSON)을 그대로 내려준다.
     * 진행/완료는 클라이언트가 수행하고, 상태는 {@code tbl_event_action}(channel=SOP)에 기록된다.
     */
    private EventDisplayResponse.SopPart buildSop(UUID eventId, Event h, OutputSpec out) {
        boolean sopOn = out != null && out.sopEnabled();
        Long templateId = (out != null && out.sop() != null) ? out.sop().templateId() : null;
        if (!sopOn || templateId == null) {
            return null;
        }
        SopTemplate template = sopTemplateRepository.findById(templateId).orElse(null);
        if (template == null) {
            log.warn("[Event] SOP 템플릿 없음 — eventId={}, templateId={}", eventId, templateId);
            return null;
        }
        EventAction action = eventActionRepository
                .findByEventIdAndChannel(eventId, NotificationChannel.SOP)
                .orElse(null);
        // 액션 행이 아직 없으면(레거시 이벤트 등) 상태는 미발행으로 표시한다. 생성은 event-server 담당.
        return new EventDisplayResponse.SopPart(
                action != null ? action.getActionId() : null,
                template.getTemplateId(),
                template.getTemplateCode(),
                action != null ? action.getStatus().name() : ActionStatus.PENDING.name(),
                template.getBody());
    }

    private EventDisplayResponse.CctvPart buildCctv(Event h, OutputSpec out) {
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
