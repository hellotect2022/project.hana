package com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc;

import com.mpole.hana.digitaltwin.integration.emblaze.amqp.EmblazeCurrentValueWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 화장실 도메인이 쓰는 태그명·값 상수와 기록 창구.
 *
 * <p>실제 쓰기는 {@link EmblazeCurrentValueWriter} 에 위임한다 — 태그 단위 병합 upsert 라
 * 같은 장비의 다른 포인트를 지우지 않는다. 여기에는 <b>화장실에서만 의미가 있는 값</b>
 * (재실 0/1, 버튼 태그명)만 남긴다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoothCurrentValueWriter {

    /** 재실 포인트 태그명. 조회(boothStatus)도 이 값으로 찾는다. */
    public static final String TAG_OCC = "occ";

    /** 재실 */
    public static final String OCC_OCCUPIED = "1";
    /** 비재실 */
    public static final String OCC_VACANT = "0";

    private final EmblazeCurrentValueWriter emblazeCurrentValueWriter;

    /**
     * 재실 상태 기록. 같은 장비의 {@code pir_accumulation} 원본값은 보존된다.
     *
     * @param value {@link #OCC_OCCUPIED} 또는 {@link #OCC_VACANT}
     */
    public void writeOccupancy(Long deviceId, String deviceKey, String value, OffsetDateTime updatedAt) {
        emblazeCurrentValueWriter.writePoint(deviceId, deviceKey, TAG_OCC, value, updatedAt);
        log.info("부스 재실 기록 deviceId={} occ={}", deviceId, value);
    }

    /** 파생·집계값 기록(누적 카운트 등). */
    public void writePoint(Long deviceId, String deviceKey, String tagName,
                           String value, OffsetDateTime updatedAt) {
        emblazeCurrentValueWriter.writePoint(deviceId, deviceKey, tagName, value, updatedAt);
    }
}
