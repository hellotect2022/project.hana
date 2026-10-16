package com.mpole.hana.digitaltwin.overview.service;

import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.InfraCard;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.OverviewResponse;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.ValueCard;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.MemberType;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.Period;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.Section;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.ValueSource;
import com.mpole.hana.digitaltwin.overview.entity.OverviewItem;
import com.mpole.hana.digitaltwin.overview.entity.OverviewItemMember;
import com.mpole.hana.digitaltwin.overview.repository.OverviewItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 종합 현황 값 계산 (2026-10-16).
 *
 * <pre>
 *   CURRENT_VALUE   = Σ 현재값                                       (tbl_device_current_value)
 *   SNAPSHOT_DELTA  = Σ max(현재값 − 기간 시작 스냅샷, 0)            (tbl_energy_snapshot, KST)
 *                     기간 시작: TODAY 오늘 00:00 / MONTH 1일 00:00 / YEAR 1월 1일 00:00
 *   표시 값         = 합 × scale, decimals 자리 반올림
 *   INFRA_STATUS    = tbl_device_infra.network_state 그대로
 * </pre>
 * 분류(CATEGORY) 대상은 그 소분류 장비 전체의 같은 태그 포인트로 펼쳐 합산한다. 같은 포인트가 여러 대상에 겹치면 한 번만 센다.
 * 현재값이 없거나 숫자가 아니거나, SNAPSHOT_DELTA 인데 스냅샷이 없는 포인트는 빼고 excludedCount 로 알린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverviewQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 이보다 오래 갱신되지 않은 현재값은 stale(통신 끊김 의심) */
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private final OverviewItemRepository repository;
    private final OverviewItemService itemService;
    private final OverviewLookup lookup;

    /** 클라이언트 — 사용 중인 항목 전체 */
    public OverviewResponse overview() {
        return build(repository.findAllByActiveTrueOrderBySectionAscSortOrderAscItemIdAsc());
    }

    /** 관리 화면 미리보기 — 항목 1개(사용 여부와 무관) */
    public OverviewResponse preview(Long itemId) {
        return build(List.of(itemService.find(itemId)));
    }

    private OverviewResponse build(List<OverviewItem> items) {
        OffsetDateTime now = OffsetDateTime.now(KST);

        // 1) 항목별 포인트 목록(분류는 펼침)
        Map<Long, List<Long>> pointsByItem = new HashMap<>();
        Set<Long> allPoints = new HashSet<>();
        Set<Long> infraIds = new HashSet<>();
        Map<String, List<Long>> categoryCache = new HashMap<>();
        for (OverviewItem item : items) {
            if (item.getSection() == Section.INFRA_STATUS) {
                item.getMembers().forEach(m -> { if (m.getInfraId() != null) infraIds.add(m.getInfraId()); });
                continue;
            }
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (OverviewItemMember m : item.getMembers()) {
                if (m.getMemberType() == MemberType.POINT && m.getPointId() != null) {
                    ids.add(m.getPointId());
                } else if (m.getMemberType() == MemberType.CATEGORY && m.getCategoryId() != null) {
                    ids.addAll(categoryCache.computeIfAbsent(m.getCategoryId() + ":" + m.getTagName(),
                            k -> lookup.categoryPointIds(m.getCategoryId(), m.getTagName())));
                }
            }
            pointsByItem.put(item.getItemId(), new ArrayList<>(ids));
            allPoints.addAll(ids);
        }

        // 2) 현재값, 기간별 기준값(필요한 기간만)
        Map<Long, OverviewLookup.CurrentValue> current = lookup.currentValues(allPoints);
        Map<Period, Map<Long, OverviewLookup.BaseValue>> baseByPeriod = new EnumMap<>(Period.class);
        for (OverviewItem item : items) {
            if (item.getValueSource() != ValueSource.SNAPSHOT_DELTA || item.getPeriod() == null) continue;
            if (baseByPeriod.containsKey(item.getPeriod())) continue;
            Set<Long> periodPoints = new HashSet<>();
            items.stream()
                    .filter(i -> i.getValueSource() == ValueSource.SNAPSHOT_DELTA && i.getPeriod() == item.getPeriod())
                    .forEach(i -> periodPoints.addAll(pointsByItem.getOrDefault(i.getItemId(), List.of())));
            baseByPeriod.put(item.getPeriod(), lookup.baseValues(periodPoints, periodStart(item.getPeriod(), now)));
        }
        Map<Long, OverviewLookup.InfraInfo> infras = lookup.infraInfo(infraIds);

        // 3) 카드
        List<ValueCard> usage = new ArrayList<>();
        List<ValueCard> production = new ArrayList<>();
        List<InfraCard> infraCards = new ArrayList<>();
        for (OverviewItem item : items) {
            switch (item.getSection()) {
                case ENERGY_USAGE -> usage.add(valueCard(item, pointsByItem, current, baseByPeriod, now));
                case ENERGY_PRODUCTION -> production.add(valueCard(item, pointsByItem, current, baseByPeriod, now));
                case INFRA_STATUS -> infraCards.add(infraCard(item, infras));
            }
        }
        return new OverviewResponse(now, usage, production, infraCards);
    }

    private ValueCard valueCard(OverviewItem item, Map<Long, List<Long>> pointsByItem,
                                Map<Long, OverviewLookup.CurrentValue> current,
                                Map<Period, Map<Long, OverviewLookup.BaseValue>> baseByPeriod,
                                OffsetDateTime now) {
        List<Long> ids = pointsByItem.getOrDefault(item.getItemId(), List.of());
        boolean snapshot = item.getValueSource() == ValueSource.SNAPSHOT_DELTA;
        Map<Long, OverviewLookup.BaseValue> bases = snapshot
                ? baseByPeriod.getOrDefault(item.getPeriod(), Map.of())
                : Map.of();

        double sum = 0;
        int used = 0, excluded = 0, stale = 0;
        OffsetDateTime baseAt = null;
        boolean adjusted = false;

        for (Long id : ids) {
            OverviewLookup.CurrentValue cur = current.get(id);
            if (cur == null || cur.value() == null) {
                excluded++;
                continue;
            }
            if (cur.updatedAt() != null && cur.updatedAt().isBefore(now.minus(STALE_AFTER))) stale++;
            if (snapshot) {
                OverviewLookup.BaseValue base = bases.get(id);
                if (base == null) {
                    excluded++;
                    continue;
                }
                sum += Math.max(cur.value() - base.value(), 0);
                if (base.baseAt() != null && (baseAt == null || base.baseAt().isBefore(baseAt))) baseAt = base.baseAt();
                adjusted |= base.adjusted();
            } else {
                sum += cur.value();
            }
            used++;
        }

        BigDecimal value = used == 0
                ? null
                : BigDecimal.valueOf(sum).multiply(item.getScale()).setScale(item.getDecimals(), RoundingMode.HALF_UP);

        return new ValueCard(item.getItemId(), item.getLabel(), value, item.getUnit(), item.getDecimals(),
                item.getValueSource(), item.getPeriod(),
                snapshot ? periodStart(item.getPeriod(), now) : null,
                ids.size(), excluded, stale, baseAt, adjusted);
    }

    private InfraCard infraCard(OverviewItem item, Map<Long, OverviewLookup.InfraInfo> infras) {
        Long infraId = item.getMembers().isEmpty() ? null : item.getMembers().get(0).getInfraId();
        OverviewLookup.InfraInfo ii = infraId == null ? null : infras.get(infraId);
        return new InfraCard(item.getItemId(), item.getLabel(), infraId,
                ii != null ? ii.infraName() : null,
                ii != null && ii.networkState() != null ? ii.networkState() : "UNKNOWN",
                ii != null ? ii.lastCommAt() : null,
                ii == null);
    }

    private static OffsetDateTime periodStart(Period period, OffsetDateTime now) {
        LocalDate d = now.toLocalDate();
        LocalDate start = switch (period) {
            case TODAY -> d;
            case MONTH -> d.withDayOfMonth(1);
            case YEAR -> d.withDayOfYear(1);
        };
        return start.atStartOfDay(KST).toOffsetDateTime();
    }
}
