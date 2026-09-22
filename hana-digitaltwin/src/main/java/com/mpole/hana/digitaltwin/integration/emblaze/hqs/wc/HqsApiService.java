package com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc;

import com.mpole.hana.digitaltwin.integration.emblaze.amqp.AmqpPointMappingCache;
import com.mpole.hana.digitaltwin.integration.emblaze.amqp.EmblazeTrace;
import com.mpole.hana.digitaltwin.integration.emblaze.amqp.AmqpPointTarget;
import com.mpole.hana.digitaltwin.integration.emblaze.config.HqsProperties;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto.HqsBoothStatusData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto.HqsBoothStatusRequest;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto.HqsWcBoothItem;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto.HqsWcItem;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 화장실 부스 재실 상태 수집·조회.
 *
 * <h3>수집 경로</h3>
 * <pre>
 *   RabbitMQ space(device_uuid, pir_accumulation)
 *     → EmblazeMonitoringListeners.onSpace
 *         ① EmblazeStatusRouter    → pir_accumulation 원본을 OCC 장비에 기록
 *         ② saveBoothStatusData    → 리스너 스레드 즉시 반환, 단일 스레드 executor 로 위임
 *              → applyPir          → PIR 이 증가했으면 occ="1" 기록(= 마지막 움직임 시각 갱신)
 * </pre>
 *
 * <h3>재실 판정은 조회 시점에 계산한다</h3>
 * <p>재실은 결국 <b>"최근에 움직임이 있었나"</b> 하나이고, 그건 타임스탬프 하나의 함수다.
 * 그래서 {@code occ} 를 0/1 로 굳혀두지 않는다 — 움직일 때마다 {@code "1"} 을 덮어써
 * <b>{@code updateDatetime} 을 앞으로 밀고</b>, 퇴실은
 * {@code DeviceWcBoothRepository#findBoothOccupancy} 가
 * {@code now - updateDatetime > vacancyTimeoutMs} 로 판정한다.</p>
 *
 * <p>따라서 {@code "0"} 을 쓰는 경로가 없고, 퇴실을 감시하는 스케줄러도 없다.
 * 예전 {@code sweepVacancy()} 는 재실 상태를 메모리에 들고 있어 <b>재기동하면 잊었고</b>,
 * 그 결과 재실로 찍힌 부스가 영원히 사용중으로 남았다(복구 경로 없음). 지금은 상태의 정본이
 * DB 의 타임스탬프 하나뿐이라 그 문제가 없다.</p>
 *
 * <p><b>동시성</b>: 리스너 경로가 단일 스레드 {@code boothSaveExecutor} 를 거치므로
 * 부스별 처리가 직렬화된다(별도 lock 불필요).</p>
 *
 * <p><b>재기동</b>: 메모리에 남는 건 PIR 비교용 기준값뿐이라 잃어도 정확성에 영향이 없다.
 * 다음 메시지가 기준값이 되고 그다음 증가부터 다시 잡힌다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HqsApiService {

    /**
     * 재실센서 장비를 찾을 때 쓰는 벤더 필드명.
     * <p>uuid 하나에 장비가 3대 걸리므로 uuid 만으로는 특정할 수 없다 —
     * space 큐의 이 필드가 재실센서(OCC) 장비를 가리킨다.</p>
     */
    private static final String FIELD_PIR = "pir_accumulation";

    private final HqsProperties hqsProperties;
    private final DeviceWcBoothRepository deviceWcBoothRepository;

    /** (device_uuid, 벤더 필드명) → 우리 포인트 */
    private final AmqpPointMappingCache amqpPointMappingCache;

    /** tbl_device_current_value.points 기록 */
    private final BoothCurrentValueWriter boothCurrentValueWriter;

    /**
     * device_uuid → 마지막 PIR 누적값. 증감 비교에만 쓴다.
     *
     * <p>재기동으로 사라져도 <b>정확성에 영향이 없다</b> — 다음 메시지가 기준값이 되고, 그다음
     * 증가부터 다시 잡힌다. 재실 상태 자체는 DB 의 {@code occ} 타임스탬프가 들고 있다.</p>
     */
    private final Map<String, Long> lastPirByDevice = new ConcurrentHashMap<>();

    // 큐에 쌓아두고 single thread 에서 차례로 처리(리스너 스레드 블로킹 방지)
    private final ExecutorService boothSaveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hqs-booth-save");
        t.setDaemon(true); // 메인 스레드 종료 시 데몬 스레드 자동 종료
        return t;
    });

    /* ════════════════════════════════════════════════════════════════
     * 수집 (RabbitMQ → DB)
     * ════════════════════════════════════════════════════════════════ */

    /**
     * Rabbit space 메시지 수신 진입점. 리스너 스레드를 막지 않도록 전용 스레드로 위임한다.
     *
     * <p>센서 타임스탬프는 받지 않는다 — 퇴실 판정이 시간 비교라 <b>우리 시계를 기준</b>으로
     * 기록해야 한다({@link #applyPir} 참고). 벤더 타임스탬프는 {@code pir_accumulation} 원본에
     * 이미 그대로 남는다.</p>
     *
     * @param deviceUuid   emblaze 조명제어기 uuid
     * @param accumulation PIR 누적값
     */
    public void saveBoothStatusData(String deviceUuid, long accumulation, EmblazeTrace trace) {
        if (!StringUtils.hasText(deviceUuid)) {
            trace.fail("occ", "deviceUuid 비어 있음");
            trace.flush(log);
            return;
        }

        try {
            // 재실 판정이 끝난 뒤 trace 를 내보낸다 — 판정 결과까지 한 줄에 담기 위함이다.
            boothSaveExecutor.execute(() -> {
                try {
                    applyPir(deviceUuid, accumulation, trace);
                } finally {
                    trace.flush(log);
                }
            });
        } catch (RejectedExecutionException e) {
            trace.fail("occ", "executor 종료됨");
            trace.flush(log);
        }
    }

    /**
     * PIR 누적값이 <b>증가했을 때만</b> {@code occ="1"} 을 기록한다.
     *
     * <p>같은 값을 계속 덮어쓰는 것처럼 보이지만 의미는 <b>타임스탬프 갱신</b>이다 —
     * 그 시각이 곧 "마지막 움직임"이고, 퇴실 판정은 조회 쿼리가 그 시각으로 계산한다.
     * 그래서 여기서 {@code "0"} 을 쓸 일이 없다(시간이 지나면 자동으로 0 으로 읽힌다).</p>
     *
     * <p><b>시각은 서버 시각을 쓴다.</b> 퇴실 판정이 {@code now() - updateDatetime} 비교라,
     * 여기에 벤더 타임스탬프를 넣으면 벤더 장비의 시계가 틀어진 만큼 판정이 통째로 어긋난다
     * (앞서면 영원히 재실, 뒤처지면 즉시 퇴실). 원본 이벤트 시각은 {@code pir_accumulation} 에 남는다.</p>
     *
     * <p>단일 스레드 executor 위에서만 호출된다.</p>
     */
    private void applyPir(String deviceUuid, long accumulation, EmblazeTrace trace) {
        AmqpPointTarget target = amqpPointMappingCache.resolve(deviceUuid, FIELD_PIR);
        if (target == null) {
            trace.fail("occ", "미등록 uuid — tbl_device_point.ref_device_code 없음");
            return;
        }

        Long last = lastPirByDevice.put(deviceUuid, accumulation);
        if (last == null) {
            // 첫 샘플은 기준값만 잡는다(증감을 알 수 없음)
            trace.ok("occ", "기준값설정(" + target.zoneCode() + ")");
            return;
        }
        if (accumulation <= last) {
            // 움직임 없음(동일) 또는 센서 리셋(감소) — 기록하지 않는다.
            // 마지막 움직임 시각이 그대로 남아 시간이 지나면 조회에서 0 으로 읽힌다.
            trace.ok("occ", "변화없음(" + last + "→" + accumulation + ")");
            return;
        }

        // 움직임 감지 → 재실. 값(1)은 그대로고 updateDatetime 만 앞으로 간다.
        boothCurrentValueWriter.writeOccupancy(
                target.deviceId(), target.deviceKey(),
                BoothCurrentValueWriter.OCC_OCCUPIED, OffsetDateTime.now());
        trace.ok("occ", "재실기록(" + target.zoneCode() + " " + last + "→" + accumulation + ")");
    }

    // ────────────────────────────────────────────────────────────────
    // [보존] 퇴실 감시 스케줄러 — 조회 시점 계산으로 대체되어 제거했다.
    //
    //  왜 없앴나
    //    · 재실 상태를 메모리(lastOccByDevice/lastMotionByDevice)에 들고 있어서
    //      서버를 재기동하면 "누가 재실 중이었는지"를 잊었다. 재실로 찍힌 부스는
    //      영원히 사용중으로 남고 복구 경로가 없었다.
    //    · sweep 주기(5초)만큼 판정이 늦었다.
    //    · 같은 판정 로직이 메모리와 DB 두 곳에 흩어져 있었다.
    //
    //  지금은 occ 포인트의 updateDatetime 하나가 정본이고, 퇴실은 조회 쿼리가 계산한다.
    // ────────────────────────────────────────────────────────────────
    // /** 이전 값과 다를 때만 DB 를 친다. */
    // private void transitOccupancy(String deviceUuid, AmqpPointTarget target,
    //                               String value, OffsetDateTime eventAt) {
    //     String previous = lastOccByDevice.get(deviceUuid);
    //     if (value.equals(previous)) {
    //         return; // 상태 변화 없음 — write 생략
    //     }
    //     lastOccByDevice.put(deviceUuid, value);
    //     boothCurrentValueWriter.writeOccupancy(target.deviceId(), target.deviceKey(), value, eventAt);
    // }
    //
    // @Scheduled(fixedDelayString = "${hdt.integration.hqs.sweep-interval-ms:5000}")
    // public void sweepVacancy() {
    //     long timeoutMs = hqsProperties.getVacancyTimeoutMs();
    //     Instant now = Instant.now();
    //
    //     for (Map.Entry<String, String> entry : lastOccByDevice.entrySet()) {
    //         if (!BoothCurrentValueWriter.OCC_OCCUPIED.equals(entry.getValue())) {
    //             continue;
    //         }
    //         String deviceUuid = entry.getKey();
    //         Instant lastMotion = lastMotionByDevice.get(deviceUuid);
    //         if (lastMotion != null && Duration.between(lastMotion, now).toMillis() <= timeoutMs) {
    //             continue; // 아직 타임아웃 전
    //         }
    //
    //         AmqpPointTarget target = amqpPointMappingCache.resolve(deviceUuid, FIELD_PIR);
    //         if (target == null) {
    //             continue;
    //         }
    //
    //         log.info("퇴실 가정(sweep) device={} deviceId={} lastMotion={}",
    //                 deviceUuid, target.deviceId(), lastMotion);
    //         lastMotionByDevice.remove(deviceUuid);
    //         try {
    //             boothSaveExecutor.execute(() ->
    //                     transitOccupancy(deviceUuid, target, BoothCurrentValueWriter.OCC_VACANT, OffsetDateTime.now()));
    //         } catch (RejectedExecutionException e) {
    //             log.warn("sweepVacancy: executor 종료됨 device={}", deviceUuid);
    //         }
    //     }
    // }

    /* ════════════════════════════════════════════════════════════════
     * 조회 (HQS → DT)
     * ════════════════════════════════════════════════════════════════ */

    /**
     * 화장실 부스 재실 현황 조회. 화장실(부모 구역) 단위로 묶어 내려준다.
     *
     * <p>SQL 이 {@code ORDER BY wc_id, booth_id} 로 정렬해 주므로 {@link LinkedHashMap} 하나로
     * 그룹핑하면 <b>화장실 순서·부스 순서가 그대로 유지</b>된다.</p>
     *
     * <p>{@code boothStatus} 는 절대 null 이 되지 않는다 — 값이 없거나
     * {@code occupancy-stale-seconds} 보다 오래된 값은 SQL 에서 이미 {@code '0'} 으로 내려온다.</p>
     */
    public HqsBoothStatusData getBoothStatusData(HqsBoothStatusRequest request) {
        List<Object[]> rows = deviceWcBoothRepository.findBoothOccupancy(
                blankToNull(request.florId()),
                blankToNull(request.wcId()),
                blankToNull(request.gender()),
                (int) (hqsProperties.getVacancyTimeoutMs() / 1000L));

        Map<String, HqsWcItem> byWcId = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String wcId = str(row[0]);
            String gender = str(row[2]);
            String boothId = str(row[3]);

            HqsWcItem wc = byWcId.computeIfAbsent(wcId, id -> HqsWcItem.builder()
                    .wcId(id)
                    .gender(gender)
                    .wcBoothList(new ArrayList<>())
                    .build());

            wc.getWcBoothList().add(HqsWcBoothItem.builder()
                    .wcBoothId(boothId)
                    .boothStatus(toBoothStatus(str(row[5])))
                    .build());
        }

        return HqsBoothStatusData.builder()
                .bldgId(request.bldgId())
                .florId(request.florId())
                .wcList(new ArrayList<>(byWcId.values()))
                .build();
    }

    /**
     * {@code points[].valueRaw} → HQS 코드. {@code "1"} 만 사용중이고 나머지는 전부 0 이다
     * — 값이 아직 없는 부스(센서 메시지 미수신)도 0(비어있음)으로 나간다.
     */
    private static int toBoothStatus(String raw) {
        return BoothCurrentValueWriter.OCC_OCCUPIED.equals(raw) ? 1 : 0;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    @PreDestroy
    void shutdownExecutors() {
        boothSaveExecutor.shutdownNow();
    }
}
