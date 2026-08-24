package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * EnOcean 무선버튼 중복 수신 제거(device별 마지막 index 비교).
 * emblaze 리스너와 함께 게이팅되어 기본 비활성.
 */
@Component
@ConditionalOnProperty(name = "hdt.integration.emblaze.enabled", havingValue = "true", matchIfMissing = false)
public class EnOceanIndexDeduplicator {

    private final ConcurrentHashMap<String, Long> lastIndexByDevice = new ConcurrentHashMap<>();

    public boolean isDuplicate(String deviceUuid, long index) {
        final boolean[] duplicate = {false};
        lastIndexByDevice.compute(deviceUuid, (key, prev) -> {
            if (prev != null && prev == index) {
                duplicate[0] = true;
                return prev;
            }
            return index;
        });
        return duplicate[0];
    }
}
