package com.mpole.hana.digitaltwin.external.emblaze.button;

import com.fasterxml.jackson.databind.JsonNode;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.external.common.StageTrace;
import com.mpole.hana.digitaltwin.external.emblaze.mapping.AmqpPointMappingCache;
import com.mpole.hana.digitaltwin.external.emblaze.mapping.AmqpPointTarget;
import com.mpole.hana.digitaltwin.external.emblaze.mapping.PointMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 물리버튼(enocean) 메시지를 {@link DeviceBundle} 로 만들어 Kafka {@code view01} 에 발행한다.
 *
 * <p>2026-10-01 — 이전에는 {@code EmblazeStatusRouter.route()} 가 버튼 값을 digitaltwin 에서 직접
 * {@code tbl_device_current_value} 에 썼다. 이제 버튼은 gateway 의 뷰 값과 같은 길
 * (Kafka view01 → event 서버)로 보내서, event 가 현재값 기록과 규칙 평가(사건형 suppress_mode=NONE)를
 * 한 번에 한다. 설계: {@code docs/architecture/2026-10-01-wc-button-kafka-design.md}.</p>
 *
 * <h3>번들에는 메시지의 매핑된 필드를 전부 싣는다</h3>
 * <p>event 의 현재값 기록은 {@code points} 를 통째로 교체한다. 버튼 장비의 포인트
 * ({@code switch_number}, {@code index})는 한 메시지에 같이 오므로, {@code route()} 가 하던 대로
 * 필드를 전부 돌며 담으면 잃는 것이 없다. 규칙은 {@code switch_number} 에만 건다.</p>
 *
 * <p>DB 를 치지 않는다 — 장비·포인트 메타는 {@link AmqpPointMappingCache} 가 기동 시 읽어 둔 것이다.
 * 메타가 없는 포인트는 값만 싣는다(등록이 덜 됐다고 사건을 버리면 안 된다).</p>
 *
 * <p>전송은 비동기다. {@code send()} 는 리스너 스레드를 막지 않고, 실패는 별도 로그 한 줄로 남는다
 * (trace 는 "producer 에 넘김" 까지만 안다). 재전송 큐는 없다 — HQS 전송과 같은 노선.</p>
 *
 * <p>2026-10-26 — light·space 큐({@code EmblazeStatusRouter})와 부스 재실 판정 occ({@code BoothCurrentValueWriter})도
 * {@code hdt.integration.emblaze.publish-via-event=true}(기본)면 이 클래스로 발행한다. 이름은 버튼에서 시작해 그대로 둔다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ButtonEventPublisher {

    private final AmqpPointMappingCache amqpPointMappingCache;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** gateway/event 와 같은 토픽이어야 한다(hdt.kafka.topic.view01). */
    @Value("${hdt.kafka.topic.view01:view01}")
    private String topic;

    /**
     * 메시지 1건을 장비별 번들로 묶어 발행한다. 같은 uuid 의 필드가 여러 장비로 갈리면 장비마다 1건.
     *
     * @return 실은 포인트 수(0 이면 발행하지 않음)
     */
    public int publish(String deviceUuid, JsonNode status, OffsetDateTime eventAt, StageTrace trace) {
        if (deviceUuid == null || deviceUuid.isBlank() || status == null || !status.isObject()) {
            trace.fail("kafka", "uuid/status 없음");
            return 0;
        }

        Map<Long, AmqpPointTarget> targetByDevice = new LinkedHashMap<>();
        Map<Long, List<XnPointData>> pointsByDevice = new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = status.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            AmqpPointTarget target = amqpPointMappingCache.resolve(deviceUuid, field.getKey());
            if (target == null) {
                continue; // 등록하지 않은 필드 — 무시(route() 와 같은 규칙)
            }
            JsonNode value = field.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            PointMeta meta = amqpPointMappingCache.resolveMeta(target.deviceId(), target.tagName());

            targetByDevice.putIfAbsent(target.deviceId(), target);
            pointsByDevice.computeIfAbsent(target.deviceId(), k -> new ArrayList<>())
                    .add(XnPointData.builder()
                            .deviceId(target.deviceId())
                            .pointId(meta != null ? meta.pointId() : null)
                            .pointkey(meta != null ? meta.pointKey() : null)
                            .deviceCode(deviceUuid)
                            .pointCode(field.getKey())
                            .objectType(meta != null ? meta.objectType() : null)
                            .pointDisplayName(meta != null ? meta.pointDisplayName() : null)
                            .unit(meta != null ? meta.unit() : null)
                            .tagName(target.tagName())
                            .valueRaw(value.asText())
                            .updateDatetime(eventAt)
                            .build());
        }

        if (pointsByDevice.isEmpty()) {
            trace.fail("kafka", "매핑된 필드 없음 — tbl_device_point.ref_device_code 확인");
            return 0;
        }

        int written = 0;
        for (Map.Entry<Long, List<XnPointData>> entry : pointsByDevice.entrySet()) {
            AmqpPointTarget target = targetByDevice.get(entry.getKey());
            DeviceBundle bundle = DeviceBundle.builder()
                    .deviceId(target.deviceId())
                    .deviceKey(target.deviceKey())
                    .timestamp(eventAt)
                    .points(List.copyOf(entry.getValue()))
                    .build();

            // key=deviceId — gateway 와 같은 규칙. 같은 장비는 같은 파티션이라 순서가 보장된다.
            // 결과는 producer 스레드에서 비동기로 온다. acks=all 이라 "성공" 로그의 offset 은 브로커가 실제로 받아 적은 위치다.
            final int pointCount = entry.getValue().size();
            kafkaTemplate.send(topic, String.valueOf(bundle.deviceId()), bundle)
                    .whenComplete((result, e) -> {
                        if (e != null) {
                            log.error("[ButtonEvent] Kafka 발행 실패(유실) topic={} deviceKey={} uuid={}: {}",
                                    topic, bundle.deviceKey(), deviceUuid, e.getMessage());
                            return;
                        }
                        var md = result.getRecordMetadata();
                        log.info("[ButtonEvent] Kafka 발행 성공 topic={} partition={} offset={} deviceKey={} points={} ts={}",
                                md.topic(), md.partition(), md.offset(), bundle.deviceKey(), pointCount, eventAt);
                    });
            written += entry.getValue().size();

            // route() 가 남기던 map= 단계를 여기서 남긴다(세 큐의 로그 형식을 맞춘다).
            trace.ok("map", target.groupCode() + "/" + target.floorName());
        }
        trace.ok("kafka", written);
        return written;
    }

    /**
     * 포인트 1건을 번들로 발행한다 — digitaltwin 이 만든 파생값(부스 재실 판정 occ 등)용 (2026-10-26).
     * event 서버가 태그 단위 병합(MERGE)으로 기록하므로 같은 장비의 다른 태그는 남는다.
     *
     * @return 발행했으면 true(전송 결과는 비동기 로그)
     */
    public boolean publishPoint(Long deviceId, String deviceKey, String tagName, String value, OffsetDateTime eventAt) {
        if (deviceId == null || tagName == null || tagName.isBlank()) {
            log.warn("[EmblazeEvent] 발행 생략 — deviceId/tagName 없음 deviceId={} tagName={}", deviceId, tagName);
            return false;
        }
        PointMeta meta = amqpPointMappingCache.resolveMeta(deviceId, tagName);
        DeviceBundle bundle = DeviceBundle.builder()
                .deviceId(deviceId)
                .deviceKey(deviceKey)
                .timestamp(eventAt)
                .points(List.of(XnPointData.builder()
                        .deviceId(deviceId)
                        .pointId(meta != null ? meta.pointId() : null)
                        .pointkey(meta != null ? meta.pointKey() : null)
                        .deviceCode(meta != null ? meta.deviceCode() : null)
                        .pointCode(meta != null ? meta.pointCode() : null)
                        .objectType(meta != null ? meta.objectType() : null)
                        .pointDisplayName(meta != null ? meta.pointDisplayName() : null)
                        .unit(meta != null ? meta.unit() : null)
                        .tagName(tagName)
                        .valueRaw(value)
                        .updateDatetime(eventAt)
                        .build()))
                .build();

        kafkaTemplate.send(topic, String.valueOf(deviceId), bundle)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        log.error("[EmblazeEvent] Kafka 발행 실패(유실) topic={} deviceKey={} tag={}: {}",
                                topic, deviceKey, tagName, e.getMessage());
                        return;
                    }
                    var md = result.getRecordMetadata();
                    log.debug("[EmblazeEvent] Kafka 발행 성공 topic={} partition={} offset={} deviceKey={} tag={} value={}",
                            md.topic(), md.partition(), md.offset(), deviceKey, tagName, value);
                });
        return true;
    }
}
