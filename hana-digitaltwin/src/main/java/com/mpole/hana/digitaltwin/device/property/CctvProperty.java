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

    @JsonProperty("main_url")
    private String mainUrl;

    @JsonProperty("sub_url")
    private String subUrl;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("password")
    private String password;

    @JsonProperty("port")
    private String port;

    @JsonProperty("cctv_ip")
    private String cctvIp;

    // 모델이름
    @JsonProperty("cctv_model")
    private String cctvModel;

    // 카메라 타입
    @JsonProperty("cctv_type")
    private String cctvType;

    // 카메라 명칭
    @JsonProperty("cctv_name")
    private String cctvName;

    // 카메라 제조사
    @JsonProperty("cctv_mfg")
    private String cctvMfg;

    @Override
    public DevicePropertyType propertyType() {
        return DevicePropertyType.CCTV;
    }
}
