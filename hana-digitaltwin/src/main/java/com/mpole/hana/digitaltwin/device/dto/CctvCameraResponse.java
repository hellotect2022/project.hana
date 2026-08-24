package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.property.CctvProperty;
import com.mpole.hana.digitaltwin.device.support.RtspUrlSupport;

/**
 * CCTV 전용 응답 (매핑/후보). 저장 모델(CctvProperty/JSONB)을 그대로 노출하지 않고,
 * 재생용 streamUrl 을 <b>읽을 때 조립</b>해 내려준다.
 *
 * <p>raw user_id/password 는 노출하지 않는다(자격증명은 streamUrl 안에만 포함).</p>
 */
public record CctvCameraResponse(
        Long deviceId,
        String deviceName,
        String deviceDisplayName,
        Long floorId,
        String floorName,
        String streamUrl,
        String subStreamUrl,
        String port,
        String cctvType,
        String cctvModel,
        String cctvMfg
) {
    public static CctvCameraResponse from(CctvInfoProjection p) {
        CctvProperty prop = p.getDeviceProperty();
        String userId = prop != null ? prop.getUserId() : null;
        String password = prop != null ? prop.getPassword() : null;
        String port = prop != null ? prop.getPort() : null;
        String mainUrl = prop != null ? prop.getMainUrl() : null;
        String subUrl = prop != null ? prop.getSubUrl() : null;

        return new CctvCameraResponse(
                p.getDeviceId(),
                p.getDeviceName(),
                p.getDeviceDisplayName(),
                p.getFloorId(),
                p.getFloorName(),
                RtspUrlSupport.withCredentials(mainUrl, userId, password, port),
                RtspUrlSupport.withCredentials(subUrl, userId, password, port),
                port,
                prop != null ? prop.getCctvType() : null,
                prop != null ? prop.getCctvModel() : null,
                prop != null ? prop.getCctvMfg() : null
        );
    }
}
