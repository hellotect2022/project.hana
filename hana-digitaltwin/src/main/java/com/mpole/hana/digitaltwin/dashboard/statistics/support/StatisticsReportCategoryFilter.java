package com.mpole.hana.digitaltwin.dashboard.statistics.support;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.device.service.DeviceCategoryCascadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 통계·리포트 시스템 필터(전력/기계/건축/소방) → 소분류 category id. */
@Component
@RequiredArgsConstructor
public class StatisticsReportCategoryFilter {

    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceCategoryCascadeService deviceCategoryCascadeService;

    /**
     * @return null = 전체, empty = 해당 시스템 없음
     */
    public List<Long> resolveSmallCategoryIds(String system) {
        if (system == null || system.isBlank() || "all".equalsIgnoreCase(system.trim())) {
            return null;
        }
        List<DeviceCategory> all = deviceCategoryRepository.findAllWithParent();
        Set<Long> roots = new LinkedHashSet<>();
        switch (system.trim().toLowerCase(Locale.ROOT)) {
            case "fire" -> addRootsByCode(all, roots, "FDP", "DET");
            case "power" -> addRootsByCode(all, roots, "KWH", "EHV", "LV", "GEN", "MCC", "SHV", "HV");
            case "mech" -> addRootsByNameKeywords(all, roots, "기계", "공조", "환기", "열원", "냉동");
            case "building" -> addRootsByNameKeywords(all, roots, "건축", "승강", "급배수", "위생", "펌프");
            default -> {
                return List.of();
            }
        }
        if (roots.isEmpty()) {
            return List.of();
        }
        Set<Long> small = new LinkedHashSet<>();
        for (Long rootId : roots) {
            small.addAll(deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(rootId));
        }
        return new ArrayList<>(small);
    }

    public static String systemLabelFromCategory(String categoryCode, String categoryName) {
        String code = categoryCode == null ? "" : categoryCode.trim().toUpperCase(Locale.ROOT);
        if (code.equals("FDP") || code.equals("DET")) {
            return "소방";
        }
        if (Set.of("KWH", "EHV", "LV", "GEN", "MCC", "SHV", "HV").contains(code)) {
            return "전력";
        }
        String name = categoryName == null ? "" : categoryName;
        if (name.contains("소방") || name.contains("화재")) {
            return "소방";
        }
        if (name.contains("전력") || name.contains("전기")) {
            return "전력";
        }
        if (name.contains("승강") || name.contains("급배") || name.contains("펌프") || name.contains("건축")) {
            return "건축설비";
        }
        if (name.contains("공조") || name.contains("냉동") || name.contains("환기") || name.contains("기계")) {
            return "기계설비";
        }
        return name.isBlank() ? "기타" : name;
    }

    private static void addRootsByCode(List<DeviceCategory> all, Set<Long> roots, String... codes) {
        Set<String> want = Set.of(codes);
        for (DeviceCategory c : all) {
            if (c.getCategoryCode() != null && want.contains(c.getCategoryCode().toUpperCase(Locale.ROOT))) {
                roots.add(c.getCategoryId());
            }
        }
    }

    private static void addRootsByNameKeywords(List<DeviceCategory> all, Set<Long> roots, String... keywords) {
        for (DeviceCategory c : all) {
            String name = c.getCategoryName();
            if (name == null) {
                continue;
            }
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    roots.add(c.getCategoryId());
                    break;
                }
            }
        }
    }
}
