package com.mpole.hana.digitaltwin.dashboard.situation.service;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.buildingLighting.domain.CommStatus;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingReading;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingState;
import com.mpole.hana.digitaltwin.buildingLighting.service.BuildingLightingReader;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.*;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.event.dto.AlarmHistoryStats;
import com.mpole.hana.digitaltwin.event.service.EventService;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceCurrentValueRepository;
import com.mpole.hana.digitaltwin.energy.dto.EnergySummary;
import com.mpole.hana.digitaltwin.energy.service.EnergySummaryService;
import com.mpole.hana.digitaltwin.parking.dto.ParkingSummary;
import com.mpole.hana.digitaltwin.parking.entity.ParkingSession;
import com.mpole.hana.digitaltwin.parking.repository.ParkingSessionRepository;
import com.mpole.hana.digitaltwin.parking.service.ParkingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 통합현황 데이터 제공.
 * <p>상단 KPI 일부(신재생 생산·기준층 실내환경)는 실DB 집계, 나머지는 프론트 mock과 동일한 스텁.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardSituationService {

    private static final Pattern LEADING_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String LIGHT_MODE_GENERAL = "일반 조명";
    private static final String LIGHT_MODE_WC = "화장실 조명";
    private static final String LIGHT_MODE_PARKING = "지하주차장 조명";
    private static final List<String> LIGHT_MODES = List.of(
            LIGHT_MODE_GENERAL, LIGHT_MODE_WC, LIGHT_MODE_PARKING);

    private static final Pattern PARKING_FLOOR = Pattern.compile("(B\\d+)", Pattern.CASE_INSENSITIVE);
    private static final List<String> PARKING_FLOORS = List.of(
            "B1", "B2", "B3", "B4", "B5", "B6", "B7");
    private static final int PARKING_CAPACITY_PER_FLOOR = 40;

    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceCurrentValueRepository DeviceCurrentValueRepository;
    private final EnergySummaryService energySummaryService;
    private final EventService eventService;
    private final BuildingLightingReader buildingLightingReader;
    private final ParkingQueryService parkingQueryService;
    private final ParkingSessionRepository parkingSessionRepository;

    /**
     * 금일 전력·가스 — 원격검침/누적 포인트 {@link EnergySummaryService} (KST 0시 기준 스냅샷).
     */
    @Transactional(readOnly = true)
    public SituationDailyUsageKpiResponse getDailyUsageKpi() {
        List<Device> elecMeters = deviceRepository.fetchElectricMeterDevices();
        List<Device> gasMeters = deviceRepository.fetchGasMeterDevices();

        List<Long> elecIds = elecMeters.stream().map(Device::getDeviceId).toList();
        List<Long> gasIds = gasMeters.stream().map(Device::getDeviceId).toList();

        List<EnergySummary> elecSummaries = elecIds.isEmpty()
                ? List.of()
                : energySummaryService.summary(elecIds, List.of("kWh", "total_kWh", "KWH", "total-kWh"));
        List<EnergySummary> gasSummaries = gasIds.isEmpty()
                ? List.of()
                : energySummaryService.summary(gasIds, List.of("G", "total-g"));

        double todayPowerKwh = sumTodayUsage(elecSummaries);
        double todayGasM3 = sumTodayUsage(gasSummaries);

        boolean powerLinked = hasTodayUsage(elecSummaries);
        boolean gasLinked = hasTodayUsage(gasSummaries);

        return SituationDailyUsageKpiResponse.builder()
                .todayPowerMwh(powerLinked ? roundUsage3(todayPowerKwh / 1000.0) : null)
                .todayGasNm3(gasLinked ? roundUsage3(todayGasM3) : null)
                .powerLinked(powerLinked)
                .gasLinked(gasLinked)
                .powerMeterCount(elecMeters.size())
                .gasMeterCount(gasMeters.size())
                .powerPointCount(elecSummaries.size())
                .gasPointCount(gasSummaries.size())
                .powerSource("EnergySummary RM_KWH/KWH 누적 포인트 금일(kWh→MWh)")
                .gasSource("EnergySummary RM_GM total-g/G 누적 포인트 금일(Nm³)")
                .build();
    }

    private static double sumTodayUsage(List<EnergySummary> summaries) {
        return summaries.stream()
                .map(EnergySummary::today)
                .filter(Objects::nonNull)
                .map(EnergySummary.Period::value)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static boolean hasTodayUsage(List<EnergySummary> summaries) {
        return summaries.stream()
                .anyMatch(s -> s.today() != null && s.today().value() != null);
    }

    private static double roundUsage3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /**
     * 소방방재 — 화재감지기(DET) 등록 대수 + 금일 FDP 계열 이벤트(실DB, 모의 제외).
     */
    @Transactional(readOnly = true)
    public SituationFireKpiResponse getFireKpi() {
        long detectorCount = deviceRepository.countFireDetectorDevices();
        int detCount = detectorCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) detectorCount;

        OffsetDateTime dayStart = LocalDate.now(KST).atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime now = OffsetDateTime.now(KST);

        long alarmTotal = 0;
        long alarmOpen = 0;
        Optional<Long> fdpCategoryId = resolveFireRootCategoryId();
        if (fdpCategoryId.isPresent()) {
            AlarmHistoryStats stats = eventService.getAlarmHistoryStats(
                    null, null, null, null, List.of(fdpCategoryId.get()), false, null, dayStart, now);
            alarmTotal = stats.total();
            alarmOpen = stats.open();
        }

        List<SituationFireDeviceResponse> devices = List.of(
                SituationFireDeviceResponse.builder()
                        .id("det")
                        .label("화재감지기")
                        .count(detCount)
                        .build()
        );

        boolean linked = detCount > 0;
        return SituationFireKpiResponse.builder()
                .alarmCount((int) Math.min(alarmTotal, Integer.MAX_VALUE))
                .sensorCount((int) Math.min(alarmOpen, Integer.MAX_VALUE))
                .devices(devices)
                .linked(linked)
                .source("tbl_device(DET) + tbl_event(FDP·금일·simulated=false)")
                .build();
    }

    private Optional<Long> resolveFireRootCategoryId() {
        return deviceCategoryRepository.findAllWithParent().stream()
                .filter(c -> "FDP".equalsIgnoreCase(c.getCategoryCode()))
                .map(DeviceCategory::getCategoryId)
                .findFirst();
    }

    /**
     * 조명제어 — 건물조명(LCS) 장비를 구역 유형(일반/화장실/지하주차)별 ON·OFF·알람 집계.
     */
    @Transactional(readOnly = true)
    public SituationLightingKpiResponse getLightingKpi() {
        List<Long> categoryIds = buildingLightingReader.resolveCategoryIds();
        List<LightingReading> readings = buildingLightingReader.readAll();
        Map<String, List<LightingReading>> byMode = new LinkedHashMap<>();
        for (String mode : LIGHT_MODES) {
            byMode.put(mode, new ArrayList<>());
        }
        for (LightingReading reading : readings) {
            byMode.get(resolveLightingSituationMode(reading)).add(reading);
        }

        Map<String, SituationLightingModeCountsResponse> countsByMode = new LinkedHashMap<>();
        for (String mode : LIGHT_MODES) {
            countsByMode.put(mode, aggregateLightingModeCounts(byMode.get(mode)));
        }

        boolean linked = !categoryIds.isEmpty();
        return SituationLightingKpiResponse.builder()
                .modes(LIGHT_MODES)
                .byMode(countsByMode)
                .linked(linked)
                .source("건물조명(LCS)·tbl_device_current_value·구역/장비명 분류")
                .build();
    }

    private static String resolveLightingSituationMode(LightingReading reading) {
        String deviceName = reading.deviceName() != null ? reading.deviceName().toUpperCase() : "";
        String zoneName = reading.zoneName() != null ? reading.zoneName() : "";
        String floorName = reading.floorName() != null ? reading.floorName() : "";

        if (deviceName.contains("-WC-") || deviceName.contains("_WC")
                || deviceName.contains("RL_WC")
                || zoneName.contains("화장실")
                || zoneName.toUpperCase().contains("WC")) {
            return LIGHT_MODE_WC;
        }
        if (zoneName.contains("주차") || floorName.contains("주차")
                || deviceName.contains("-PK-") || deviceName.contains("주차")
                || deviceName.contains("PARKING")) {
            return LIGHT_MODE_PARKING;
        }
        return LIGHT_MODE_GENERAL;
    }

    private static SituationLightingModeCountsResponse aggregateLightingModeCounts(List<LightingReading> readings) {
        int on = 0;
        int off = 0;
        int alarm = 0;
        for (LightingReading r : readings) {
            if (r.commStatus() == CommStatus.ALARM) {
                alarm++;
            }
            if (r.state() == LightingState.ON) {
                on++;
            } else {
                off++;
            }
        }
        return SituationLightingModeCountsResponse.builder().on(on).off(off).alarm(alarm).build();
    }

    /**
     * 주차관리 — gateway 적재 tbl_parking_in/out 기준 현재 주차·금일 입출차·층별 점유(B1~B7, 定원 40면/층).
     */
    @Transactional(readOnly = true)
    public SituationParkingKpiResponse getParkingKpi() {
        LocalDate today = LocalDate.now(KST);
        ParkingSummary todaySummary = parkingQueryService.summary(today, null, null);
        long parked = parkingSessionRepository.countParked();

        Map<String, Integer> occupiedByFloor = new LinkedHashMap<>();
        for (String floor : PARKING_FLOORS) {
            occupiedByFloor.put(floor, 0);
        }
        for (ParkingSession session : parkingSessionRepository.findParkedSessions()) {
            String floor = parseParkingFloor(session.getInDevice());
            if (floor != null && occupiedByFloor.containsKey(floor)) {
                occupiedByFloor.merge(floor, 1, Integer::sum);
            }
        }

        List<SituationParkingLotKpiResponse> lots = PARKING_FLOORS.stream()
                .map(floor -> SituationParkingLotKpiResponse.builder()
                        .floor(floor)
                        .occupied(occupiedByFloor.get(floor))
                        .total(PARKING_CAPACITY_PER_FLOOR)
                        .build())
                .toList();

        int totalCapacity = PARKING_FLOORS.size() * PARKING_CAPACITY_PER_FLOOR;
        int occupied = parked > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parked;
        boolean linked = parkingSessionRepository.count() > 0;

        return SituationParkingKpiResponse.builder()
                .occupied(occupied)
                .total(totalCapacity)
                .lots(lots)
                .todayInCount(todaySummary.inCount())
                .todayOutCount(todaySummary.outCount())
                .linked(linked)
                .source("tbl_parking_in/out(gateway 5분 동기) + 입차장비명 B층 파싱 + 定원 40면/층(B1~B7)")
                .build();
    }

    private static String parseParkingFloor(String inDevice) {
        if (inDevice == null || inDevice.isBlank()) {
            return null;
        }
        Matcher matcher = PARKING_FLOOR.matcher(inDevice);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<SituationTopKpiItemResponse> getTopKpis() {
        return stubBoard().topKpis();
    }

    @Transactional(readOnly = true)
    public SituationBoardResponse getBoard() {
        return stubBoard();
    }

    /** 신재생 에너지 생산(누적) — 태양광 ceg + 지열 chg 합산 및 설비별 내역. */
    @Transactional(readOnly = true)
    public SituationRenewKpiResponse getRenewKpi() {
        List<Device> devices = deviceRepository.fetchRenewableDevices();
        Map<Long, DeviceCurrentValue> currentValueMap = loadSnapshots(devices);

        double solar = 0;
        double geo = 0;
        int linkedCount = 0;
        List<SituationRenewDeviceResponse> deviceItems = new ArrayList<>();
        for (Device device : devices) {
            DeviceCurrentValue currentValue = currentValueMap.get(device.getDeviceId());
            if (currentValue == null || currentValue.getPoints() == null || currentValue.getPoints().isEmpty()) {
                continue;
            }
            double deviceKwh = 0;
            String type = null;
            for (XnPointData point : currentValue.getPoints()) {
                if (point == null || point.tagName() == null) {
                    continue;
                }
                String tag = point.tagName().trim();
                if ("ceg".equalsIgnoreCase(tag)) {
                    double v = parseDouble(point.valueRaw());
                    solar += v;
                    deviceKwh += v;
                    type = "solar";
                } else if ("chg".equalsIgnoreCase(tag)) {
                    double v = parseDouble(point.valueRaw());
                    geo += v;
                    deviceKwh += v;
                    type = "geo";
                }
            }
            if (type != null) {
                linkedCount++;
                deviceItems.add(SituationRenewDeviceResponse.builder()
                        .name(device.getDeviceName() != null ? device.getDeviceName() : device.getDeviceKey())
                        .type(type)
                        .kwh(round1(deviceKwh))
                        .build());
            }
        }

        return SituationRenewKpiResponse.builder()
                .productionKwh(round1(solar + geo))
                .solarKwh(round1(solar))
                .geoKwh(round1(geo))
                .deviceCount(linkedCount)
                .devices(deviceItems)
                .linked(linkedCount > 0)
                .source("tbl_device + tbl_device_current_value (RE_PV ceg + RE_GS chg)")
                .build();
    }

    /** 전력감시 패널 — 한전 인입(SHV) 순시 계측값. */
    @Transactional(readOnly = true)
    public SituationPowerKpiResponse getPowerMonitorKpi() {
        List<Device> devices = deviceRepository.fetchMainIncomerDevices();
        Map<Long, DeviceCurrentValue> currentValueMap = loadSnapshots(devices);

        for (Device device : devices) {
            DeviceCurrentValue currentValue = currentValueMap.get(device.getDeviceId());
            if (currentValue == null || currentValue.getPoints() == null || currentValue.getPoints().isEmpty()) {
                continue;
            }
            Map<String, String> byTag = new LinkedHashMap<>();
            for (XnPointData point : currentValue.getPoints()) {
                if (point != null && point.tagName() != null) {
                    byTag.putIfAbsent(point.tagName().trim(), point.valueRaw());
                }
            }
            return SituationPowerKpiResponse.builder()
                    .kw(numberOrNull(byTag.get("total-kW")))
                    .voltage(numberOrNull(byTag.get("avg-V")))
                    .current(numberOrNull(byTag.get("avg-A")))
                    .frequency(numberOrNull(byTag.get("HZ")))
                    .powerFactor(numberOrNull(byTag.get("PF")))
                    .peak(numberOrNull(byTag.get("peak-w")))
                    .cumulativeKwh(numberOrNull(byTag.get("kWh")))
                    .linked(true)
                    .updatedAt(currentValue.getUpdatedAt() != null ? currentValue.getUpdatedAt().toString() : null)
                    .source("tbl_device + tbl_device_current_value (PS_PRD_SHV)")
                    .build();
        }

        return SituationPowerKpiResponse.builder()
                .linked(false)
                .source("PS_PRD_SHV 스냅샷 없음")
                .build();
    }

    /** 기준층 실내 환경 — 항온항습기 temp/humi 평균. */
    @Transactional(readOnly = true)
    public SituationIndoorEnvKpiResponse getIndoorEnvKpi() {
        List<Device> devices = deviceRepository.fetchIndoorEnvDevices();
        Map<Long, DeviceCurrentValue> currentValueMap = loadSnapshots(devices);

        double tempSum = 0;
        double humiSum = 0;
        int tempCount = 0;
        int humiCount = 0;
        for (Device device : devices) {
            DeviceCurrentValue currentValue = currentValueMap.get(device.getDeviceId());
            if (currentValue == null || currentValue.getPoints() == null) {
                continue;
            }
            for (XnPointData point : currentValue.getPoints()) {
                if (point == null || point.tagName() == null) {
                    continue;
                }
                String tag = point.tagName().trim();
                if ("temp".equalsIgnoreCase(tag)) {
                    tempSum += parseDouble(point.valueRaw());
                    tempCount++;
                } else if ("humi".equalsIgnoreCase(tag)) {
                    humiSum += parseDouble(point.valueRaw());
                    humiCount++;
                }
            }
        }

        Double temperature = tempCount > 0 ? round1(tempSum / tempCount) : null;
        Double humidity = humiCount > 0 ? round1(humiSum / humiCount) : null;
        return SituationIndoorEnvKpiResponse.builder()
                .temperature(temperature)
                .humidity(humidity)
                .sampleCount(tempCount + humiCount)
                .linked(tempCount > 0 || humiCount > 0)
                .source("tbl_device + tbl_device_current_value (HSE_CLG_CHT temp/humi)")
                .build();
    }

    private Map<Long, DeviceCurrentValue> loadSnapshots(List<Device> devices) {
        Set<Long> ids = devices.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return DeviceCurrentValueRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));
    }

    private static double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String s = raw.trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            Matcher matcher = LEADING_NUMBER.matcher(s);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group());
                } catch (NumberFormatException ignored2) {
                    return 0;
                }
            }
            return 0;
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static Double numberOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            Matcher matcher = LEADING_NUMBER.matcher(s);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group());
                } catch (NumberFormatException ignored2) {
                    return null;
                }
            }
            return null;
        }
    }

    private static SituationBoardResponse stubBoard() {
        return SituationBoardResponse.builder()
                .topKpis(List.of(
                        SituationTopKpiItemResponse.builder()
                                .id("alarm").label("알람 발생 건수").value("2").unit("건").tone("alarm").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("gas").label("금일 가스 사용량").value("1,240").unit("Nm³").tone("gas").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("power").label("금일 전력 사용량").value("42.6").unit("MWh").tone("power").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("renew").label("신재생 에너지 생산량").value("3.8").unit("MWh").tone("renew").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("heat").label("지역난방 사용량").value("18.2").unit("Gcal").tone("heat").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("equip").label("전체 설비 가동 현황").tone("equip")
                                .run(118).stop(4).fault(3).build(),
                        SituationTopKpiItemResponse.builder()
                                .id("env").label("기준층 실내 환경").tone("env")
                                .temp(23.4).humidity(48.0).build()
                ))
                .mechanicalTabs(List.of("열원설비", "공조환기", "전기설비", "기타"))
                .mechanicalEquipmentByTab(stubMechanicalByTab())
                .powerMonitor(SituationPowerMonitorResponse.builder()
                        .kw(1824).voltage(380).frequency(60).peak(2140).powerFactor(0.94)
                        .dailyMwh(42.6).vsYesterdayPct(4.2).demandOccurTime("12:34")
                        .series(List.of(
                                point("00:00", 820),
                                point("06:00", 1650),
                                point("12:00", 3820),
                                point("18:00", 2910),
                                point("24:00", 940)
                        ))
                        .build())
                .fireStatus(SituationFireStatusResponse.builder()
                        .alarmCount(1).sensorCount(2)
                        .devices(List.of(
                                fireDevice("recv", "화재수신반", 12),
                                fireDevice("det", "감지기", 248),
                                fireDevice("relay", "중계기", 36)
                        ))
                        .build())
                .accessStatus(List.of(
                        access("open", "열림", 3),
                        access("closed", "닫힘", 42),
                        access("forcedOpen", "강제열림", 0),
                        access("forcedClosed", "강제닫힘", 1)
                ))
                .meteringUtilities(List.of(
                        utility("elec", "전기", 42, 1),
                        utility("water", "수도", 18, 0),
                        utility("hot", "온수", 12, 1),
                        utility("gas", "가스", 8, 0),
                        utility("hvac", "냉난방", 24, 1),
                        utility("drain", "영구배수", 6, 0),
                        utility("reuse", "중수/우수", 10, 0),
                        utility("flow", "유량", 8, 0)
                ))
                .lightingStatus(SituationLightingStatusResponse.builder()
                        .modes(List.of("일반 조명", "화장실 조명", "지하주차장 조명"))
                        .byMode(Map.of(
                                "일반 조명", lightingCounts(186, 42, 2),
                                "화장실 조명", lightingCounts(48, 12, 1),
                                "지하주차장 조명", lightingCounts(92, 28, 0)
                        ))
                        .build())
                .parkingStatus(SituationParkingStatusResponse.builder().occupied(142).total(280).build())
                .energyRenew(List.of(
                        renew("02:00", 15, 10),
                        renew("04:00", 15, 10),
                        renew("06:00", 40, 35),
                        renew("08:00", 145, 105),
                        renew("10:00", 175, 100),
                        renew("12:00", 185, 85),
                        renew("14:00", 195, 75),
                        renew("16:00", 175, 100),
                        renew("18:00", 145, 105),
                        renew("20:00", 40, 35),
                        renew("22:00", 15, 10),
                        renew("24:00", 15, 10)
                ))
                .build();
    }

    private static Map<String, List<SituationMechanicalEquipmentResponse>> stubMechanicalByTab() {
        Map<String, List<SituationMechanicalEquipmentResponse>> map = new LinkedHashMap<>();
        map.put("열원설비", List.of(
                equip("abs", "흡수식 냉동기", 12, 2, 1),
                equip("compact", "컴팩트 유닛 설비", 8, 1, 0),
                equip("ehp", "EHP", 14, 0, 1),
                equip("tower", "냉각탑", 6, 1, 0),
                equip("boiler", "보일러", 4, 0, 0),
                equip("hex", "열교환기", 10, 2, 1)
        ));
        map.put("공조환기", List.of(
                equip("ahu", "AHU", 18, 2, 1),
                equip("fcu", "FCU", 42, 5, 2),
                equip("vent", "환기설비", 9, 1, 0),
                equip("pac", "공조기", 7, 0, 1),
                equip("filter", "필터 유닛", 11, 3, 0)
        ));
        map.put("전기설비", List.of(
                equip("ups", "UPS", 6, 0, 1),
                equip("mcc", "MCC", 8, 1, 0),
                equip("panel", "분전반", 24, 2, 1),
                equip("xfmr", "변압기", 4, 0, 0),
                equip("gen", "비상발전기", 2, 1, 0)
        ));
        map.put("기타", List.of(
                equip("wp", "급수펌프", 5, 1, 0),
                equip("dp", "배수펌프", 4, 0, 1),
                equip("sp", "오수펌프", 3, 1, 0),
                equip("gp", "지열순환펌프", 2, 0, 0),
                equip("lift", "승강기", 8, 2, 1)
        ));
        return map;
    }

    private static SituationTimeSeriesPointResponse point(String t, int v) {
        return SituationTimeSeriesPointResponse.builder().t(t).v(v).build();
    }

    private static SituationFireDeviceResponse fireDevice(String id, String label, int count) {
        return SituationFireDeviceResponse.builder().id(id).label(label).count(count).build();
    }

    private static SituationAccessItemResponse access(String key, String label, int value) {
        return SituationAccessItemResponse.builder().key(key).label(label).value(value).build();
    }

    private static SituationMeteringUtilityResponse utility(String id, String label, int measuring, int disconnected) {
        return SituationMeteringUtilityResponse.builder()
                .id(id).label(label).measuring(measuring).disconnected(disconnected).build();
    }

    private static SituationLightingModeCountsResponse lightingCounts(int on, int off, int alarm) {
        return SituationLightingModeCountsResponse.builder().on(on).off(off).alarm(alarm).build();
    }

    private static SituationRenewEnergyPointResponse renew(String t, int solar, int geo) {
        return SituationRenewEnergyPointResponse.builder().t(t).solar(solar).geo(geo).build();
    }

    private static SituationMechanicalEquipmentResponse equip(String id, String name, int run, int stop, int alarm) {
        return SituationMechanicalEquipmentResponse.builder()
                .id(id).name(name).run(run).stop(stop).alarm(alarm).build();
    }
}
