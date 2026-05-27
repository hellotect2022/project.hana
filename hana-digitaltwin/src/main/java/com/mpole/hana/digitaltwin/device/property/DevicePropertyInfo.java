package com.mpole.hana.digitaltwin.device.property;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CctvProperty.class, name = "CCTV"),
        @JsonSubTypes.Type(value = ChillerProperty.class, name = "CHILLER"),
        @JsonSubTypes.Type(value = GatewayProperty.class, name = "GATEWAY"),
        @JsonSubTypes.Type(value = GenericDeviceProperty.class, name = "GENERIC")
})

public sealed interface DevicePropertyInfo permits CctvProperty, ChillerProperty, GatewayProperty, GenericDeviceProperty  {
    DevicePropertyType propertyType();
}
