package com.mpole.hana.gateway.ingestion.dbview.view01.snapshot;

import com.mpole.hana.common.constant.PointDataSource;
import com.mpole.hana.common.constant.PointType;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.XnPointData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>장비 스냅샷 보관소</b> — "장비별 포인트 골격"을 메모리에 들고, 뷰 값을 그 칸에 적용한다.
 *
 * <h3>왜 이 구조인가</h3>
 * 기존 경로는 "뷰에서 온 행"이 주인공이라 매 사이클 팬아웃 → groupingBy → 정렬로 번들을 새로 만든다.
 * 그 결과 (a) 델타만 담으면 하류 통짜 REPLACE 로 안 바뀐 포인트가 소실되고,
 * (b) 전량을 담으면 매 사이클 30만 행을 재가공해야 한다.
 * 여기서는 골격을 한 번 만들어 두고 <b>값 칸만 갱신</b>하므로 둘 다 피한다 —
 * 판별은 델타(바뀐 장비만), 발행은 전체(그 장비의 모든 포인트).
 *
 * <h3>인덱스 2개가 같은 PointSlot 을 공유한다</h3>
 * <pre>
 *   signalIndex : "refDeviceCode:refPointCode" → List&lt;PointSlot&gt;   (뷰 행을 어디에 쓸지 찾기 · 1:N 팬아웃)
 *   deviceIndex : deviceId                     → DeviceSnapshot      (발행 단위)
 * </pre>
 * 같은 인스턴스를 가리키므로 signalIndex 로 값을 쓰면 deviceIndex 에서 즉시 보인다.
 *
 * <h3>메타 리프레시</h3>
 * 주기적으로 tbl_device_point 를 다시 읽되, <b>이전 slot 의 값을 pointId 기준으로 이월</b>한다.
 * 이월하지 않으면 리프레시마다 모든 값이 null 로 초기화된다.
 * 새 인덱스는 통째로 만들어 <b>volatile 참조를 한 번에 교체</b>한다(부분 갱신 중 읽기 불일치 방지).
 *
 * <p>기존 {@code InitDevicePointDataService} 는 건드리지 않는다 — 이 클래스는 별도 경로로 추가되며
 * {@code hdt.dbview.view01.snapshot.enabled=true} 일 때만 기동한다.</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "hdt.dbview.view01.snapshot", name = "enabled", havingValue = "true")
public class DeviceSnapshotStore {

    /** point_key 에서 device_key 를 유도할 때 자를 언더스코어 개수(기존 번들 로직과 동일 규칙). */
    private static final int DEVICE_KEY_UNDERSCORE_COUNT = 4;

    private final DatabaseClient postgresDatabaseClient;

    /** 뷰 신호키 → 그 신호를 받는 slot 들(공유 신호는 여러 장비에 걸침). */
    private volatile Map<String, List<PointSlot>> signalIndex = Map.of();
    /** deviceId → 장비 골격. */
    private volatile Map<Long, DeviceSnapshot> deviceIndex = Map.of();

    /** 이번 사이클에 값이 실제로 바뀐 장비들. 여러 스레드가 표시할 수 있어 concurrent set. */
    private final Set<Long> dirtyDeviceIds = ConcurrentHashMap.newKeySet();

    public DeviceSnapshotStore(@Qualifier("postgresDatabaseClient") DatabaseClient postgresDatabaseClient) {
        this.postgresDatabaseClient = postgresDatabaseClient;
    }

    /* ────────────────────────────────────────────────
     * 골격 로드 / 리프레시
     * ──────────────────────────────────────────────── */

    /**
     * tbl_device_point 를 읽어 인덱스를 새로 만들고 참조를 교체한다.
     * 이전 값은 pointId 기준으로 이월된다(멱등 — 여러 번 호출해도 값이 유실되지 않음).
     */
    public Mono<Void> refresh() {
        // pointId : pointSlot
        Map<Long, PointSlot> previousByPointId = snapshotPreviousValues();

        return loadPointRows()
                .doOnNext(rows -> rebuildIndexes(rows, previousByPointId))
                .doOnSuccess(rows -> log.info("[Snapshot] 골격 로드 완료 — 장비 {}대, 포인트 {}개, 신호키 {}개",
                        deviceIndex.size(),
                        deviceIndex.values().stream().mapToInt(d -> d.slots().size()).sum(),
                        signalIndex.size()))
                .then();
    }

