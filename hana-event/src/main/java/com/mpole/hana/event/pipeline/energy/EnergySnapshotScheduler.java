package com.mpole.hana.event.pipeline.energy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * 누적 전력량 5분 스냅샷 (2026-10-12) — 금일·금월 전력량의 기준값.
 *
 * <pre>
 *   5분 정각(00:00, 00:05 …, KST)마다
 *     tbl_device_current_value.points(JSONB) 에서 누적 포인트(스키마 accumulate=true)의 현재값을 꺼내
 *     tbl_energy_snapshot(bucket_at, point_id, device_id, value, source_at) 에 INSERT
 * </pre>
 *
 * <ul>
 *   <li>값은 이미 배율이 적용된 값이다 — 현재값 자체가 배율 적용 후 저장된다({@link PointMetaCache#applyScale}).</li>
 *   <li>같은 5분을 두 번 넣어도 PK(point_id, bucket_at) 로 무시된다(재기동·중복 실행 안전).</li>
 *   <li>숫자가 아닌 값·빈 값은 건너뛴다. 통신이 끊겨 값이 멈춰 있어도 그대로 찍고 {@code source_at} 으로 묵은 값임을 남긴다.</li>
 *   <li>event 서버가 내려가 있던 구간은 비는데, 금일·금월 계산(digitaltwin EnergySummaryService)이 직전 스냅샷으로 흡수한다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnergySnapshotScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(60);

    /**
     * JSONB 현재값에서 누적 포인트만 펼쳐 넣는다. 숫자 형식이 아닌 값은 제외.
     * points 원소는 XnPointData 직렬화 — pointId / valueRaw / updateDatetime 키.
     */
    private static final String SNAPSHOT_SQL = """
            INSERT INTO tbl_energy_snapshot (bucket_at, point_id, device_id, value, source_at)
            SELECT :bucketAt,
                   (pt->>'pointId')::bigint,
                   cv.device_id,
                   (pt->>'valueRaw')::double precision,
                   CASE WHEN pt->>'updateDatetime' IS NOT NULL THEN (pt->>'updateDatetime')::timestamptz END
              FROM tbl_device_current_value cv
              CROSS JOIN LATERAL jsonb_array_elements(COALESCE(cv.points, '[]'::jsonb)) pt
             WHERE (pt->>'pointId')::bigint = ANY(:pointIds)
               AND (pt->>'valueRaw') ~ '^-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?$'
            ON CONFLICT (point_id, bucket_at) DO NOTHING
            """;

    private final DatabaseClient postgresDatabaseClient;
    private final PointMetaCache pointMetaCache;

    @Value("${hdt.energy.snapshot-enabled:true}")
    private boolean enabled;

    /** 5분 정각마다(KST). cron 초 단위: 초 분 시 일 월 요일 */
    @Scheduled(cron = "${hdt.energy.snapshot-cron:0 */5 * * * *}", zone = "Asia/Seoul", scheduler = "eventTaskScheduler")
    public void snapshot() {
        if (!enabled) return;
        Set<Long> ids = pointMetaCache.accumulatePointIds();
        if (ids.isEmpty()) {
            log.debug("[EnergySnapshot] 누적 포인트 없음 — 건너뜀(카테고리 스키마에서 누적을 켜야 대상이 생긴다)");
            return;
        }
        // 실행이 몇 초 늦어도 버킷은 5분 정각으로 내린다(13:05:02 → 13:05:00)
        ZonedDateTime now = ZonedDateTime.now(KST);
        OffsetDateTime bucketAt = now.truncatedTo(ChronoUnit.MINUTES)
                .withMinute(now.getMinute() - now.getMinute() % 5)
                .toOffsetDateTime();
        try {
            Long rows = postgresDatabaseClient.sql(SNAPSHOT_SQL)
                    .bind("bucketAt", bucketAt)
                    .bind("pointIds", ids.toArray(new Long[0]))
                    .fetch()
                    .rowsUpdated()
                    .block(BLOCK_TIMEOUT);
            log.info("[EnergySnapshot] {} 스냅샷 {}건 / 대상 {}포인트", bucketAt, rows, ids.size());
        } catch (Exception e) {
            log.error("[EnergySnapshot] {} 스냅샷 실패(다음 5분에 계속): {}", bucketAt, e.getMessage());
        }
    }
}
