package com.mpole.hana.digitaltwin.dashboard.mechanical.support;

import com.mpole.hana.common.dto.XnPointData;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * tbl_device_current_value.points[] 기준 운전/정지 판정.
 * @see docs/real-db-analysis.md
 */
public final class MechanicalRunStatusResolver {

    public static final String STATUS_RUN = "run";
    public static final String STATUS_STOP = "stop";
    public static final String STATUS_UNKNOWN = "unknown";

    private static final Map<String, List<String>> RUN_TAG_RULES = Map.ofEntries(
            Map.entry("AHU", List.of("sa_fan", "ra_fan")),
            Map.entry("OHU", List.of("sa_fan")),
            Map.entry("CH", List.of("CWP-st", "CTWP-st", "CT-st")),
            Map.entry("FCU", List.of("sa_fan")),
            Map.entry("ELV", List.of("ST")),
            Map.entry("EFU", List.of("fan-st")),
            Map.entry("SEF", List.of("sa-fan", "ea-fan")),
            Map.entry("SEIF", List.of("sa-st", "ea-st", "ia-st")),
            Map.entry("CU", List.of("WP-st")),
            Map.entry("CHT", List.of("mode")),
            Map.entry("SHV", List.of("total-kW", "kWh")),
            Map.entry("HV", List.of("states")),
            Map.entry("LV", List.of("states")),
            Map.entry("GEN", List.of("kW", "V", "A"))
    );

    private MechanicalRunStatusResolver() {
    }

    public static String inferMechType(String deviceKey, String refDeviceCode) {
        String key = deviceKey == null ? "" : deviceKey;
        String code = refDeviceCode == null ? "" : refDeviceCode;
        if (key.contains("_SHV_")) {
            return "SHV";
        }
        if (key.contains("_HV_")) {
            return "HV";
        }
        if (key.contains("_LV_")) {
            return "LV";
        }
        if (key.contains("_GEN_")) {
            return "GEN";
        }
        if (key.contains("_CHT_")) {
            return "CHT";
        }
        if (key.contains("_AHU_") || code.startsWith("AHU-")) {
            return "AHU";
        }
        if (key.contains("_OHU_") || code.startsWith("HV-")) {
            return "OHU";
        }
        if (key.contains("_CH_") || code.startsWith("CH-")) {
            return "CH";
        }
        if (key.contains("_ELV_") || key.contains("ELEV")) {
            return "ELV";
        }
        if (key.contains("_FCU_") || code.startsWith("FCU-")) {
            return "FCU";
        }
        // SEIF must precede SEF (substring overlap)
        if (key.contains("_SEIF_")) {
            return "SEIF";
        }
        if (key.contains("_SEF_")) {
            return "SEF";
        }
        if (key.contains("_EFU_")) {
            return "EFU";
        }
        if (key.contains("_CU_")) {
            return "CU";
        }
        return "OTHER";
    }

    public static String inferTabId(String deviceKey, String mechType) {
        if (deviceKey != null) {
            if (deviceKey.startsWith("HVAC")) {
                return "hvac";
            }
            if (deviceKey.startsWith("HSE")) {
                return "heat";
            }
            if (deviceKey.startsWith("ELEC_TE")) {
                return "etc";
            }
            if (deviceKey.startsWith("PS_")) {
                return "electric";
            }
        }
        return switch (mechType) {
            case "AHU", "OHU", "FCU" -> "hvac";
            case "CH" -> "heat";
            case "ELV" -> "etc";
            case "SHV", "HV", "LV", "GEN" -> "electric";
            default -> "etc";
        };
    }

    public static String resolveDeviceRunStatus(String mechType, List<XnPointData> points) {
        if (points == null || points.isEmpty()) {
            return STATUS_UNKNOWN;
        }
        List<String> tags = RUN_TAG_RULES.get(mechType);
        if (tags == null || tags.isEmpty()) {
            return STATUS_UNKNOWN;
        }

        if ("ELV".equals(mechType)) {
            String raw = findValueRaw(points, Set.copyOf(tags));
            if (raw == null) {
                return STATUS_UNKNOWN;
            }
            String v = raw.toLowerCase(Locale.ROOT);
            return ("auto".equals(v) || "run".equals(v) || "1".equals(v)) ? STATUS_RUN : STATUS_STOP;
        }

        if ("SHV".equals(mechType) || "GEN".equals(mechType)) {
            return tags.stream().anyMatch(tag -> findValueRaw(points, Set.of(tag)) != null)
                    ? STATUS_RUN
                    : STATUS_UNKNOWN;
        }

        // 항온항습기: 별도 ON/OFF 포인트가 없어 운전모드(mode)로 판정한다.
        // mode != 0 → 운전, mode == 0 → 정지, 값이 없으면 미갱신.
        if ("CHT".equals(mechType)) {
            String raw = findValueRaw(points, Set.copyOf(tags));
            if (raw == null || raw.isBlank()) {
                return STATUS_UNKNOWN;
            }
            try {
                return Double.parseDouble(raw.trim()) != 0d ? STATUS_RUN : STATUS_STOP;
            } catch (NumberFormatException ex) {
                return STATUS_UNKNOWN;
            }
        }

        List<String> values = tags.stream()
                .map(tag -> findValueRaw(points, Set.of(tag)))
                .filter(v -> v != null)
                .toList();
        if (values.isEmpty()) {
            return STATUS_UNKNOWN;
        }
        return values.stream().anyMatch(MechanicalRunStatusResolver::isOnValue) ? STATUS_RUN : STATUS_STOP;
    }

    public static String extractRefDeviceCode(String deviceKey) {
        if (deviceKey == null || deviceKey.isBlank()) {
            return "";
        }
        int idx = deviceKey.lastIndexOf('_');
        return idx >= 0 ? deviceKey.substring(idx + 1) : deviceKey;
    }

    public static String extractHighlight(List<XnPointData> points) {
        if (points == null) {
            return null;
        }
        for (String tag : List.of("sa_temp", "sw-temp", "ra_temp")) {
            String raw = findValueRaw(points, Set.of(tag));
            if (raw != null) {
                return raw;
            }
        }
        return null;
    }

    private static String findValueRaw(List<XnPointData> points, Set<String> tagNames) {
        for (XnPointData point : points) {
            if (point.tagName() != null && tagNames.contains(point.tagName())) {
                return point.valueRaw();
            }
        }
        return null;
    }

    private static boolean isOnValue(String raw) {
        if (raw == null) {
            return false;
        }
        try {
            return Integer.parseInt(raw.trim()) == 1;
        } catch (NumberFormatException ignored) {
            String v = raw.toLowerCase(Locale.ROOT);
            return "on".equals(v) || "1".equals(v);
        }
    }
}
