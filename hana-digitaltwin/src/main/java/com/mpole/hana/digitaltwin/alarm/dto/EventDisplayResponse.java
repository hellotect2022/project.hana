package com.mpole.hana.digitaltwin.alarm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;

import java.util.List;

/**
 * 이벤트(알람) 화면 조립 응답.
 *
 * <p>{@code GET /api/alarm/history/{alarmId}/display} 결과. 정본(tbl_alarm_history)의 outputs 를
 * 근거로 켜진 표시 채널만 조립한다: SOP(절차 패널, get-or-create) + CCTV(발생 장비 주변 카메라).
 * 클라이언트(Unity)는 outputs 목록을 보고 어떤 패널을 열지 결정하고, sop/cctv 페이로드로 렌더한다.</p>
 *
 * <p>{@code @JsonInclude(NON_NULL)} — 꺼진 채널(sop/cctv)은 null 로 생략된다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventDisplayResponse(
        Long alarmId,
        Long deviceId,
        String level,
        List<String> outputs,   // 켜진 표시 채널(NOTIFY/SOP/CCTV)
        SopPart sop,            // outputs.sop 꺼져 있으면 null
        CctvPart cctv           // outputs.cctv 꺼져 있으면 null
) {

    /** SOP 절차 패널 페이로드. structure = 생성 시점 템플릿 body 스냅샷. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SopPart(
            Long instanceId,
            Long templateId,
            String templateCode,
            Integer currentStepNo,
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
