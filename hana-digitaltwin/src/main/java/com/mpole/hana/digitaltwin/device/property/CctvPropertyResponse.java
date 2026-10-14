package com.mpole.hana.digitaltwin.device.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.mpole.hana.digitaltwin.device.support.RtspUrlSupport;

/**
 * CCTV property_info <b>응답 전용</b> 표현.
 *
 * <p>저장 엔티티({@link CctvProperty}, JSONB)는 그대로 두고, 응답에서만 {@code stream_url} 을
 * 계산해 포함한다(미저장). {@link DevicePropertyInfo} 를 구현해 {@code DeviceResponse.propertyInfo}
 * 타입을 유지하되, 요청 역직렬화용 {@code @JsonSubTypes} 에는 등록하지 않는다(응답 전용).</p>
 */
@JsonTypeName("CCTV")
public record CctvPropertyResponse(
        @JsonProperty("type") String type,
        @JsonProperty("main_url") String mainUrl,
        @JsonProperty("sub_url") String subUrl,
        @JsonProperty("user_id") String userId,
        @JsonProperty("password") String password,
        @JsonProperty("port") String port,
        @JsonProperty("cctv_ip") String cctvIp,
        @JsonProperty("cctv_model") String cctvModel,
        @JsonProperty("cctv_type") String cctvType,
        @JsonProperty("cctv_mfg") String cctvMfg,
        @JsonProperty("stream_url") String streamUrl
) implements DevicePropertyInfo {

    public static CctvPropertyResponse from(CctvProperty p) {
        return new CctvPropertyResponse(
                p.getType(),
                p.getMainUrl(),
                p.getSubUrl(),
                p.getUserId(),
                p.getPassword(),
                p.getPort(),
                p.getCctvIp(),
                p.getCctvModel(),
                p.getCctvType(),
                p.getCctvMfg(),
                // 자격증명/포트가 반영된 재생 URL — 계산값(미저장)
                RtspUrlSupport.withCredentials(p.getMainUrl(), p.getUserId(), p.getPassword(), p.getPort())
        );
    }

    @Override
    public DevicePropertyType propertyType() {
        return DevicePropertyType.CCTV;
    }
}
