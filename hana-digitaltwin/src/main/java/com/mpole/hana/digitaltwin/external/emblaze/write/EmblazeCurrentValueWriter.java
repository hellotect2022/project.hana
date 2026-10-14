package com.mpole.hana.digitaltwin.external.emblaze.write;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.external.emblaze.mapping.AmqpPointMappingCache;
import com.mpole.hana.digitaltwin.external.emblaze.mapping.PointMeta;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceCurrentValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * RabbitMQ 로 받은 값을 {@code tbl_device_current_value.points} 에 기록한다.
 *
 * <p>한 장비가 포인트를 여러 개 갖는다(조명 = present_level·target_level·remaining_time).
 * 배열을 통째로 교체하면 서로를 지우므로 반드시
 * {@link DeviceCurrentValueRepository#upsertPointByTag} (태그 단위 병합)를 쓴다.
 * 병합은 SQL 한 문장에서 원자적으로 끝나 읽기-수정-쓰기 왕복이 없고,
 * 재기동 직후에도 다른 태그의 값을 잃지 않는다.</p>
 *
 * <p>엔티티 {@code save()} 는 쓰지 않는다(로드 스냅샷의 points 를 통째로 되씀).
 * {@code connection_state} 도 건드리지 않는다 — event 소유 컬럼이다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmblazeCurrentValueWriter {

    private final DeviceCurrentValueRepository deviceCurrentValueRepository;
    private final ObjectMapper objectMapper;
    private final AmqpPointMappingCache amqpPointMappingCache;

    /**
     * 포인트 1건을 태그 단위로 병합 기록한다.
     * <p>같은 {@code tagName} 원소만 교체되고 나머지 포인트는 그대로 남는다.</p>
     */
    @Transactional
    public void writePoint(Long deviceId, String deviceKey, String tagName,
                           String value, OffsetDateTime updatedAt) {
        if (deviceId == null) {
            log.warn("writePoint: deviceId 없음 tagName={}", tagName);
            return;
        }
        if (tagName == null || tagName.isBlank()) {
            log.warn("writePoint: tagName 없음 deviceId={}", deviceId);
            return;
        }

        // points 는 JSONB 라 여기 적은 것이 전부다 — 조회 API 는 그 JSON 을 그대로 읽어 내보내지
        // tbl_device_point 와 다시 조인하지 않는다. 안 채우면 영원히 빈 채로 남는다.
        // 메타는 캐시가 기동 시 읽어 둔 것이라 DB 를 치지 않는다. 없으면 값만 적는다
        // — 등록이 덜 됐다고 값을 버리면 안 된다. alarmYn 은 엑센 뷰 값이라 원천이 없다.
        PointMeta meta = amqpPointMappingCache.resolveMeta(deviceId, tagName);
        if (meta == null) {
            log.debug("포인트 메타 없음 deviceId={} tagName={} — 값만 기록한다", deviceId, tagName);
        }

        // upsertPointByTag 는 "단일 원소 배열"을 받는다 — 기존 배열에서 같은 태그를 제거한 뒤 append 한다.
        List<XnPointData> one = List.of(XnPointData.builder()
                .deviceId(deviceId)
                .pointId(meta == null ? 0L : meta.pointId())
                .pointkey(meta == null ? null : meta.pointKey())   // 필드명이 소문자 k 다
                .pointDisplayName(meta == null ? null : meta.pointDisplayName())
                .unit(meta == null ? null : meta.unit())
                .objectType(meta == null ? null : meta.objectType())
                .deviceCode(meta == null ? null : meta.deviceCode())
                .pointCode(meta == null ? null : meta.pointCode())
                .tagName(tagName)
                .valueRaw(value)
                .updateDatetime(updatedAt)
                .build());

        try {
            String json = objectMapper.writeValueAsString(one);
            deviceCurrentValueRepository.upsertPointByTag(deviceId, deviceKey, tagName, json);
            log.debug("현재값 기록 deviceId={} deviceKey={} {}={}", deviceId, deviceKey, tagName, value);
        } catch (JsonProcessingException e) {
            log.error("현재값 직렬화 실패 deviceId={} tagName={}", deviceId, tagName, e);
        } catch (Exception e) {
            log.error("현재값 upsert 실패 deviceId={} tagName={}", deviceId, tagName, e);
        }
    }

}
