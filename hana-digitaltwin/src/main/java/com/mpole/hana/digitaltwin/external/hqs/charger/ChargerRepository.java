package com.mpole.hana.digitaltwin.external.hqs.charger;

import com.mpole.hana.digitaltwin.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * HQS 전기차 충전기 조회.
 *
 * <p>충전기는 카테고리 {@code ELEC > EV > DCC(급속) / ACC(완속)} 의 장비이고,
 * <b>충전소는 그 장비들이 배치된 구역(zone)</b> 이다. 별도 충전소 테이블이 없다.</p>
 */
@Repository
public interface ChargerRepository extends JpaRepository<Device, Long> {

    /** 급속·완속 충전기 소분류 코드 */
    String CATEGORY_FAST = "DCC";
    String CATEGORY_SLOW = "ACC";

    /**
     * 충전소(구역) 기준 충전기 목록.
     *
     * <p>{@code deviceCurrentValue} 까지 fetch 해 상태 포인트를 N+1 없이 읽는다.</p>
     *
     * @param zoneIds 충전소 구역 + 하위 구역. 자식 구역에 충전기를 배치할 수도 있어 서브트리로 받는다
     */
    @Query("""
            SELECT DISTINCT d FROM Device d
            JOIN FETCH d.category c
            LEFT JOIN FETCH d.placement p
            LEFT JOIN FETCH p.unityZone z
            LEFT JOIN FETCH z.floor f
            LEFT JOIN FETCH d.deviceCurrentValue
            WHERE c.categoryCode IN ('DCC', 'ACC')
              AND z.zoneId IN :zoneIds
            ORDER BY c.categoryCode ASC, d.deviceId ASC
            """)
    List<Device> findChargersByZoneIds(@Param("zoneIds") List<Long> zoneIds);

    /**
     * 충전기 전체(충전소 무관). {@code stationId} 를 지정하지 않았을 때 쓴다.
     * <p>배치되지 않은 충전기는 소속 충전소를 알 수 없어 제외한다.</p>
     */
    @Query("""
            SELECT DISTINCT d FROM Device d
            JOIN FETCH d.category c
            JOIN FETCH d.placement p
            JOIN FETCH p.unityZone z
            LEFT JOIN FETCH z.floor f
            LEFT JOIN FETCH d.deviceCurrentValue
            WHERE c.categoryCode IN ('DCC', 'ACC')
            ORDER BY z.zoneId ASC, c.categoryCode ASC, d.deviceId ASC
            """)
    List<Device> findAllChargers();
}
