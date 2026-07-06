package com.mpole.hana.digitaltwin.dashboard.mechanical.service;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.dashboard.mechanical.dto.*;
import com.mpole.hana.digitaltwin.dashboard.mechanical.support.MechanicalRunStatusResolver;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardMechanicalService {

    private static final Map<String, String> TYPE_LABELS = Map.ofEntries(
            Map.entry("AHU", "AHU (공조기)"),
            Map.entry("OHU", "OHU (옥외기)"),
            Map.entry("FCU", "FCU"),
            Map.entry("CH", "냉동기 (CH)"),
            Map.entry("ELV", "승강기"),
            Map.entry("EFU", "EFU (배기탈취)"),
            Map.entry("SEF", "SEF (급배기송풍기)"),
            Map.entry("SEIF", "SEIF (급배기유인팬)"),
            Map.entry("CU", "컴팩트 유닛"),
            Map.entry("CHT", "항온항습기"),
            Map.entry("SHV", "특고압반"),
            Map.entry("HV", "고압반"),
            Map.entry("LV", "저압반"),
            Map.entry("GEN", "발전기"),
            Map.entry("OTHER", "기타")
    );

    private static final List<MechanicalTabResponse> TAB_META = List.of(
            tab("hvac", "공조환기"),
            tab("heat", "열원설비"),
            tab("electric", "전기설비"),
            tab("etc", "운송·기타")
    );

    private final DeviceRepository deviceRepository;
    private final DeviceCurrentValueRepository deviceCurrentValueRepository;

    @Transactional(readOnly = true)
    public MechanicalPageSnapshotResponse getPageSnapshot() {
        return MechanicalPageSnapshotResponse.builder()
                .tabs(TAB_META)
                .activeTabId("hvac")
                .stub(false)
                .build();
    }

    @Transactional(readOnly = true)
    public MechanicalSituationPanelResponse getSituationEquipment() {
        List<Device> devices = deviceRepository.fetchMechanicalSituationDevices();
        Map<Long, DeviceCurrentValue> currentValueMap = deviceCurrentValueRepository.findAll().stream()
                .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        Map<String, Map<String, List<MechanicalSituationDeviceResponse>>> grouped = new LinkedHashMap<>();
        for (MechanicalTabResponse tab : TAB_META) {
            grouped.put(tab.id(), new LinkedHashMap<>());
        }

        for (Device device : devices) {
            String deviceKey = device.getDeviceKey();
            String refCode = MechanicalRunStatusResolver.extractRefDeviceCode(deviceKey);
            String mechType = MechanicalRunStatusResolver.inferMechType(deviceKey, refCode);
            String tabId = MechanicalRunStatusResolver.inferTabId(deviceKey, mechType);

            DeviceCurrentValue currentValue = currentValueMap.get(device.getDeviceId());
            List<XnPointData> points = currentValue != null ? currentValue.getPoints() : List.of();
            String status = currentValue == null
                    ? MechanicalRunStatusResolver.STATUS_UNKNOWN
                    : MechanicalRunStatusResolver.resolveDeviceRunStatus(mechType, points);

            MechanicalSituationDeviceResponse item = MechanicalSituationDeviceResponse.builder()
                    .deviceId(device.getDeviceId())
                    .deviceKey(deviceKey)
                    .refDeviceCode(refCode)
                    .status(status)
                    .highlight(MechanicalRunStatusResolver.extractHighlight(points))
                    .updatedAt(formatTimestamp(currentValue != null ? currentValue.getUpdatedAt() : null))
                    .build();

            grouped.computeIfAbsent(tabId, key -> new LinkedHashMap<>())
                    .computeIfAbsent(mechType, key -> new ArrayList<>())
                    .add(item);
        }

        List<MechanicalSituationTabResponse> tabs = TAB_META.stream()
                .map(tab -> {
                    Map<String, List<MechanicalSituationDeviceResponse>> typeMap =
                            grouped.getOrDefault(tab.id(), Map.of());
                    List<MechanicalSituationGroupResponse> groups = typeMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> toGroup(entry.getKey(), entry.getValue()))
                            .toList();
                    return MechanicalSituationTabResponse.builder()
                            .id(tab.id())
                            .label(tab.label())
                            .groups(groups)
                            .build();
                })
                .toList();

        return MechanicalSituationPanelResponse.builder()
                .tabs(tabs)
                .source("tbl_device + tbl_device_current_value")
                .stub(false)
                .build();
    }

    private static final Map<String, String> TAB_DEVICE_PREFIX = Map.of(
            "heat", "HSE",
            "hvac", "HVAC",
            "electric", "PS",
            "other", "ELEC_TE"
    );

    @Transactional(readOnly = true)
    public MechanicalPageBoardResponse getPageBoard(String tabId) {
        String prefix = TAB_DEVICE_PREFIX.get(tabId);
        if (prefix == null) {
            return MechanicalPageBoardResponse.builder()
                    .tabId(tabId)
                    .linked(false)
                    .total(0)
                    .categoryMix(List.of())
                    .runStatus(MechRunStatusResponse.builder().operating(0).stopped(0).na(0).alarm(0).build())
                    .equipBars(List.of())
                    .source("실DB 미확인 (전기설비 device 없음)")
                    .build();
        }

        List<Device> devices = deviceRepository.fetchMechanicalSituationDevices().stream()
                .filter(d -> d.getDeviceKey() != null && d.getDeviceKey().startsWith(prefix))
                .toList();
        Map<Long, DeviceCurrentValue> currentValueMap = deviceCurrentValueRepository.findAll().stream()
                .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        Map<String, int[]> byType = new LinkedHashMap<>();
        int totalRun = 0;
        int totalStop = 0;
        int totalUnknown = 0;

        for (Device device : devices) {
            String deviceKey = device.getDeviceKey();
            String refCode = MechanicalRunStatusResolver.extractRefDeviceCode(deviceKey);
            String mechType = MechanicalRunStatusResolver.inferMechType(deviceKey, refCode);

            DeviceCurrentValue currentValue = currentValueMap.get(device.getDeviceId());
            List<XnPointData> points = currentValue != null ? currentValue.getPoints() : List.of();
            String status = currentValue == null
                    ? MechanicalRunStatusResolver.STATUS_UNKNOWN
                    : MechanicalRunStatusResolver.resolveDeviceRunStatus(mechType, points);

            int[] counts = byType.computeIfAbsent(mechType, k -> new int[3]);
            if (MechanicalRunStatusResolver.STATUS_RUN.equals(status)) {
                counts[0]++;
                totalRun++;
            } else if (MechanicalRunStatusResolver.STATUS_STOP.equals(status)) {
                counts[1]++;
                totalStop++;
            } else {
                counts[2]++;
                totalUnknown++;
            }
        }

        int total = devices.size();
        int denom = Math.max(total, 1);

        List<MechCategoryMixResponse> categoryMix = byType.entrySet().stream()
                .map(e -> {
                    int[] c = e.getValue();
                    int typeTotal = c[0] + c[1] + c[2];
                    return MechCategoryMixResponse.builder()
                            .id(e.getKey().toLowerCase(Locale.ROOT))
                            .name(TYPE_LABELS.getOrDefault(e.getKey(), e.getKey()))
                            .count(typeTotal)
                            .value((int) Math.round((typeTotal * 100.0) / denom))
                            .build();
                })
                .sorted(Comparator.comparingInt(MechCategoryMixResponse::count).reversed())
                .toList();

        List<MechEquipGroupResponse> equipBars = byType.entrySet().stream()
                .map(e -> {
                    int[] c = e.getValue();
                    return MechEquipGroupResponse.builder()
                            .id(e.getKey().toLowerCase(Locale.ROOT))
                            .name(TYPE_LABELS.getOrDefault(e.getKey(), e.getKey()))
                            .total(c[0] + c[1] + c[2])
                            .operating(c[0])
                            .stopped(c[1])
                            .na(c[2])
                            .alarm(0)
                            .build();
                })
                .sorted(Comparator.comparingInt(MechEquipGroupResponse::total).reversed())
                .toList();

        return MechanicalPageBoardResponse.builder()
                .tabId(tabId)
                .linked(true)
                .total(total)
                .categoryMix(categoryMix)
                .runStatus(MechRunStatusResponse.builder()
                        .operating(totalRun)
                        .stopped(totalStop)
                        .na(totalUnknown)
                        .alarm(0)
                        .build())
                .equipBars(equipBars)
                .source("tbl_device + tbl_device_current_value")
                .build();
    }

    @Transactional(readOnly = true)
    public MechanicalEquipmentKpiResponse getEquipmentKpi() {
        List<Device> devices = deviceRepository.fetchMechanicalSituationDevices();
        Map<Long, DeviceCurrentValue> currentValueMap = deviceCurrentValueRepository.findAll().stream()
                .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        int run = 0;
        int stop = 0;
        int unknown = 0;
        for (Device device : devices) {
            String deviceKey = device.getDeviceKey();
            String refCode = MechanicalRunStatusResolver.extractRefDeviceCode(deviceKey);
            String mechType = MechanicalRunStatusResolver.inferMechType(deviceKey, refCode);

            DeviceCurrentValue currentValue = currentValueMap.get(device.getDeviceId());
            List<XnPointData> points = currentValue != null ? currentValue.getPoints() : List.of();
            String status = currentValue == null
                    ? MechanicalRunStatusResolver.STATUS_UNKNOWN
                    : MechanicalRunStatusResolver.resolveDeviceRunStatus(mechType, points);

            if (MechanicalRunStatusResolver.STATUS_RUN.equals(status)) {
                run++;
            } else if (MechanicalRunStatusResolver.STATUS_STOP.equals(status)) {
                stop++;
            } else {
                unknown++;
            }
        }

        return MechanicalEquipmentKpiResponse.builder()
                .run(run)
                .stop(stop)
                .unknown(unknown)
                .total(devices.size())
                .source("tbl_device + tbl_device_current_value")
                .stub(false)
                .build();
    }

    private static MechanicalSituationGroupResponse toGroup(
            String type,
            List<MechanicalSituationDeviceResponse> devices
    ) {
        int run = (int) devices.stream()
                .filter(d -> MechanicalRunStatusResolver.STATUS_RUN.equals(d.status()))
                .count();
        int stop = (int) devices.stream()
                .filter(d -> MechanicalRunStatusResolver.STATUS_STOP.equals(d.status()))
                .count();
        int unknown = (int) devices.stream()
                .filter(d -> MechanicalRunStatusResolver.STATUS_UNKNOWN.equals(d.status()))
                .count();

        return MechanicalSituationGroupResponse.builder()
                .id(type.toLowerCase(Locale.ROOT))
                .name(TYPE_LABELS.getOrDefault(type, type))
                .run(run)
                .stop(stop)
                .unknown(unknown)
                .total(devices.size())
                .devices(devices)
                .build();
    }

    private static String formatTimestamp(OffsetDateTime value) {
        return value != null ? value.toString() : null;
    }

    private static MechanicalTabResponse tab(String id, String label) {
        return MechanicalTabResponse.builder().id(id).label(label).build();
    }
}
