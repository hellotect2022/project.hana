package com.mpole.hana.digitaltwin.external.emblaze.write;

import com.mpole.hana.digitaltwin.external.common.BoothOccupancy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 화장실 도메인이 쓰는 태그명·값 상수와 기록 창구.
 *
 * <p>실제 쓰기는 {@link EmblazeCurrentValueWriter} 에 위임한다 — 태그 단위 병합 upsert 라
 * 같은 장비의 다른 포인트를 지우지 않는다.</p>
 *
 * <p>재실 값·태그명은 {@link BoothOccupancy} 가 들고 있다 — 읽는 쪽(HQS 조회)도 같은 값을
 * 봐야 하는데 여기 두면 그쪽이 emblaze 를 참조하게 된다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoothCurrentValueWriter {

    private final EmblazeCurrentValueWriter emblazeCurrentValueWriter;

    /**
     * 재실 상태 기록. 같은 장비의 {@code pir_accumulation} 원본값은 보존된다.
     *
     * @param value {@link BoothOccupancy#OCCUPIED} 또는 {@link BoothOccupancy#VACANT}
     */
    public void writeOccupancy(Long deviceId, String deviceKey, String value, OffsetDateTime updatedAt) {
        emblazeCurrentValueWriter.writePoint(deviceId, deviceKey, BoothOccupancy.TAG, value, updatedAt);
        log.info("부스 재실 기록 deviceId={} occ={}", deviceId, value);
    }
}
