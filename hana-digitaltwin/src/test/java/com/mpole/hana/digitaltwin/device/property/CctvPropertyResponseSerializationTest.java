package com.mpole.hana.digitaltwin.device.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CctvPropertyResponseSerializationTest {

    @Test
    void serializesWithTypeAndStreamUrl() throws Exception {
        CctvProperty cp = CctvProperty.builder()
                .mainUrl("rtsp://192.168.107.16:554/channel76")
                .userId("admin")
                .password("cctv!234@")
                .port("554")
                .cctvIp("172.126.210.101")
                .cctvModel("HND-532MI-T")
                .cctvType("실내 DOME")
                .cctvMfg("하니웰")
                .build();

        // DeviceResponse.propertyInfo 와 동일하게 DevicePropertyInfo 선언 타입으로 직렬화
        DevicePropertyInfo pi = CctvPropertyResponse.from(cp);
        String json = new ObjectMapper().writeValueAsString(pi);
        System.out.println("SERIALIZED=" + json);

        assertTrue(json.contains("\"type\":\"CCTV\""), json);
        assertTrue(json.contains("\"stream_url\":\"rtsp://admin:cctv!234%40@192.168.107.16:554/channel76\""), json);
    }
}
