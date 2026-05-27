package com.mpole.hana.digitaltwin.device.property;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
public enum DevicePropertyType {
    CCTV("CCTV"),
    CHILLER("CHILLER"),
    GATEWAY("GATEWAY"),
    GENERIC("GENERIC");

    private final String jsonName;

    @JsonValue
    public String getJsonName() {
        return jsonName;
    }

    @JsonCreator
    public static DevicePropertyType fromJson(String value) {
        if (value == null || value.isBlank()) {
            return GENERIC;
        }
        for (DevicePropertyType type : values()) {
            if (type.jsonName.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 property type: " + value);
    }

    public Class<? extends DevicePropertyInfo> propertyClass() {
        return switch (this) {
            case CCTV -> CctvProperty.class;
            case CHILLER -> ChillerProperty.class;
            case GATEWAY -> GatewayProperty.class;
            case GENERIC -> GenericDeviceProperty.class;
        };
    }
}
