package com.mpole.hana.event.pipeline.value;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.XnPointData;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 태그 단위 병합 — 같은 배치 안에서 장비 묶음 합치기 (2026-10-26) */
class CurrentValueMergeTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");

    private static XnPointData pt(String tag, String value) {
        return XnPointData.builder().tagName(tag).valueRaw(value).build();
    }

    private static DeviceBundle bundle(long deviceId, OffsetDateTime at, XnPointData... points) {
        return new DeviceBundle(deviceId, "DEV-" + deviceId, at, List.of(points));
    }

    @Test
    void 일부_묶음_두개는_태그가_모두_남는다() {
        List<DeviceBundle> out = CurrentValueService.mergeByDevice(List.of(
                bundle(1, T0, pt("occ", "1")),
                bundle(1, T0.plusSeconds(1), pt("switch_number", "3"))));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).points()).extracting(XnPointData::tagName).containsExactly("occ", "switch_number");
        assertThat(out.get(0).timestamp()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void 같은_태그는_시각이_늦은_묶음이_이긴다_입력순서와_무관() {
        // event 소비자는 묶음을 시각 내림차순으로 넘긴다 — 늦은 묶음이 앞에 와도 최신 값이 남아야 한다
        List<DeviceBundle> out = CurrentValueService.mergeByDevice(List.of(
                bundle(1, T0.plusSeconds(5), pt("st", "2")),
                bundle(1, T0, pt("st", "1"))));

        assertThat(out.get(0).points()).extracting(XnPointData::valueRaw).containsExactly("2");
    }

    @Test
    void 장비별로_따로_합친다() {
        List<DeviceBundle> out = CurrentValueService.mergeByDevice(List.of(
                bundle(1, T0, pt("a", "1")),
                bundle(2, T0, pt("a", "9")),
                bundle(1, T0.plusSeconds(1), pt("b", "2"))));

        assertThat(out).hasSize(2);
        DeviceBundle d1 = out.stream().filter(b -> b.deviceId() == 1L).findFirst().orElseThrow();
        assertThat(d1.points()).extracting(XnPointData::tagName).containsExactly("a", "b");
    }

    @Test
    void 장비id_없는_묶음과_null_원소는_건너뛴다() {
        List<DeviceBundle> out = CurrentValueService.mergeByDevice(List.of(
                new DeviceBundle(null, "X", T0, List.of(pt("a", "1"))),
                new DeviceBundle(3L, "DEV-3", null, java.util.Arrays.asList(pt("a", "1"), null))));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).points()).hasSize(1);
    }
}
