package com.mpole.hana.digitaltwin.parking.service;

import com.mpole.hana.digitaltwin.parking.dto.ParkingOccupancy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주차면 점유 현황 (2026-10-14).
 *
 * <pre>
 *   대상  tbl_device_category.category_code ∈ (PA-C, PA-D, PA-E, PA-R) — SQL 에 고정
 *   상태  tbl_device_current_value.points 의 tagName = 'st' 값 — Y = 주차, N = 빈자리, 그 밖·없음 = 알 수 없음
 *   층    장비 배치(tbl_device_placement) → 구역(tbl_unity_loc_zone) → 층(tbl_unity_loc_floor)
 * </pre>
 *
 * <p><b>현재값이 아니라 장비 목록에서 출발한다</b> — 현재값이 아직 없는 센서도 전체 주차면 수에 들어가고 unknown 으로 따로 센다.
 * 잔여는 N 인 칸만 센다(값이 없는 센서를 빈자리로 치지 않는다).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParkingOccupancyService {

    private static final String SQL = """
            SELECT f.floor_id,
                   f.floor_name,
                   count(*)                                                                AS total,
                   count(*) FILTER (WHERE upper(st.v) = 'Y')                               AS parked,
                   count(*) FILTER (WHERE upper(st.v) = 'N')                               AS available,
                   count(*) FILTER (WHERE st.v IS NULL OR upper(st.v) NOT IN ('Y', 'N'))   AS unknown
              FROM tbl_device d
              JOIN tbl_device_category dc ON dc.category_id = d.category_id
              LEFT JOIN tbl_device_current_value cv ON cv.device_id = d.device_id
              LEFT JOIN LATERAL (
                    SELECT trim(pt->>'valueRaw') AS v
                      FROM jsonb_array_elements(COALESCE(cv.points, '[]'::jsonb)) pt
                     WHERE pt->>'tagName' = 'st'
                     LIMIT 1
              ) st ON TRUE
              LEFT JOIN tbl_device_placement pl ON pl.device_id = d.device_id
              LEFT JOIN tbl_unity_loc_zone   z  ON z.zone_id    = pl.unity_zone_id
              LEFT JOIN tbl_unity_loc_floor  f  ON f.floor_id   = z.floor_id
             WHERE dc.category_code IN ('PA-C', 'PA-D', 'PA-E', 'PA-R')
             GROUP BY f.floor_id, f.floor_name, f.sort_order
             ORDER BY f.sort_order DESC NULLS LAST
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ParkingOccupancy occupancy() {
        List<ParkingOccupancy.Floor> floors = jdbc.query(SQL,
                (rs, i) -> new ParkingOccupancy.Floor(
                        rs.getObject("floor_id") == null ? null : rs.getLong("floor_id"),
                        rs.getString("floor_name"),
                        rs.getLong("total"),
                        rs.getLong("parked"),
                        rs.getLong("available"),
                        rs.getLong("unknown")));

        long total = 0, parked = 0, available = 0, unknown = 0;
        for (ParkingOccupancy.Floor f : floors) {
            total += f.total();
            parked += f.parked();
            available += f.available();
            unknown += f.unknown();
        }
        return new ParkingOccupancy(total, parked, available, unknown, floors);
    }
}
