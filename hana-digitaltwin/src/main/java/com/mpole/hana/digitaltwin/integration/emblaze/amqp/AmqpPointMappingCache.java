package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code (device_uuid, 벤더 필드명)} → 우리 포인트 매핑 캐시.
 *
 * <p>emblaze 는 센서·스위치의 구별용 ID 를 주지 않고 <b>모든 메시지를 조명제어기 UUID</b> 로 보낸다.
 * 그래서 uuid 만으로는 장비를 특정할 수 없다 — 한 uuid 에 조명·재실센서·버튼 장비 3대가 걸린다.
 * 큐마다 필드명이 다르다는 점을 이용해 <b>uuid + 필드명</b>을 키로 쓴다.</p>
 *
 * <p>매핑 원본은 {@code tbl_device_point.ref_device_code / ref_point_code} 다.
 * 장비·포인트를 추가하거나 uuid 를 바꾼 뒤에는 {@link #reload()} 를 호출해야 반영된다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AmqpPointMappingCache {

    private final AmqpPointMappingRepository amqpPointMappingRepository;

    /** key = uuid + "::" + 필드명 */
    private final Map<String, AmqpPointTarget> byUuidAndField = new ConcurrentHashMap<>();

    @PostConstruct
    public void reload() {
        List<Object[]> rows = amqpPointMappingRepository.findAmqpPointMappings();

        Map<String, AmqpPointTarget> loaded = new ConcurrentHashMap<>();
        for (Object[] row : rows) {
            String uuid = str(row[0]);
            String field = str(row[1]);
            Long deviceId = row[3] == null ? null : ((Number) row[3]).longValue();
            if (uuid == null || field == null || deviceId == null) {
                continue;
            }

            AmqpPointTarget target = new AmqpPointTarget(
                    deviceId,
                    str(row[4]),
                    str(row[2]),
                    str(row[5]),
                    row[6] == null ? null : ((Number) row[6]).longValue(),
                    str(row[7]),
                    str(row[8]),
                    str(row[9]),
                    str(row[10]),
                    str(row[11]));

            AmqpPointTarget previous = loaded.put(key(uuid, field), target);
            if (previous != null && !previous.deviceId().equals(deviceId)) {
                // 같은 uuid+필드를 두 장비에 매핑한 설정 오류 — 뒤에 온 것이 이긴다. 데이터 정정 필요.
                log.warn("AMQP 매핑 중복 uuid={} field={} deviceId {} -> {}",
                        uuid, field, previous.deviceId(), deviceId);
            }
        }

        byUuidAndField.clear();
        byUuidAndField.putAll(loaded);
        log.info("AMQP 포인트 매핑 {}건 로드", byUuidAndField.size());
    }

    /**
     * 이 uuid 의 이 필드를 어느 포인트에 기록할지. 매핑이 없으면 {@code null}
     * — 호출부는 조용히 무시한다(우리가 등록하지 않은 제어기·필드가 계속 들어오기 때문).
     */
    public AmqpPointTarget resolve(String deviceUuid, String field) {
        if (deviceUuid == null || deviceUuid.isBlank() || field == null || field.isBlank()) {
            return null;
        }
        return byUuidAndField.get(key(deviceUuid.trim(), field.trim()));
    }

    public int size() {
        return byUuidAndField.size();
    }

    private static String key(String uuid, String field) {
        return uuid + "::" + field;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
