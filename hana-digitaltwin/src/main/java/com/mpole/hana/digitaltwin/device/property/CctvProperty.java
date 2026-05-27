package com.mpole.hana.digitaltwin.device.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonTypeName("CCTV")
public final class CctvProperty implements DevicePropertyInfo{
    @Builder.Default
    private String type = DevicePropertyType.CCTV.getJsonName();

    @JsonProperty("main_rtsp_url")
    private String mainRtspUrl;

    @JsonProperty("sub_rtsp_url")
    private String subRtspUrl;

    @JsonProperty("stream_user_id")
    private String streamUserId;

    @JsonProperty("stream_password")
    private String streamPassword;

    @Override
    public DevicePropertyType propertyType() {
        return DevicePropertyType.CCTV;
    }
}
