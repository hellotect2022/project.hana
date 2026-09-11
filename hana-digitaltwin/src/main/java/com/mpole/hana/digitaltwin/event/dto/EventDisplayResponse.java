package com.mpole.hana.digitaltwin.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;

import java.util.List;
import java.util.UUID;

/**
 * 이벤트 화면 조립 응답.
 *
 * <p>{@code GET /api/event/{eventId}/display} 결과. 정본(tbl_event)의 outputs 를
 * 근거로 켜진 표시 채널만 조립한다: SOP(템플릿 규격 공급) + CCTV(발생 장비 주변 카메라).
 * 클라이언트(Unity)는 outputs 목록을 보고 어떤 패널을 열지 결정하고, sop/cctv 페이로드로 렌더한다.</p>
 *
 * <p>{@code @JsonInclude(NON_NULL)} — 꺼진 채널(sop/cctv)은 null 로 생략된다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventDisplayResponse(
        UUID eventId,
        Long deviceId,
        String level,
        List<String> outputs,   // 켜진 표시 채널(POPUP/SOP/CCTV)
        SopPart sop,            // outputs.sop 꺼져 있으면 null
        CctvPart cctv           // outputs.cctv 꺼져 있으면 null
) {

    /**
     * SOP 표출 페이로드 — 서버는 <b>템플릿 규격만 내려주고</b> 진행·완료는 클라이언트(Unity)가 수행한다.
     * 진행 상태는 {@code tbl_event_action}(channel=SOP)의 status 로 추적되며,
     * 클라이언트가 완료를 보고하면 그 행이 DONE 으로 갱신된다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SopPart(
            UUID actionId,
            Long templateId,
            String templateCode,
            String status,
            SopTemplateBody structure
    ) {
    }

    /** CCTV 표출 페이로드. layout 은 카메라 개수로 파생(GRID_1/4/6/8/9). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CctvPart(
            String layout,
            int count,
            List<Camera> cameras
    ) {
    }

    /** 카메라 1대. Unity 가 RTSP 를 직접 재생하며 credential 로 인증한다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Camera(
            Long cctvId,
            String name,
            int order,
            String mainUrl,
            String subUrl,
            Credential credential,
            Long floorId,
            String floorName
    ) {
    }

    /** RTSP 인증 자격증명. 인증된 조립 API 로만 전달. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Credential(String userId, String password) {
    }
}
