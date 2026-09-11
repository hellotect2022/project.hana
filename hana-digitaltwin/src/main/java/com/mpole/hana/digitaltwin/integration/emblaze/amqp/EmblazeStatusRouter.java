package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Map;

/**
 * emblaze 메시지의 {@code status} 필드들을 우리 포인트로 흘려보낸다.
 *
 * <p>큐마다 따로 파싱하지 않는다 — {@code (uuid, 필드명)} 매핑이 이미 장비를 결정하므로
 * <b>status 안의 필드를 하나씩 훑어 매핑된 것만 기록</b>하면 큐가 몇 개든 코드가 같다.
 * 새 필드를 받고 싶으면 {@code tbl_device_point} 에 행을 추가하고 캐시를 reload 하면 되고
 * 자바 코드는 손대지 않는다.</p>
 *
 * <p>매핑이 없는 필드·uuid 는 조용히 버린다 — 우리가 등록하지 않은 제어기(사무실·복도 조명 등)의
 * 메시지가 같은 큐로 계속 들어오기 때문이다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmblazeStatusRouter {

    private final AmqpPointMappingCache amqpPointMappingCache;
    private final EmblazeCurrentValueWriter emblazeCurrentValueWriter;

    /**
     * @param deviceUuid emblaze 조명제어기 uuid
     * @param status     메시지의 {@code status} 오브젝트
     * @param eventAt    센서 타임스탬프
     * @return 실제로 기록한 포인트 수
     */
    public int route(String deviceUuid, JsonNode status, OffsetDateTime eventAt) {
        if (deviceUuid == null || deviceUuid.isBlank() || status == null || !status.isObject()) {
            return 0;
        }

        int written = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = status.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            AmqpPointTarget target = amqpPointMappingCache.resolve(deviceUuid, field.getKey());
            log.info("target 을 찾았음 : {}",target);
            if (target == null) {
                continue; // 등록하지 않은 필드 — 무시
            }

            JsonNode value = field.getValue();
            if (value == null || value.isNull()) {
                continue;
            }

            emblazeCurrentValueWriter.writePoint(
                    target.deviceId(), target.deviceKey(), target.tagName(),
                    value.asText(), eventAt);
            written++;
        }

        if (written == 0) {
            log.debug("매핑된 필드 없음 uuid={} fields={}", deviceUuid, status.fieldNames());
        }
        return written;
    }

    /** ISO-8601 파싱. 실패하면 수신 시각을 쓴다 — 타임스탬프 때문에 이벤트를 버리지 않는다. */
    public static OffsetDateTime parseTimestamp(String timestamp) {
        try {
            return OffsetDateTime.parse(timestamp);
        } catch (DateTimeParseException | NullPointerException e) {
            log.warn("timestamp 파싱 실패 '{}' → 수신시각 사용", timestamp);
            return OffsetDateTime.now();
        }
    }
}
