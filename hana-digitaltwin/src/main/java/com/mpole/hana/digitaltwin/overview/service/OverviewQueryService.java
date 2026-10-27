package com.mpole.hana.digitaltwin.overview.service;

import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.InfraCard;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.OverviewResponse;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.RatioCard;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.RatioTerm;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.ValueCard;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.MemberType;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.Period;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.Role;
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
 * 종합 현황 값 계산 (2026-10-16, 자립률 2026-10-23).
 *
 * <pre>
 *   CURRENT_VALUE   = Σ 현재값                                       (tbl_device_current_value)
 *   SNAPSHOT_DELTA  = Σ max(현재값 − 기간 시작 스냅샷, 0)            (tbl_energy_snapshot, KST)
 *                     기간 시작: TODAY 오늘 00:00 / MONTH 1일 00:00 / YEAR 1월 1일 00:00
 *   표시 값         = 합 × scale, decimals 자리 반올림
 *   INFRA_STATUS    = tbl_device_infra.network_state 그대로
 *   ENERGY_INDEPENDENCE(자립률, %) — 카드 참조
 *                   = Σ(분자 카드 값 × 계수) ÷ Σ(분모 카드 값 × 계수) × 100
 *                     카드 값 = 참조한 금일 에너지 사용·생산 카드의 표시 값(위 규칙 그대로, 패널 숫자와 같음).
 *                     참조 카드가 "숨김"이어도 계산한다. 값 없는 카드는 빼고 missingCount 로 알린다.
 *                     분모가 0 이하이거나 분자·분모 중 값 있는 카드가 없으면 null.
 *                     ZEB 등급: 100↑ 1 / 80↑ 2 / 60↑ 3 / 40↑ 4 / 20↑ 5 / 20 미만 없음
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
    /** 자립률 계산 내역(분자·분모·카드별 값)의 표시 자릿수 */
    private static final int TERM_DECIMALS = 4;

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

        // 0) 계산할 카드 = 목록의 사용·생산 카드 + 자립률이 참조하는데 목록에 없는 카드(숨김 카드, 미리보기 1건)
        Map<Long, OverviewItem> valueItems = new LinkedHashMap<>();
        items.stream().filter(OverviewQueryService::isValueSection).forEach(i -> valueItems.put(i.getItemId(), i));
        Set<Long> missingRefs = new HashSet<>();
        items.stream()
                .filter(i -> i.getSection() == Section.ENERGY_INDEPENDENCE)
                .flatMap(i -> i.getMembers().stream())
                .map(OverviewItemMember::getRefItemId)
                .filter(id -> id != null && !valueItems.containsKey(id))
                .forEach(missingRefs::add);
        if (!missingRefs.isEmpty()) {
            repository.findAllById(missingRefs).stream()
                    .filter(OverviewQueryService::isValueSection)
                    .forEach(i -> valueItems.put(i.getItemId(), i));
        }

        // 1) 카드별 포인트 목록(분류는 펼침)
        Map<Long, List<Long>> pointsByItem = new HashMap<>();
        Set<Long> allPoints = new HashSet<>();
        Map<String, List<Long>> categoryCache = new HashMap<>();
        for (OverviewItem item : valueItems.values()) {
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
        Set<Long> infraIds = new HashSet<>();
        items.stream().filter(i -> i.getSection() == Section.INFRA_STATUS)
                .forEach(i -> i.getMembers().forEach(m -> { if (m.getInfraId() != null) infraIds.add(m.getInfraId()); }));

        // 2) 현재값, 기간별 기준값(필요한 기간만)
        Map<Long, OverviewLookup.CurrentValue> current = lookup.currentValues(allPoints);
        Map<Period, Map<Long, OverviewLookup.BaseValue>> baseByPeriod = new EnumMap<>(Period.class);
        for (OverviewItem item : valueItems.values()) {
            if (item.getValueSource() != ValueSource.SNAPSHOT_DELTA || item.getPeriod() == null) continue;
            if (baseByPeriod.containsKey(item.getPeriod())) continue;
            Set<Long> periodPoints = new HashSet<>();
            valueItems.values().stream()
                    .filter(i -> i.getValueSource() == ValueSource.SNAPSHOT_DELTA && i.getPeriod() == item.getPeriod())
                    .forEach(i -> periodPoints.addAll(pointsByItem.getOrDefault(i.getItemId(), List.of())));
            baseByPeriod.put(item.getPeriod(), lookup.baseValues(periodPoints, periodStart(item.getPeriod(), now)));
        }
        Map<Long, OverviewLookup.InfraInfo> infras = lookup.infraInfo(infraIds);

        // 3) 카드 값(사용·생산, 참조 카드 포함)
        Map<Long, ValueCard> cardById = new HashMap<>();
        for (OverviewItem item : valueItems.values()) {
            cardById.put(item.getItemId(), valueCard(item, pointsByItem, current, baseByPeriod, now));
        }

        // 4) 응답 — 사용·생산은 목록에 있던 카드만(참조 때문에 불러온 숨김 카드는 내보내지 않는다)
        List<ValueCard> usage = new ArrayList<>();
        List<ValueCard> production = new ArrayList<>();
        List<InfraCard> infraCards = new ArrayList<>();
        List<RatioCard> ratioCards = new ArrayList<>();
        for (OverviewItem item : items) {
            switch (item.getSection()) {
                case ENERGY_USAGE -> usage.add(cardById.get(item.getItemId()));
                case ENERGY_PRODUCTION -> production.add(cardById.get(item.getItemId()));
                case INFRA_STATUS -> infraCards.add(infraCard(item, infras));
                case ENERGY_INDEPENDENCE -> ratioCards.add(ratioCard(item, valueItems, cardById));
            }
        }
        return new OverviewResponse(now, usage, production, infraCards, ratioCards);
    }

    private static boolean isValueSection(OverviewItem i) {
        return i.getSection() == Section.ENERGY_USAGE || i.getSection() == Section.ENERGY_PRODUCTION;
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

    private RatioCard ratioCard(OverviewItem item, Map<Long, OverviewItem> valueItems, Map<Long, ValueCard> cardById) {
        BigDecimal numerator = BigDecimal.ZERO, denominator = BigDecimal.ZERO;
        boolean numeratorUsed = false, denominatorUsed = false;
        int missing = 0;
        List<RatioTerm> terms = new ArrayList<>();

        for (OverviewItemMember m : item.getMembers()) {
            OverviewItem ref = m.getRefItemId() == null ? null : valueItems.get(m.getRefItemId());
            ValueCard card = ref == null ? null : cardById.get(ref.getItemId());
            BigDecimal refValue = card == null ? null : card.value();
            BigDecimal coefficient = m.getCoefficient() != null ? m.getCoefficient() : BigDecimal.ONE;
            BigDecimal weighted = refValue == null ? null : refValue.multiply(coefficient);

            if (weighted == null) {
                missing++;
            } else if (m.getRole() == Role.NUMERATOR) {
                numerator = numerator.add(weighted);
                numeratorUsed = true;
            } else if (m.getRole() == Role.DENOMINATOR) {
                denominator = denominator.add(weighted);
                denominatorUsed = true;
            }

            terms.add(new RatioTerm(m.getMemberId(), m.getRole(), m.getRefItemId(),
                    ref != null ? ref.getLabel() : null,
                    ref != null ? ref.getUnit() : null,
                    refValue,
                    OverviewItemService.parseExpr(m.getCoefficientExpr(), coefficient),
                    coefficient.stripTrailingZeros(),
                    round(weighted),
                    ref == null));
        }

        BigDecimal value = numeratorUsed && denominatorUsed && denominator.signum() > 0
                ? numerator.multiply(BigDecimal.valueOf(100))
                        .divide(denominator, item.getDecimals(), RoundingMode.HALF_UP)
                : null;

        return new RatioCard(item.getItemId(), item.getLabel(), value, item.getDecimals(), zebGrade(value),
                numeratorUsed ? round(numerator) : null, denominatorUsed ? round(denominator) : null,
                missing, terms);
    }

    /** ZEB 등급 — 100% 이상 1등급 … 20% 이상 5등급, 20% 미만은 등급 없음 */
    static Integer zebGrade(BigDecimal percent) {
        if (percent == null) return null;
        double p = percent.doubleValue();
        if (p >= 100) return 1;
        if (p >= 80) return 2;
        if (p >= 60) return 3;
        if (p >= 40) return 4;
        if (p >= 20) return 5;
        return null;
    }

    private static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(TERM_DECIMALS, RoundingMode.HALF_UP);
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
