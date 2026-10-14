package com.mpole.hana.digitaltwin.external.hqs.charger;

import com.mpole.hana.digitaltwin.external.hqs.charger.dto.HqsChargerItem;
import com.mpole.hana.digitaltwin.external.hqs.charger.dto.HqsConnectorItem;
import com.mpole.hana.digitaltwin.external.hqs.charger.dto.HqsStationItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HQS 전기차 충전기 조회.
 *
 * <pre>
 *   충전소(station) = 장비 그룹             ← group_type='EV_STATION', group_code 가 stationId
 *   충전기(charger) = 그 그룹의 멤버 장비    ← 카테고리 DCC(급속) / ACC(완속)
 *   충전구(connector) = 상태 포인트 1개      ← 충전기당 1개로 취급
 * </pre>
 *
 * <p><b>충전기 1대 = 충전구 1개로 본다.</b> 우리가 수집하는 건 충전기 단위 상태 포인트
 * {@code st} 하나뿐이라 충전구를 구분할 근거가 없다. 실제 급속기에 커넥터가 여러 개라면
 * XN 에서 충전구별 포인트를 받아야 하고, 그때 배열에 항목이 늘어난다
 * (배열 구조를 유지해 둔 이유다).</p>
 *
 * <p>예전에는 충전소가 구역(zone)이었고, 여기서 배치를 훑어 자바로 묶었다. 그룹이
 * 이미 묶음 단위라 그 과정이 사라졌다 — 자세한 배경은 {@link ChargerRepository} 주석 참고.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargerApiService {

    private static final String TYPE_FAST = "DC_FAST";
    private static final String TYPE_SLOW = "AC_SLOW";

    private final ChargerRepository chargerRepository;

    /**
     * 충전소별 충전기 목록.
     *
     * @param stationId 충전소 그룹의 {@code group_code}.
     *                  <b>비우면 전체 충전소</b>를 반환한다(현재 충전소가 한 곳뿐이라 실질 동일).
     * @return 충전소 단위로 묶은 목록. 해당 stationId 가 없으면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<HqsStationItem> getChargerList(String stationId) {
        String filter = StringUtils.hasText(stationId) ? stationId.trim() : null;
        List<Object[]> rows = chargerRepository.findChargersByStation(filter);

        if (rows.isEmpty() && filter != null) {
            // 없는 충전소인지, 충전기가 비어 있는 충전소인지 여기서는 구분되지 않는다.
            // 어느 쪽이든 HQS 에는 빈 목록이 맞고, 원인은 로그로 남긴다.
            log.warn("충전기 0건 stationId={} — 그룹이 없거나 멤버가 비어 있다", filter);
        }

        // 쿼리가 sort_order·group_code·카테고리·device_id 순으로 정렬해 내려주므로
        // LinkedHashMap 이면 그 순서가 그대로 유지된다(HashMap 이면 호출마다 달라진다).
        Map<String, HqsStationItem> byStation = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String stationCode = str(row[0]);
            HqsStationItem station = byStation.computeIfAbsent(stationCode, code ->
                    HqsStationItem.builder()
                            .stationId(code)
                            .stationName(str(row[1]))
                            .chargers(new ArrayList<>())
                            .build());

            HqsConnectorItem connector = HqsConnectorItem.builder()
                    .status(ChargerStatus.fromRaw(str(row[5])))
                    .updatedAt(resolveUpdatedAt(str(row[6]), toOffsetDateTime(row[7])))
                    .build();

            station.getChargers().add(HqsChargerItem.builder()
                    .chargerId(str(row[3]))
                    .chargerName(str(row[4]))
                    .type(resolveType(str(row[2]), str(row[3])))
                    .connectors(List.of(connector))
                    .build());
        }
        return new ArrayList<>(byStation.values());
    }

    /** 카테고리 소분류 코드 → HQS 충전기 타입. */
    private String resolveType(String categoryCode, String chargerId) {
        if (ChargerRepository.CATEGORY_FAST.equals(categoryCode)) {
            return TYPE_FAST;
        }
        if (ChargerRepository.CATEGORY_SLOW.equals(categoryCode)) {
            return TYPE_SLOW;
        }
        log.warn("충전기 타입을 판정할 수 없습니다. deviceKey={} categoryCode={}", chargerId, categoryCode);
        return null;
    }

    /**
     * 상태 포인트의 수집 시각. 없거나 형식이 깨졌으면 행 자체의 갱신 시각으로 떨어진다.
     *
     * <p>파싱을 SQL 이 아니라 여기서 하는 이유는 {@link ChargerRepository} 주석 참고 —
     * 값 하나가 깨졌다고 쿼리 전체가 실패하면 안 된다.</p>
     */
    private OffsetDateTime resolveUpdatedAt(String pointUpdated, OffsetDateTime rowUpdated) {
        if (!StringUtils.hasText(pointUpdated)) {
            return rowUpdated;
        }
        try {
            return OffsetDateTime.parse(pointUpdated);
        } catch (DateTimeParseException e) {
            log.warn("상태 포인트 시각 파싱 실패 '{}' → 행 갱신시각 사용", pointUpdated);
            return rowUpdated;
        }
    }

    /**
     * native 쿼리가 돌려준 timestamptz 를 {@link OffsetDateTime} 으로.
     *
     * <p>Hibernate 는 native 쿼리의 timestamptz 를 버전·드라이버에 따라
     * {@code OffsetDateTime}/{@code Timestamp}/{@code Instant} 중 하나로 준다.
     * 한쪽으로 캐스팅해 두면 의존성을 올렸을 때 {@code ClassCastException} 으로 터진다.</p>
     */
    private static OffsetDateTime toOffsetDateTime(Object o) {
        return switch (o) {
            case null -> null;
            case OffsetDateTime odt -> odt;
            case Instant instant -> instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
            case Timestamp ts -> ts.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            default -> {
                log.warn("알 수 없는 시각 타입 {} — null 로 처리한다", o.getClass().getName());
                yield null;
            }
        };
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
