package com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.integration.emblaze.amqp.AmqpPointMappingCache;
import com.mpole.hana.digitaltwin.integration.emblaze.amqp.EmblazeTrace;
import com.mpole.hana.digitaltwin.integration.emblaze.amqp.AmqpPointTarget;
import com.mpole.hana.digitaltwin.integration.emblaze.amqp.MonitoringQueue;
import com.mpole.hana.digitaltwin.integration.emblaze.config.HqsProperties;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.HqsClient;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsCallResult;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsOutboundResponse;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto.HqsWcButtonRequest;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceCurrentValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 화장실 부스 물리버튼 처리.
 *
 * <pre>
 *   RabbitMQ enocean(device_uuid, switch_number, index, timestamp)
 *     → EmblazeMonitoringListeners.onEnocean
 *         ① EnOceanIndexDeduplicator  중복 수신 제거(같은 index = 같은 이벤트)
 *         ② EmblazeStatusRouter       switch_number·index 원본을 TB 장비에 기록
 *         ③ handleButtonPress
 *              → AmqpPointMappingCache.resolve(uuid, "switch_number") → 어느 부스인지
 *              → HQS 로 wcButton 전송
 *              → 요청 종류별 누적 카운트 +1
 * </pre>
 *
 * <p>누적 카운트는 통계 장비 1대의 {@code DERIVED} 포인트로 관리한다.</p>
 * <ul>
 *   <li>{@link #TAG_INSPECT_CNT} — 좌측 버튼(0·1) 눌릴 때마다 +1</li>
 *   <li>{@link #TAG_CLEAN_CNT} — 우측 버튼(2·3) 눌릴 때마다 +1</li>
 * </ul>
 *
 * <p><b>주의</b>: 카운트의 정본이 {@code tbl_device_current_value.points} 하나뿐이다.
 * 통계 장비의 포인트는 반드시 {@code data_source = 'DERIVED'} 여야 한다 —
 * {@code VIEW01}(또는 미지정)이면 gateway 가 기동 시 발행하면서 값을 null 로 덮고
 * <b>복구할 원본이 없다</b>.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WcButtonService {

    /** 점검요청 누적 건수 포인트 태그 (좌측 버튼) */
    public static final String TAG_INSPECT_CNT = "inspect_req_cnt";
    /** 청소요청 누적 건수 포인트 태그 (우측 버튼) */
    public static final String TAG_CLEAN_CNT = "clean_req_cnt";

    /**
     * 버튼 장비를 찾을 때 쓰는 벤더 필드명.
     * <p>uuid 하나에 장비가 3대 걸리므로 uuid 만으로는 특정할 수 없다 —
     * enocean 큐의 이 필드가 물리버튼(TB) 장비를 가리킨다.</p>
     */
    private static final String FIELD_SWITCH = "switch_number";

    private final HqsProperties hqsProperties;
    private final AmqpPointMappingCache amqpPointMappingCache;
    private final DeviceWcBoothRepository deviceWcBoothRepository;
    private final DeviceCurrentValueRepository deviceCurrentValueRepository;
    private final BoothCurrentValueWriter boothCurrentValueWriter;
    private final HqsClient hqsClient;

    /** 카운트는 읽기-수정-쓰기라 동시 누름에서 유실될 수 있다. 누름 빈도가 낮아 단순 lock 으로 충분. */
    private final Object counterLock = new Object();

    /**
     * 버튼 누름 1건 처리.
     *
     * @param deviceUuid   emblaze 조명제어기 uuid
     * @param switchNumber 눌린 스위치 번호(0·1=점검요청, 2·3=청소요청)
     * @param pressedAt    센서 타임스탬프
     */
    public void handleButtonPress(String deviceUuid, String switchNumber, OffsetDateTime pressedAt,
                                  EmblazeTrace trace) {
        WcButtonType type = WcButtonType.fromSwitchNumber(switchNumber);
        if (type == null) {
            trace.fail("type", "정의되지 않은 switch_number");
            return;
        }
        trace.ok("type", type);

        AmqpPointTarget target = amqpPointMappingCache.resolve(deviceUuid, FIELD_SWITCH);
        if (target == null) {
            trace.fail("map", "미등록 uuid — tbl_device_point.ref_device_code 없음");
            return;
        }
        trace.ok("map", target.zoneCode() + "/" + target.floorName());

        OffsetDateTime at = pressedAt == null ? OffsetDateTime.now() : pressedAt;
        postToHqs(target, type, switchNumber, at, trace);
        increaseCount(type, at, trace);
    }

    /* ════════════════════════════════════════════════════════════════
     * HQS 전송
     * ════════════════════════════════════════════════════════════════ */

    private void postToHqs(AmqpPointTarget target, WcButtonType type,
                           String switchNumber, OffsetDateTime pressedAt, EmblazeTrace trace) {
        try {
            String buildingId = hqsProperties.getBuildingId();

            // 식별자는 boothStatus 응답과 같은 기준을 쓴다 — zone_code (장비를 다시 만들어도 안 바뀐다).
            HqsWcButtonRequest body = HqsWcButtonRequest.builder()
                    .bldgId(buildingId == null ? "" : buildingId)
                    .florId(target.floorName())
                    .wcId(target.parentZoneCode())
                    .gender(genderOf(target.parentZoneCode()))
                    .wcBoothId(target.zoneCode())
                    .buttonType(switchNumber)
                    .timestamp(pressedAt.toString())
                    .build();

            if (!StringUtils.hasText(body.wcId()) || !StringUtils.hasText(body.wcBoothId())
                    || !StringUtils.hasText(body.florId())) {
                // 필수값이 비었으면 HQS 가 IF110(파라미터 오류)을 줄 뿐이다 — 보내기 전에 원인을 남긴다.
                trace.fail("hqs", "필수값 누락(부스 구역 배치 확인) florId=" + body.florId()
                        + " wcId=" + body.wcId() + " wcBoothId=" + body.wcBoothId());
                return;
            }
            if (!StringUtils.hasText(buildingId)) {
                trace.ok("bldgId", "미설정(hdt.integration.hqs.building-id)");
            }

            HqsCallResult result = hqsClient.postWcButton(body);
            // 버튼 누름은 재전송 큐가 없다 — 전송이 실패해도 누적 카운트는 계속 올린다.
            if (result.isSuccess()) {
                trace.ok("hqs", result.describe() + " trx=" + result.trxId());
            } else {
                trace.fail("hqs", result.describe() + " trx=" + result.trxId());
            }
        } catch (Exception e) {
            trace.fail("hqs", e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    /**
     * 화장실 zone_code 에서 성별을 뽑는다 — {@code WC_4F_M01} → {@code M}, {@code WC_4F_W01} → {@code F}.
     * <p>조회 API 의 SQL 과 같은 규칙이다(세 번째 마디의 첫 글자).</p>
     */
    private static String genderOf(String parentZoneCode) {
        if (parentZoneCode == null) {
            return null;
        }
        String[] parts = parentZoneCode.split("_");
        if (parts.length < 3 || parts[2].isEmpty()) {
            return null;
        }
        return parts[2].charAt(0) == 'M' ? "M" : "F";
    }

    /* ════════════════════════════════════════════════════════════════
     * 누적 카운트
     * ════════════════════════════════════════════════════════════════ */

    /** 해당 버튼 종류의 누적 카운트를 1 올린다. */
    private void increaseCount(WcButtonType type, OffsetDateTime pressedAt, EmblazeTrace trace) {
        String statsDeviceKey = hqsProperties.getStatsDeviceKey();
        if (!StringUtils.hasText(statsDeviceKey)) {
            trace.skip("cnt", "hdt.integration.hqs.stats-device-key 미설정");
            return;
        }

        Optional<Device> found = deviceWcBoothRepository.findFirstByDeviceKey(statsDeviceKey);
        if (found.isEmpty()) {
            trace.skip("cnt", "통계 장비 없음 device_key=" + statsDeviceKey);
            return;
        }
        Device statsDevice = found.get();
        String tagName = tagOf(type);

        synchronized (counterLock) {
            long next = readCount(statsDevice.getDeviceId(), tagName) + 1L;
            boothCurrentValueWriter.writePoint(
                    statsDevice.getDeviceId(), statsDevice.getDeviceKey(),
                    tagName, String.valueOf(next), pressedAt);
            trace.ok("cnt", next + "(" + type.getLabel() + ")");
        }
    }

    /** 현재 누적값 조회. 행·포인트가 없거나 숫자가 아니면 0 부터 시작한다. */
    private long readCount(Long deviceId, String tagName) {
        List<XnPointData> points = deviceCurrentValueRepository.findById(deviceId)
                .map(DeviceCurrentValue::getPoints)
                .orElse(List.of());
        if (points == null) {
            return 0L;
        }
        return points.stream()
                .filter(p -> tagName.equals(p.tagName()))
                .findFirst()
                .map(XnPointData::valueRaw)
                .map(raw -> {
                    try {
                        return Long.parseLong(raw.trim());
                    } catch (NumberFormatException | NullPointerException e) {
                        log.warn("누적값 파싱 실패 tag={} raw='{}' → 0 부터 재시작", tagName, raw);
                        return 0L;
                    }
                })
                .orElse(0L);
    }

    private static String tagOf(WcButtonType type) {
        return type == WcButtonType.INSPECT ? TAG_INSPECT_CNT : TAG_CLEAN_CNT;
    }
}
