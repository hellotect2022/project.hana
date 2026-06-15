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

    @Override
    public DevicePropertyType propertyType() {
        return DevicePropertyType.CCTV;
    }
}
