package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.XnPointData;
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

        // upsertPointByTag 는 "단일 원소 배열"을 받는다 — 기존 배열에서 같은 태그를 제거한 뒤 append 한다.
        List<XnPointData> one = List.of(XnPointData.builder()
                .deviceId(deviceId)
                .pointId(0L)
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
