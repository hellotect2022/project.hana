package com.mpole.hana.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.mpole.hana.common.constant.ControlTrigger;
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
        /*
         * 디바이스 제어 목록(2026-10-12 단건 deviceControl → 목록). 한 레벨에서 여러 장비를 각자의 시점에 제어한다.
         * 옛 키 "deviceControl"(단건 객체)로 저장된 데이터도 1건짜리 목록으로 읽힌다.
         */
        @JsonAlias("deviceControl")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<DeviceControlOutput> deviceControls
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

    /**
     * 디바이스 제어 명령(서버 사이드). {@code commandPointId}(tbl_device_command_point) 에 {@code value} 를 보낸다.
     * {@code value} 해석은 기존 수동 제어(POST /api/command/control)와 같다 — TOGGLE 은 1/0, ENUM 은 선택지 값, RANGE 는 숫자.
     *
     * @param trigger 실행 시점. null 이면 {@link ControlTrigger#ON_OCCUR} (2026-10-12)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeviceControlOutput(boolean enabled, Long commandPointId, String value, ControlTrigger trigger) {

        /** 실행 시점(지정 없으면 발생 시). */
        public ControlTrigger triggerOrDefault() {
            return trigger == null ? ControlTrigger.ON_OCCUR : trigger;
        }
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

    /** 켜진 제어가 하나라도 있는가. */
    public boolean deviceControlEnabled() {
        return deviceControls != null && deviceControls.stream().anyMatch(c -> c != null && c.enabled());
    }

    /** 이 시점에 실행할 켜진 제어 목록(제어포인트가 비어 있는 항목은 제외). */
    @JsonIgnore
    public List<DeviceControlOutput> controlsFor(ControlTrigger trigger) {
        if (deviceControls == null) return List.of();
        return deviceControls.stream()
                .filter(c -> c != null && c.enabled() && c.commandPointId() != null && c.triggerOrDefault() == trigger)
                .toList();
    }

    /** 이 시점에 실행할 제어가 있는가. event 서버가 digitaltwin 호출 여부를 정할 때 쓴다. */
    public boolean hasControlsFor(ControlTrigger trigger) {
        return !controlsFor(trigger).isEmpty();
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
        if (deviceControlEnabled()) out.add(NotificationChannel.DEVICE_CONTROL);
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