    /** 값 이월을 위해 현재 slot 들을 pointId 로 색인해 둔다. */
    private Map<Long, PointSlot> snapshotPreviousValues() {
        Map<Long, PointSlot> previous = new HashMap<>();
        for (DeviceSnapshot snapshot : deviceIndex.values()) {
            for (PointSlot slot : snapshot.slots()) {
                if (slot.pointId() != null) {
                    previous.put(slot.pointId(), slot);
                }
            }
        }
        return previous;
    }

    private Mono<List<PointRow>> loadPointRows() {
        return postgresDatabaseClient.sql("""
                        SELECT point_key, point_id, device_id,
                               ref_device_code, ref_point_code,
                               tag_name, point_type, point_display_name, unit,
                               data_source
                        FROM tbl_device_point
                        """)
                .map((row, meta) -> new PointRow(
                        row.get("point_key", String.class),
                        row.get("point_id", Long.class),
                        row.get("device_id", Long.class),
                        row.get("ref_device_code", String.class),
                        row.get("ref_point_code", String.class),
                        row.get("tag_name", String.class),
                        row.get("point_type", String.class),
                        row.get("point_display_name", String.class),
                        row.get("unit", String.class),
                        // 미지정(null)·미정의 문자열은 null → 하위호환으로 VIEW01 취급된다.
                        PointDataSource.fromCode(row.get("data_source", String.class))))
                .all()
                .collectList();
    }

    /** 새 인덱스를 통째로 구성한 뒤 참조를 교체한다(부분 갱신 금지 — 읽는 쪽이 반쯤 만들어진 상태를 보면 안 됨). */
    private void rebuildIndexes(List<PointRow> rows, Map<Long, PointSlot> previousByPointId) {
        Map<Long, List<PointSlot>> slotsByDevice = new HashMap<>();
        Map<String, List<PointSlot>> newSignalIndex = new HashMap<>();

        for (PointRow row : rows) {
            if (row.deviceId() == null) {
                continue; // 장비에 속하지 않은 포인트는 발행 단위가 없어 스킵
            }
            PointSlot slot = new PointSlot(
                    row.deviceId(), row.pointId(), row.pointKey(),
                    row.refDeviceCode(), row.refPointCode(),
                    row.tagName(), row.pointDisplayName(), row.unit(),
                    parsePointType(row.pointType()),
                    row.dataSource()
            );

            // 이전 값 이월 — 없으면 미수집 상태로 시작
            slot.carryValueFrom(previousByPointId.get(row.pointId()));

            slotsByDevice.computeIfAbsent(row.deviceId(), k -> new ArrayList<>()).add(slot);

            // ref 가 없는 포인트는 뷰에서 값이 오지 않는다(수동 등) — 골격에는 남기고 신호 인덱스에서만 제외.
            // data_source 가 이 파이프라인이 아닌 포인트(AMQP/DERIVED 등)도 제외한다 —
            // ref 코드를 외부 시스템 식별자로 쓰더라도 뷰 신호와 같은 키 공간에 섞이면 안 된다.
            if (row.refDeviceCode() != null && row.refPointCode() != null && slot.isViewFed()) {
                newSignalIndex.computeIfAbsent(signalKey(row.refDeviceCode(), row.refPointCode()),
                        k -> new ArrayList<>()).add(slot);
            }
        }

        Map<Long, DeviceSnapshot> newDeviceIndex = new HashMap<>();
        slotsByDevice.forEach((deviceId, slots) -> newDeviceIndex.put(deviceId,
                new DeviceSnapshot(deviceId, deriveDeviceKey(slots), List.copyOf(slots))));

        // 참조 교체(원자적 publish)
        this.signalIndex = Map.copyOf(newSignalIndex);
        this.deviceIndex = Map.copyOf(newDeviceIndex);
    }

    /* ────────────────────────────────────────────────
     * 값 적용 / 발행
     * ──────────────────────────────────────────────── */

