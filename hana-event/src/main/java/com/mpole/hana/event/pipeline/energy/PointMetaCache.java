package com.mpole.hana.event.pipeline.energy;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.XnPointData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 포인트별 저장 설정 캐시 — 카테고리 스키마(schema_definition) 의 태그 설정을 point_id 단위로 펼친 것 (2026-10-12).
 *
 * <pre>
 *   scale       배율. 원본 × scale 이 저장·표시값. 1 이 아닌 포인트만 담는다 (수변전 전력·전력량 ÷1000 등)
 *   accumulate  누적 포인트. 5분 스냅샷 대상(금일·금월 전력량)
 * </pre>
 *
 * <p><b>배율은 Kafka 수신 직후 한 곳에서 적용한다</b>({@link #applyScale}, View01KafkaConsumer 가 호출). 그 뒤 현재값·값 이력·
 * 이벤트 평가·웹소켓·스냅샷이 모두 같은 배율 적용값을 쓴다. 원본값은 DB 에 남지 않는다(2026-10-12 사용자 결정 — 저장부터 표시 단위로).
 * 그래서 배율을 나중에 바꾸면 그 이전 이력과 단위가 달라지고, 그 태그의 이벤트 규칙 임계값도 배율 적용 단위로 적어야 한다.</p>
 *
 * <p>스키마는 관리자 화면(장비 카테고리 관리 → 포인트 스키마 "고급")에서 바뀌므로 30초마다 다시 읽는다(규칙 캐시와 같은 방식).
 * 불변 스냅샷을 volatile 로 통째로 바꿔 끼워 읽는 쪽은 잠금 없이 본다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointMetaCache {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    /** 스키마 태그 설정을 포인트로 펼친다. 배율이 1 이 아니거나 누적인 포인트만. 장비 분류가 없으면 대상 아님 */
    private static final String LOAD_SQL = """
            SELECT p.point_id,
                   p.device_id,
                   CASE WHEN (s->>'scale') ~ '^-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?$'
                        THEN (s->>'scale')::double precision END                    AS scale,
                   COALESCE((s->>'accumulate')::boolean, false)                      AS accumulate
              FROM tbl_device_point p
              JOIN tbl_device d          ON d.device_id = p.device_id
              JOIN tbl_device_category c ON c.category_id = d.category_id
              CROSS JOIN LATERAL jsonb_array_elements(COALESCE(c.schema_definition, '[]'::jsonb)) s
             WHERE s->>'tagName' = p.tag_name
               AND (COALESCE((s->>'accumulate')::boolean, false)
                    OR ((s->>'scale') IS NOT NULL AND (s->>'scale') NOT IN ('', '1', '1.0')))
            """;

    private final DatabaseClient postgresDatabaseClient;

    private record Snapshot(Map<Long, BigDecimal> scaleByPoint, Map<Long, Long> accumulateDeviceByPoint) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
    }

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    /** 기동 시 1회(블로킹). 실패해도 기동은 계속한다 — 배율 없이 원본이 흐르는 것보다 기동 실패가 더 나쁘지 않으므로 다음 주기에 재시도 */
    @PostConstruct
    public void loadOnStartup() {
        try {
            refresh();
        } catch (Exception e) {
            log.error("[PointMetaCache] 기동 로드 실패(30초 뒤 재시도, 그 사이 배율 미적용): {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${hdt.energy.meta-refresh-ms:30000}",
            initialDelayString = "${hdt.energy.meta-refresh-ms:30000}",
            scheduler = "eventTaskScheduler")
    public void refreshPeriodically() {
        try {
            refresh();
        } catch (Exception e) {
            log.error("[PointMetaCache] 리로드 실패(이전 설정 유지): {}", e.getMessage());
        }
    }

    private void refresh() {
        Map<Long, BigDecimal> scale = new HashMap<>();
        Map<Long, Long> accumulate = new HashMap<>();
        postgresDatabaseClient.sql(LOAD_SQL)
                .map((row, meta) -> {
                    Long pointId = row.get("point_id", Long.class);
                    Long deviceId = row.get("device_id", Long.class);
                    Double s = row.get("scale", Double.class);
                    Boolean acc = row.get("accumulate", Boolean.class);
                    if (pointId != null) {
                        if (s != null && s != 1.0d) scale.put(pointId, BigDecimal.valueOf(s));
                        if (Boolean.TRUE.equals(acc)) accumulate.put(pointId, deviceId);
                    }
                    return pointId == null ? 0L : pointId;
                })
                .all()
                .then()
                .block(BLOCK_TIMEOUT);

        Snapshot prev = snapshot;
        snapshot = new Snapshot(Collections.unmodifiableMap(scale), Collections.unmodifiableMap(accumulate));
        if (prev.scaleByPoint().size() != scale.size() || prev.accumulateDeviceByPoint().size() != accumulate.size()) {
            log.info("[PointMetaCache] 포인트 설정 갱신. 배율 포인트={}, 누적 포인트={}", scale.size(), accumulate.size());
        }
    }

    /** 누적(5분 스냅샷) 대상 point_id. */
    public Set<Long> accumulatePointIds() {
        return snapshot.accumulateDeviceByPoint().keySet();
    }

    /**
     * 번들의 값에 배율을 적용한 새 번들 목록. 배율 대상이 없으면 원래 객체를 그대로 돌려준다(복사 비용 0).
     * 숫자가 아닌 값("ok" 등)은 그대로 둔다. 소수 오차가 없도록 BigDecimal 로 곱하고 뒤의 0 은 지운다(1234567 × 0.001 → "1234.567").
     */
    public List<DeviceBundle> applyScale(List<DeviceBundle> bundles) {
        Map<Long, BigDecimal> scale = snapshot.scaleByPoint();
        if (scale.isEmpty() || bundles == null || bundles.isEmpty()) return bundles;

        List<DeviceBundle> out = null;
        for (int i = 0; i < bundles.size(); i++) {
            DeviceBundle b = bundles.get(i);
            DeviceBundle scaled = scaleBundle(b, scale);
            if (scaled != b && out == null) {
                out = new ArrayList<>(bundles.subList(0, i));
            }
            if (out != null) out.add(scaled);
        }
        return out == null ? bundles : out;
    }

    private static DeviceBundle scaleBundle(DeviceBundle b, Map<Long, BigDecimal> scale) {
        if (b == null || b.points() == null || b.points().isEmpty()) return b;
        List<XnPointData> points = null;
        for (int i = 0; i < b.points().size(); i++) {
            XnPointData p = b.points().get(i);
            BigDecimal factor = (p == null || p.pointId() == null) ? null : scale.get(p.pointId());
            XnPointData np = factor == null ? p : scalePoint(p, factor);
            if (np != p && points == null) {
                points = new ArrayList<>(b.points().subList(0, i));
            }
            if (points != null) points.add(np);
        }
        return points == null ? b : new DeviceBundle(b.deviceId(), b.deviceKey(), b.timestamp(), points);
    }

    private static XnPointData scalePoint(XnPointData p, BigDecimal factor) {
        String raw = p.valueRaw();
        if (raw == null || raw.isBlank()) return p;
        try {
            BigDecimal v = new BigDecimal(raw.trim()).multiply(factor).stripTrailingZeros();
            return p.toBuilder().valueRaw(v.toPlainString()).build();
        } catch (NumberFormatException e) {
            return p;   // 숫자가 아닌 값은 배율 대상 아님
        }
    }
}
