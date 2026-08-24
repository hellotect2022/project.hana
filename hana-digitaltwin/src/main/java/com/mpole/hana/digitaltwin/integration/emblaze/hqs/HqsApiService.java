package com.mpole.hana.digitaltwin.integration.emblaze.hqs;

import com.mpole.hana.digitaltwin.integration.emblaze.config.HqsProperties;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsBoothStatusData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsBoothStatusRequest;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsWcBoothItem;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsWcItem;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 화장실 부스 재실 mock 상태머신 + 조회.
 * <p>저장소는 원본대로 in-memory mock(남/여 각 4부스 하드코딩). 실 DB 는 후속 Loop.</p>
 * <p><b>재실/퇴실 리팩터</b>: 원본의 per-device {@code vacancyScheduler}+{@code ScheduledFuture}(generation 취소) 를 제거하고,
 * 부스별 {@code lastMotionAt} 기록 + 단일 {@link #sweepVacancy()} @Scheduled 로 퇴실 가정을 판단한다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HqsApiService {

    private static final long MOCK_SAVE_DELAY_MS = 300L;

    private final HqsProperties hqsProperties;

    /** mock 부스 트리 (Rabbit·API·sweep 공유, 동기화 필요) */
    private final Object boothLock = new Object();
    private List<HqsWcItem> masterWcItems = new ArrayList<>();

    private final Map<String, Long> lastPirByDevice = new ConcurrentHashMap<>();
    private final Map<String, String> deviceToBoothId = new ConcurrentHashMap<>();

    /** boothId -> 마지막 재실(motion) 시각. sweep 이 {@code now - lastMotionAt > vacancyTimeoutMs} 로 퇴실 가정을 판단한다. */
    private final Map<String, Instant> lastMotionByBooth = new ConcurrentHashMap<>();

    // 큐에 쌓아두고 single thread 에서 차례로 처리(리스너 스레드 블로킹 방지)
    private final ExecutorService boothSaveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hqs-booth-mock-save");
        t.setDaemon(true); // 메인 스레드 종료 시 데몬 스레드 자동 종료
        return t;
    });

    @PostConstruct
    void initMockWcStructure() {
        synchronized (boothLock) {
            masterWcItems = buildDefaultMockWcList();
        }
    }

    /**
     * Rabbit space 등에서 호출. DB 저장 가정 지연 후, device별 매핑 부스의 재실만 반영.
     * Rabbit 리스너 스레드를 막지 않도록 지연·갱신은 전용 스레드에서 수행한다.
     */
    public void saveBoothStatusData(String deviceUuid, String timestamp, long accumulation) {
        if (!StringUtils.hasText(deviceUuid)) {
            log.warn("saveBoothStatusData: deviceUuid 비어 있음");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(MOCK_SAVE_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("saveBoothStatusData 시뮬레이션 중단");
                return;
            }
            try {
                OffsetDateTime odt = OffsetDateTime.parse(timestamp);
                log.info("saveBoothStatusData 적용 device={} ts={} pir={}", deviceUuid, odt, accumulation);
            } catch (DateTimeParseException e) {
                log.error("saveBoothStatusData: timestamp 파싱 실패 {}", timestamp, e);
                return;
            }
            synchronized (boothLock) {
                applyPirToMockBooth(deviceUuid, accumulation);
            }
        }, boothSaveExecutor);
    }

    /**
     * 화장실 부스 상태 조회. 메모리 mock 스냅샷을 요청 조건으로 필터해 반환한다.
     */
    public HqsBoothStatusData getBoothStatusData(HqsBoothStatusRequest request) {
        synchronized (boothLock) {
            return HqsBoothStatusData.builder()
                    .bldgId(request.bldgId())
                    .florId(request.florId())
                    .wcList(copyFilteredWcList(request))
                    .build();
        }
    }

    private void applyPirToMockBooth(String deviceUuid, long accumulation) {
        // deviceId -> boothId 매핑
        String boothId = deviceToBoothId.computeIfAbsent(deviceUuid, this::pickMockBoothIdForDevice);

        if (!lastPirByDevice.containsKey(deviceUuid)) {
            lastPirByDevice.put(deviceUuid, accumulation);
            log.debug("PIR 첫 샘플 device={} booth={} pir={}", deviceUuid, boothId, accumulation);
            return;
        }

        long last = lastPirByDevice.get(deviceUuid);

        if (accumulation > last) {
            // 움직임 증가 → 재실 + lastMotionAt 갱신
            lastPirByDevice.put(deviceUuid, accumulation);
            setBoothStatusByBoothId(boothId, "1");
            lastMotionByBooth.put(boothId, Instant.now());
            log.info("재실 반영 booth={} device={} {} -> {}", boothId, deviceUuid, last, accumulation);
        } else if (accumulation < last) {
            // 움직임 감소 → 즉시 비재실
            lastPirByDevice.put(deviceUuid, accumulation);
            setBoothStatusByBoothId(boothId, "0");
            lastMotionByBooth.remove(boothId);
            log.info("PIR 감소·비재실 booth={} device={} {} -> {}", boothId, deviceUuid, last, accumulation);
        } else {
            // 동일 → 재실중이면 lastMotionAt 연장(sweep 퇴실 가정 지연)
            if ("1".equals(getBoothStatusByBoothId(boothId))) {
                lastMotionByBooth.put(boothId, Instant.now());
                log.debug("PIR 동일·재실 연장 booth={} device={}", boothId, deviceUuid);
            }
        }
    }

    /**
     * 단일 sweep: 모든 부스를 훑어, 현재 재실("1")이면서 {@code now - lastMotionAt > vacancyTimeoutMs} 인 부스를
     * 퇴실 가정("0")으로 전환한다. 원본의 per-device ScheduledFuture 방식을 대체한다.
     */
    @Scheduled(fixedDelayString = "${hdt.integration.hqs.sweep-interval-ms:5000}")
    public void sweepVacancy() {
        long timeoutMs = hqsProperties.getVacancyTimeoutMs();
        Instant now = Instant.now();
        synchronized (boothLock) {
            for (HqsWcItem wc : masterWcItems) {
                if (wc.getWcBoothList() == null) {
                    continue;
                }
                for (HqsWcBoothItem booth : wc.getWcBoothList()) {
                    if (!"1".equals(booth.getBoothStatus())) {
                        continue;
                    }
                    Instant lastMotion = lastMotionByBooth.get(booth.getWcBoothId());
                    if (lastMotion == null || Duration.between(lastMotion, now).toMillis() > timeoutMs) {
                        booth.setBoothStatus("0");
                        lastMotionByBooth.remove(booth.getWcBoothId());
                        log.info("퇴실 가정(sweep) booth={} lastMotion={}", booth.getWcBoothId(), lastMotion);
                    }
                }
            }
        }
    }

    /** 신규 device는 mock 부스 id 중 하나에 안정적으로 매핑 */
    private String pickMockBoothIdForDevice(String deviceUuid) {
        List<String> allBoothIds = List.of("ccc01", "ccc02", "ccc03", "ccc04", "ddd01", "ddd02", "ddd03", "ddd04");
        return allBoothIds.get(Math.floorMod(deviceUuid.hashCode(), allBoothIds.size()));
    }

    private void setBoothStatusByBoothId(String wcBoothId, String status) {
        HqsWcBoothItem booth = findBoothItem(wcBoothId);
        if (booth != null) {
            booth.setBoothStatus(status);
        } else {
            log.warn("mock 부스 없음 wcBoothId={}", wcBoothId);
        }
    }

    private String getBoothStatusByBoothId(String wcBoothId) {
        HqsWcBoothItem booth = findBoothItem(wcBoothId);
        return booth != null ? booth.getBoothStatus() : "0";
    }

    private HqsWcBoothItem findBoothItem(String wcBoothId) {
        for (HqsWcItem wc : masterWcItems) {
            if (wc.getWcBoothList() == null) {
                continue;
            }
            for (HqsWcBoothItem b : wc.getWcBoothList()) {
                if (wcBoothId.equals(b.getWcBoothId())) {
                    return b;
                }
            }
        }
        return null;
    }

    private List<HqsWcItem> copyFilteredWcList(HqsBoothStatusRequest request) {
        return masterWcItems.stream()
                .filter(wc -> !StringUtils.hasText(request.wcId()) || request.wcId().equals(wc.getWcId()))
                .filter(wc -> !StringUtils.hasText(request.gender()) || request.gender().equalsIgnoreCase(wc.getGender()))
                .map(this::deepCopyWcItem) // lock 하 스냅샷을 별도 객체로 copy 해 응답에 전달
                .toList();
    }

    private HqsWcItem deepCopyWcItem(HqsWcItem src) {
        List<HqsWcBoothItem> booths = src.getWcBoothList() == null ? List.of()
                : src.getWcBoothList().stream()
                .map(b -> HqsWcBoothItem.builder()
                        .wcBoothId(b.getWcBoothId())
                        .boothStatus(b.getBoothStatus())
                        .build())
                .toList();
        return HqsWcItem.builder()
                .wcId(src.getWcId())
                .gender(src.getGender())
                .wcBoothList(new ArrayList<>(booths))
                .build();
    }

    // 초기 mock 객체 생성
    private static List<HqsWcItem> buildDefaultMockWcList() {
        List<HqsWcBoothItem> mBooths = new ArrayList<>();
        for (String id : List.of("ccc01", "ccc02", "ccc03", "ccc04")) {
            mBooths.add(HqsWcBoothItem.builder().wcBoothId(id).boothStatus("0").build());
        }
        HqsWcItem male = HqsWcItem.builder()
                .wcId("ccccc")
                .gender("M")
                .wcBoothList(mBooths)
                .build();

        List<HqsWcBoothItem> fBooths = new ArrayList<>();
        for (String id : List.of("ddd01", "ddd02", "ddd03", "ddd04")) {
            fBooths.add(HqsWcBoothItem.builder().wcBoothId(id).boothStatus("0").build());
        }
        HqsWcItem female = HqsWcItem.builder()
                .wcId("dddd")
                .gender("F")
                .wcBoothList(fBooths)
                .build();

        return new ArrayList<>(List.of(male, female));
    }

    @PreDestroy
    void shutdownExecutors() {
        boothSaveExecutor.shutdownNow();
    }
}
