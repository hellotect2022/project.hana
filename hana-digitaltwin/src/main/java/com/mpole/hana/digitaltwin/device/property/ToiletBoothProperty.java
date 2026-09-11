package com.mpole.hana.digitaltwin.device.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonTypeName("TOILET_BOOTH")
public final class ToiletBoothProperty implements DevicePropertyInfo{
    @Builder.Default
    private String type = DevicePropertyType.TOILET_BOOTH.getJsonName();

    @JsonProperty("gender")
    private String gender;

    /**
     * 이 부스에 붙은 재실센서(emblaze)의 device_uuid.
     * <p>RabbitMQ space 메시지의 {@code device_uuid} 와 매칭해 우리 deviceId 를 찾는 키다.
     * {@code tbl_device_point.ref_device_code} 를 쓰지 않는 이유 — 그 컬럼은 gateway 가
     * view 테이블 매핑용으로 전량 스캔하는 자리라, 여기에 emblaze uuid 를 넣으면 부스 장비가
     * gateway 골격(deviceIndex)에 편입돼 기동 시 전량 발행에 휩쓸리고 points 가 null 로 덮인다.
     * property_info 는 gateway 가 읽지 않으므로 안전하다.</p>
     */
    @JsonProperty("sensor_uuid")
    private String sensorUuid;

    /**
     * 이 부스에 붙은 물리버튼(EnOcean)의 device_uuid.
     * <p>재실센서와 <b>별개 장비</b>라 uuid 가 다르다. RabbitMQ enocean 메시지의
     * {@code device_uuid} 와 매칭한다.</p>
     */
    @JsonProperty("button_uuid")
    private String buttonUuid;

    @Override
    public DevicePropertyType propertyType() {
        return DevicePropertyType.TOILET_BOOTH;
    }
}
