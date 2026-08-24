package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.property.CctvProperty;

public interface CctvInfoProjection {
    Long getDeviceId();
    String getDeviceName();
    String getDeviceDisplayName();
    Long getFloorId();
    String getFloorName();
    CctvProperty getDeviceProperty();
}
