package com.mpole.hana.digitaltwin.external.emblaze.mapping;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code (device_uuid, 벤더 필드명)} → 우리 포인트 매핑 캐시.
 *
 * <p>emblaze 는 센서·스위치의 구별용 ID 를 주지 않고 <b>모든 메시지를 조명제어기 UUID</b> 로 보낸다.
 * 그래서 uuid 만으로는 장비를 특정할 수 없다 — 한 uuid 에 조명·재실센서·버튼 장비 3대가 걸린다.
 * 큐마다 필드명이 다르다는 점을 이용해 <b>uuid + 필드명</b>을 키로 쓴다.</p>
 *
 * <p>매핑 원본은 {@code tbl_device_point.ref_device_code / ref_point_code} 다.
 * 장비·포인트를 추가하거나 uuid 를 바꾼 뒤에는 {@link #reload()} 를 호출해야 반영된다.</p>
 *
 * <p>부스 신원({@code groupCode}·{@code wcCode}·{@code gender}·{@code floorName})도 여기 같이
 * 올라온다. 버튼이 눌릴 때마다 DB 를 조회하지 않기 위함이다 — 사람이 누르는 횟수만큼
 * 쿼리가 나가면 안 된다.</p>
 *
 * <p><b>다만 갱신은 기동 시 한 번뿐이다</b>({@link #reload()} 를 호출하는 곳이
 * {@code @PostConstruct} 밖에 없다). 관리자 화면에서 그룹 코드·{@code wcCode} 를 바꾸면
 * 재기동 전까지 옛 값이 나간다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AmqpPointMappingCache {

    private final AmqpPointMappingRepository amqpPointMappingRepository;

    /** key = uuid + "::" + 필드명 */
    private final Map<String, AmqpPointTarget> byUuidAndField = new ConcurrentHashMap<>();

    /** key = deviceId + "::" + tagName. 현재값에 같이 적을 표시·식별 정보. */
    private final Map<String, PointMeta> metaByDeviceAndTag = new ConcurrentHashMap<>();

    @PostConstruct
    public void reload() {
        reloadTargets();
        reloadMeta();
    }

    private void reloadTargets() {
        List<Object[]> rows = amqpPointMappingRepository.findAmqpPointMappings();

        Map<String, AmqpPointTarget> loaded = new ConcurrentHashMap<>();
        for (Object[] row : rows) {
            String uuid = str(row[0]);
            String field = str(row[1]);
            Long deviceId = row[3] == null ? null : ((Number) row[3]).longValue();
            if (uuid == null || field == null || deviceId == null) {
                continue;
            }

            AmqpPointTarget target = new AmqpPointTarget(
                    deviceId,
                    str(row[4]),   // device_key
                    str(row[2]),   // tag_name
                    str(row[5]),   // group_code  → HQS wcBoothId
                    str(row[6]),   // wc_code     → HQS wcId
                    str(row[7]),   // gender
                    str(row[8]));  // floor_name  → HQS florId

            AmqpPointTarget previous = loaded.put(key(uuid, field), target);
            if (previous != null && !previous.deviceId().equals(deviceId)) {
                // 같은 uuid+필드를 두 장비에 매핑한 설정 오류 — 뒤에 온 것이 이긴다. 데이터 정정 필요.
                log.warn("AMQP 매핑 중복 uuid={} field={} deviceId {} -> {}",
                        uuid, field, previous.deviceId(), deviceId);
            }
        }

        byUuidAndField.clear();
        byUuidAndField.putAll(loaded);
        log.info("AMQP 포인트 매핑 {}건 로드", byUuidAndField.size());
    }

    /**
     * 현재값에 같이 적을 표시·식별 정보를 적재한다.
     *
     * <p>라우팅 매핑과 대상이 다르다 — {@code occ} 같은 파생 포인트도 화면에는 이름이 떠야 하므로
     * {@code ref_point_code} 유무를 가리지 않는다.</p>
     */
    private void reloadMeta() {
        List<Object[]> rows = amqpPointMappingRepository.findPointMeta();

        Map<String, PointMeta> loaded = new ConcurrentHashMap<>();
        for (Object[] row : rows) {
            Long deviceId = row[0] == null ? null : ((Number) row[0]).longValue();
            String tagName = str(row[1]);
            if (deviceId == null || tagName == null) {
                continue;
            }
            loaded.put(metaKey(deviceId, tagName), new PointMeta(
                    row[2] == null ? null : ((Number) row[2]).longValue(), // point_id
                    str(row[3]),   // point_key
                    str(row[4]),   // point_display_name
                    str(row[5]),   // unit
                    str(row[6]),   // point_type
                    str(row[7]),   // ref_device_code
                    str(row[8]))); // ref_point_code
        }

        metaByDeviceAndTag.clear();
        metaByDeviceAndTag.putAll(loaded);
        log.info("AMQP 포인트 메타 {}건 로드", metaByDeviceAndTag.size());
    }

    /**
     * 이 장비의 이 태그에 딸린 표시·식별 정보. 등록이 없으면 {@code null}
     * — 호출부는 값만 기록한다(메타가 없다고 값을 버리면 안 된다).
     */
    public PointMeta resolveMeta(Long deviceId, String tagName) {
        if (deviceId == null || tagName == null || tagName.isBlank()) {
            return null;
        }
        return metaByDeviceAndTag.get(metaKey(deviceId, tagName.trim()));
    }

    private static String metaKey(Long deviceId, String tagName) {
        return deviceId + "::" + tagName;
    }

    /**
     * 이 uuid 의 이 필드를 어느 포인트에 기록할지. 매핑이 없으면 {@code null}
     * — 호출부는 조용히 무시한다(우리가 등록하지 않은 제어기·필드가 계속 들어오기 때문).
     */
    public AmqpPointTarget resolve(String deviceUuid, String field) {
        if (deviceUuid == null || deviceUuid.isBlank() || field == null || field.isBlank()) {
            return null;
        }
        return byUuidAndField.get(key(deviceUuid.trim(), field.trim()));
    }

    public int size() {
        return byUuidAndField.size();
    }

    private static String key(String uuid, String field) {
        return uuid + "::" + field;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