    /**
     * 뷰에서 온 행 하나를 해당 slot 들에 적용한다(1:N 팬아웃 — 공유 신호는 여러 장비에 반영).
     * 값이 실제로 바뀐 장비만 dirty 로 표시한다.
     */
    public void apply(XnPointData raw) {
        if (raw == null || raw.deviceCode() == null || raw.pointCode() == null) return;

        List<PointSlot> slots = signalIndex.get(signalKey(raw.deviceCode(), raw.pointCode()));

        if (slots == null) return; // 매핑되지 않은 신호 — 무시

        for (PointSlot slot : slots) {
            if (slot.applyIfChanged(raw.valueRaw(), raw.alarmYn(), raw.updateDatetime())) {
                dirtyDeviceIds.add(slot.deviceId());
            }
        }
    }

    /**
     * dirty 로 표시된 장비들의 번들을 만들고 표시를 비운다.
     * 번들에는 <b>그 장비의 전체 포인트</b>가 실린다(값이 없는 포인트 포함).
     */
    public List<DeviceBundle> drainDirtyBundles(OffsetDateTime fallbackTimestamp) {
        if (dirtyDeviceIds.isEmpty()) return List.of();

        // 스냅샷을 뜬 뒤 비운다 — 사이에 새로 들어온 변경은 다음 사이클에 잡힌다.
        List<Long> targets = List.copyOf(dirtyDeviceIds);
        dirtyDeviceIds.removeAll(targets);

        Map<Long, DeviceSnapshot> index = this.deviceIndex;
        List<DeviceBundle> bundles = new ArrayList<>(targets.size());
        for (Long deviceId : targets) {
            DeviceSnapshot snapshot = index.get(deviceId);
            if (snapshot != null) {
                bundles.add(snapshot.toBundle(fallbackTimestamp));
            }
        }
        return bundles;
    }

    /**
     * 기동 직후 1회 전량 발행용 — <b>이 파이프라인이 값을 공급하는 장비만</b> dirty 로 표시한다(하류 초기 동기화).
     *
     * <p>뷰 신호가 하나도 없는 장비(RabbitMQ 로 채우는 부스, 계산값만 있는 통계 장비 등)를 발행하면
     * 값 없는 포인트가 그대로 나가고, 하류가 {@code points} 를 통째로 교체하므로
     * <b>다른 경로가 써 둔 값이 null 로 덮인다.</b> {@code data_source} 로 소유권을 가른다.</p>
     */
    public void markAllDirty() {
        int skipped = 0;
        for (Map.Entry<Long, DeviceSnapshot> entry : deviceIndex.entrySet()) {
            if (entry.getValue().hasViewFedPoint()) {
                dirtyDeviceIds.add(entry.getKey());
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.info("[Snapshot] 전량 발행 제외 {}대 — 뷰 신호가 없는 장비(data_source 가 이 파이프라인이 아님)", skipped);
        }
    }

    public int deviceCount() {
        return deviceIndex.size();
    }

    public int signalKeyCount() {
        return signalIndex.size();
    }

    /* ────────────────────────────────────────────────
     * 내부 유틸
     * ──────────────────────────────────────────────── */

    private static String signalKey(String refDeviceCode, String refPointCode) {
        return refDeviceCode + ":" + refPointCode;
    }

    private static PointType parsePointType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PointType.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            // 알 수 없는 타입 한 건 때문에 골격 전체 로드가 실패하면 안 된다.
            log.warn("[Snapshot] 알 수 없는 point_type '{}' — null 로 처리", raw);
            return null;
        }
    }

    /** point_key 앞부분에서 device_key 를 유도한다(기존 번들 로직과 동일 규칙). */
    private static String deriveDeviceKey(List<PointSlot> slots) {
        for (PointSlot slot : slots) {
            String derived = cutByUnderscore(slot.pointKey(), DEVICE_KEY_UNDERSCORE_COUNT);
            if (derived != null && !derived.isBlank()) {
                return derived;
            }
        }
        return null;
    }

    static String cutByUnderscore(String value, int underscoreCount) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int pos = -1;
        for (int i = 0; i < underscoreCount; i++) {
            pos = value.indexOf('_', pos + 1);
            if (pos == -1) {
                return value;
            }
        }
        return value.substring(0, pos);
    }

    /** tbl_device_point 한 행(로드 전용 내부 타입). */
    private record PointRow(
            String pointKey, Long pointId, Long deviceId,
            String refDeviceCode, String refPointCode,
            String tagName, String pointType, String pointDisplayName, String unit,
            PointDataSource dataSource
    ) {
    }
}
