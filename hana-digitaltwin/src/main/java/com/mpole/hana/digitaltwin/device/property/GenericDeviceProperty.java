package com.mpole.hana.digitaltwin.device.property;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonTypeName("GENERIC")
public final class GenericDeviceProperty implements DevicePropertyInfo {

    private String type = DevicePropertyType.GENERIC.getJsonName();

    private final Map<String, Object> attributes = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @JsonAnySetter
    public void putAttribute(String key, Object value) {
        if ("type".equals(key)) {
            this.type = value != null ? value.toString() : DevicePropertyType.GENERIC.getJsonName();
            return;
        }
        attributes.put(key, value);
    }

    @Override
    public DevicePropertyType propertyType() {
        return DevicePropertyType.GENERIC;
    }
}
