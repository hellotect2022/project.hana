package com.mpole.hana.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

/**
 * 이벤트(알람) 발생 시의 출력 채널 세트.
 *
 * <p>알람 정책의 레벨(주의/경계/심각)마다 저장되며(JSONB), 이벤트 발생 시 발생 레벨의
 * outputs 가 정본(tbl_alarm_history)에 스냅샷으로 박제된다. 채널은 세트에 최대 1회
 * 등장하므로 맵이 아니라 키드 레코드로 각 채널 config 를 명확히 한다.</p>
 *
 * <p>모듈 간 계약이라 hana-common 에 두고 특정 모듈 enum 의존을 피한다.
 * {@code @JsonInclude(NON_NULL)} — 값 없는 채널/필드는 직렬화에서 생략.
 * NOTIFY 채널 컴포넌트는 {@code Object.notify()} 충돌 회피를 위해 {@code notifyOutput}
 * 이름을 쓰되 JSON 키는 {@code @JsonProperty("notify")} 로 유지한다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
public record OutputSpec(
        @JsonProperty("notify") NotifyOutput notifyOutput,
        SopOutput sop,
        CctvOutput cctv,
        SmsOutput sms
) {

    /** 기본 팝업/토스트 알림. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotifyOutput(boolean enabled) {
    }

    /** SOP 절차 패널. templateId = 표시할 SOP 템플릿. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SopOutput(boolean enabled, Long templateId) {
    }

    /** 주변 CCTV. source=DEVICE_MAPPING 이면 발생 장비 매핑으로 자동 선정, limit=최대 카메라 수. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CctvOutput(boolean enabled, CctvSource source, Integer limit) {
    }

    /** 문자 발송(서버 사이드, 현재 슬롯 — 발송 연동은 후속). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SmsOutput(boolean enabled, Long recipientGroupId, Long templateId) {
    }

    public boolean notifyEnabled() {
        return notifyOutput != null && notifyOutput.enabled();
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

    /** 켜진 '표시 채널'(클라이언트 렌더 대상: NOTIFY/SOP/CCTV) 이름 목록. */
    public List<String> displayChannels() {
        List<String> out = new ArrayList<>();
        if (notifyEnabled()) out.add(OutputChannel.POPUP.name());
        if (sopEnabled()) out.add(OutputChannel.SOP.name());
        if (cctvEnabled()) out.add(OutputChannel.CCTV.name());
        return out;
    }
}
