package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.property.CctvProperty;

public interface CctvInfoProjection {
    Long getDeviceId();
    String getDeviceName();
    Long getFloorId();
    String getFloorName();
    CctvProperty getDeviceProperty();
}
