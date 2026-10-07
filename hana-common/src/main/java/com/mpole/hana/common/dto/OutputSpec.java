package com.mpole.hana.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.mpole.hana.common.constant.NotificationChannel;

import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

/**
 * 이벤트 발생 시의 출력 채널 세트.
 *
 * <p>규칙 레벨(주의/경계/심각)마다 저장되며(JSONB), 이벤트 발생 시 발생 레벨의 outputs 가
 * 정본({@code tbl_event.outputs})에 스냅샷으로 박제된다. 이후 정책이 바뀌어도 과거 이벤트의
 * 의미는 변하지 않는다.</p>
 *
 * <p>발생 시 {@code tbl_event.outputs} 로 스냅샷되어 클라이언트 표출 근거가 된다(채널별 상태 행은 2026-10-02 폐기).</p>
 *
 * <p><b>popup 키</b> — 과거 {@code notify} 였으나 {@link NotificationChannel#POPUP} 과 이름을 맞췄다.
 * 기존 저장 데이터 호환을 위해 {@code @JsonAlias("notify")} 로 옛 키도 읽는다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
public record OutputSpec(
        @JsonAlias("notify") PopupOutput popup,
        SopOutput sop,
        CctvOutput cctv,
        SmsOutput sms,
        EmailOutput email,
        DeviceControlOutput deviceControl
) {

    /** 팝업/토스트 알림. 사용자 확인(ack) 대상. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PopupOutput(boolean enabled) {
    }

    /** SOP 절차 패널. templateId = 클라이언트에 내려줄 SOP 템플릿. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SopOutput(boolean enabled, Long templateId) {
    }

    /** 주변 CCTV. source=DEVICE_MAPPING 이면 발생 장비 매핑으로 자동 선정, limit=최대 카메라 수. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CctvOutput(boolean enabled, CctvSource source, Integer limit) {
    }

    /** 문자 발송(서버 사이드). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SmsOutput(boolean enabled, Long recipientGroupId, Long templateId) {
    }

    /** 이메일 발송(서버 사이드). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmailOutput(boolean enabled, Long recipientGroupId, Long templateId) {
    }

    /** 디바이스 제어 명령(서버 사이드). commandPointId 에 value 를 쓴다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeviceControlOutput(boolean enabled, Long commandPointId, String value) {
    }

    public boolean popupEnabled() {
        return popup != null && popup.enabled();
    }

    public boolean sopEnabled() {
        return sop != null && sop.enabled();
    }

    public boolean cctvEnabled() {
        return cctv != null && cctv.enabled();
    }

    public boolean smsEnabled() {
        return sms != null && sms.enabled();
    }

    public boolean emailEnabled() {
        return email != null && email.enabled();
    }

    public boolean deviceControlEnabled() {
        return deviceControl != null && deviceControl.enabled();
    }

    /**
     * 켜진 채널 목록(로그·표시용). 비어 있으면 "액션 없는 event"(표시 전용)이며 그것도 정상이다.
     */
    public List<NotificationChannel> enabledChannels() {
        List<NotificationChannel> out = new ArrayList<>();
        if (popupEnabled()) out.add(NotificationChannel.POPUP);
        if (sopEnabled()) out.add(NotificationChannel.SOP);
        //if (cctvEnabled()) out.add(NotificationChannel.CCTV);
        //if (smsEnabled()) out.add(NotificationChannel.SMS);
        //if (emailEnabled()) out.add(NotificationChannel.EMAIL);
        //if (deviceControlEnabled()) out.add(NotificationChannel.DEVICE_CONTROL);
        return out;
    }

    /** 클라이언트가 렌더하는 표시 채널만(서버 사이드 발송 채널 제외). */
    public List<String> displayChannels() {
        List<String> out = new ArrayList<>();
        if (popupEnabled()) out.add(NotificationChannel.POPUP.name());
        if (sopEnabled()) out.add(NotificationChannel.SOP.name());
        //if (cctvEnabled()) out.add(NotificationChannel.CCTV.name());
        return out;
    }
}
