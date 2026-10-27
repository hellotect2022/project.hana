package com.mpole.hana.digitaltwin.overview.service;

import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.ItemRequest;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.ItemResponse;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.MemberRequest;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.MemberResponse;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.OrderRequest;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.MemberType;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.Role;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.Section;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.ValueSource;
import com.mpole.hana.digitaltwin.overview.entity.OverviewItem;
import com.mpole.hana.digitaltwin.overview.entity.OverviewItemMember;
import com.mpole.hana.digitaltwin.overview.repository.OverviewItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 종합 현황 항목 설정 (2026-10-16) — 등록·수정·삭제·순서 + 관리 화면용 표시 정보.
 *
 * <h3>저장 규칙</h3>
 * <ul>
 *   <li>INFRA_STATUS: 값 출처·기간 없음, 구성 대상은 INFRA 정확히 1개</li>
 *   <li>에너지 영역: 값 출처 필수, 구성 대상 POINT/CATEGORY 1개 이상(중복 불가)</li>
 *   <li>SNAPSHOT_DELTA: 기간 필수, <b>모든 대상이 누적 옵션(accumulate)이어야 저장된다</b> — 누적이 아니면 스냅샷이 없어 계산할 수 없다</li>
 *   <li>CURRENT_VALUE: 기간 없음</li>
 *   <li>대상이 실제로 있어야 한다(포인트·분류+태그·인프라)</li>
 *   <li>ENERGY_INDEPENDENCE(자립률, 2026-10-23): 값 출처·기간 없음, 구성 대상은 ITEM(금일 에너지 사용·생산 카드) +
 *       역할(분자/분모) + 계수식. 분자·분모 각각 1개 이상. 결과 단위 %</li>
 *   <li>다른 항목이 참조(ITEM) 중인 카드는 삭제할 수 없다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverviewItemService {

    private final OverviewItemRepository repository;
    private final OverviewLookup lookup;

    public List<ItemResponse> list(Section section) {
        List<OverviewItem> items = section == null
                ? repository.findAllByOrderBySectionAscSortOrderAscItemIdAsc()
                : repository.findAllBySectionOrderBySortOrderAscItemIdAsc(section);
        return toResponses(items);
    }

    @Transactional
    public ItemResponse create(ItemRequest req) {
        validate(req);
        OverviewItem item = new OverviewItem();
        apply(item, req);
        item.setSortOrder(repository.maxSortOrder(req.section()) + 1);
        return toResponses(List.of(repository.save(item))).get(0);
    }

    @Transactional
    public ItemResponse update(Long itemId, ItemRequest req) {
        OverviewItem item = find(itemId);
        if (req.section() != null && req.section() != item.getSection()) {
            throw new IllegalArgumentException("영역(section)은 바꿀 수 없습니다. 삭제 후 다른 영역에 새로 등록하세요.");
        }
        ItemRequest fixed = new ItemRequest(item.getSection(), req.label(), req.valueSource(), req.period(),
                req.unit(), req.scale(), req.decimals(), req.active(), req.members());
        validate(fixed);
        apply(item, fixed);
        return toResponses(List.of(repository.save(item))).get(0);
    }

    /** 삭제 — 자립률 등이 참조 중인 카드는 지우지 않는다(참조 항목에서 먼저 빼야 함) */
    @Transactional
    public void delete(Long itemId) {
        OverviewItem item = find(itemId);
        List<String> users = repository.findLabelsReferencing(itemId);
        if (!users.isEmpty()) {
            throw new IllegalArgumentException("이 카드를 쓰는 항목이 있어 삭제할 수 없습니다: " + String.join(", ", users)
                    + " — 에너지 자립률에서 이 카드를 먼저 빼세요.");
        }
        repository.delete(item);
    }

    /** 영역 안 순서 — itemIds 순서대로 1, 2, 3 … 목록에 없는 항목은 그 뒤로 기존 순서 유지 */
    @Transactional
    public List<ItemResponse> reorder(OrderRequest req) {
        if (req == null || req.section() == null || req.itemIds() == null) {
            throw new IllegalArgumentException("section 과 itemIds 가 필요합니다.");
        }
        List<OverviewItem> items = repository.findAllBySectionOrderBySortOrderAscItemIdAsc(req.section());
        Map<Long, OverviewItem> byId = new HashMap<>();
        items.forEach(i -> byId.put(i.getItemId(), i));
        for (Long id : req.itemIds()) {
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException("이 영역에 없는 항목입니다. itemId=" + id);
            }
        }
        int order = 1;
        Set<Long> placed = new HashSet<>();
        for (Long id : req.itemIds()) {
            if (placed.add(id)) byId.get(id).setSortOrder(order++);
        }
        for (OverviewItem i : items) {
            if (!placed.contains(i.getItemId())) i.setSortOrder(order++);
        }
        return list(req.section());
    }

    OverviewItem find(Long itemId) {
        return repository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("종합 현황 항목을 찾을 수 없습니다. id=" + itemId));
    }

    /* ───────────────────────── 검증·반영 ───────────────────────── */

    private void validate(ItemRequest req) {
        if (req == null || req.section() == null) throw new IllegalArgumentException("영역(section)은 필수입니다.");
        if (req.label() == null || req.label().isBlank()) throw new IllegalArgumentException("표시 이름(label)은 필수입니다.");
        if (req.label().trim().length() > 100) throw new IllegalArgumentException("표시 이름은 100자 이하입니다.");
        List<MemberRequest> members = req.members() == null ? List.of() : req.members();

        if (req.section() == Section.INFRA_STATUS) {
            if (req.valueSource() != null || req.period() != null) {
                throw new IllegalArgumentException("통신 상태 항목에는 값 출처·기간을 넣지 않습니다.");
            }
            if (members.size() != 1 || members.get(0).memberType() != MemberType.INFRA || members.get(0).infraId() == null) {
                throw new IllegalArgumentException("통신 상태 항목은 인프라(INFRA) 1개를 지정해야 합니다.");
            }
            if (lookup.infraInfo(List.of(members.get(0).infraId())).isEmpty()) {
                throw new IllegalArgumentException("인프라를 찾을 수 없습니다. infraId=" + members.get(0).infraId());
            }
            return;
        }

        if (req.section() == Section.ENERGY_INDEPENDENCE) {
            validateRatio(req, members);
            return;
        }

        if (req.valueSource() == null) throw new IllegalArgumentException("값 출처(valueSource)는 필수입니다.");
        if (req.valueSource() == ValueSource.SNAPSHOT_DELTA && req.period() == null) {
            throw new IllegalArgumentException("누적 증가분(SNAPSHOT_DELTA)은 기간(period: TODAY/MONTH/YEAR)이 필요합니다.");
        }
        if (req.valueSource() == ValueSource.CURRENT_VALUE && req.period() != null) {
            throw new IllegalArgumentException("현재값(CURRENT_VALUE)에는 기간을 넣지 않습니다.");
        }
        if (req.scale() != null && req.scale().signum() <= 0) throw new IllegalArgumentException("배율(scale)은 0보다 커야 합니다.");
        if (req.decimals() != null && (req.decimals() < 0 || req.decimals() > 6)) {
            throw new IllegalArgumentException("소수 자릿수(decimals)는 0~6 입니다.");
        }
        if (members.isEmpty()) throw new IllegalArgumentException("구성 대상을 하나 이상 지정하세요.");

        boolean snapshot = req.valueSource() == ValueSource.SNAPSHOT_DELTA;
        Set<String> seen = new HashSet<>();
        List<Long> pointIds = new ArrayList<>();
        List<String> notAccumulate = new ArrayList<>();

        for (MemberRequest m : members) {
            if (m.memberType() == MemberType.POINT) {
                if (m.pointId() == null) throw new IllegalArgumentException("포인트 대상에 pointId 가 없습니다.");
                if (!seen.add("P" + m.pointId())) throw new IllegalArgumentException("같은 포인트가 두 번 들어 있습니다. pointId=" + m.pointId());
                pointIds.add(m.pointId());
            } else if (m.memberType() == MemberType.CATEGORY) {
                if (m.categoryId() == null || m.tagName() == null || m.tagName().isBlank()) {
                    throw new IllegalArgumentException("분류 대상에는 categoryId 와 tagName 이 필요합니다.");
                }
                if (!seen.add("C" + m.categoryId() + ":" + m.tagName().trim())) {
                    throw new IllegalArgumentException("같은 분류·태그가 두 번 들어 있습니다.");
                }
                OverviewLookup.CategoryInfo ci = lookup.categoryInfo(m.categoryId(), m.tagName().trim());
                if (!ci.exists()) throw new IllegalArgumentException("분류를 찾을 수 없습니다. categoryId=" + m.categoryId());
                if (!ci.hasTag()) {
                    throw new IllegalArgumentException("분류 [" + ci.path() + "] 에 태그 '" + m.tagName() + "' 정의가 없습니다.");
                }
                if (snapshot && !ci.accumulate()) notAccumulate.add(ci.path() + " · " + m.tagName().trim());
            } else {
                throw new IllegalArgumentException("에너지 항목의 구성 대상은 포인트(POINT) 또는 분류(CATEGORY)입니다.");
            }
        }

        if (!pointIds.isEmpty()) {
            Map<Long, OverviewLookup.PointInfo> infos = lookup.pointInfo(pointIds);
            for (Long id : pointIds) {
                OverviewLookup.PointInfo pi = infos.get(id);
                if (pi == null) throw new IllegalArgumentException("포인트를 찾을 수 없습니다. pointId=" + id);
                if (snapshot && !pi.accumulate()) notAccumulate.add(pi.pointKey());
            }
        }

        if (!notAccumulate.isEmpty()) {
            throw new IllegalArgumentException("누적 증가분은 누적 옵션이 켜진 대상만 저장할 수 있습니다. 누적 꺼짐: "
                    + String.join(", ", notAccumulate)
                    + " — 장비 카테고리 관리 › 고급에서 누적을 켜거나, 값 출처를 현재값으로 바꾸세요.");
        }
    }

    /**
     * 자립률(카드 참조) — 구성 대상은 모두 ITEM(금일 에너지 사용·생산 카드), 역할 필수,
     * 계수식의 숫자는 모두 0보다 큼, 같은 카드·같은 역할 중복 불가, 분자·분모 각각 하나 이상.
     * 값 출처·기간은 카드가 갖고 있으므로 받지 않는다(넣어도 무시).
     */
    private void validateRatio(ItemRequest req, List<MemberRequest> members) {
        if (req.decimals() != null && (req.decimals() < 0 || req.decimals() > 6)) {
            throw new IllegalArgumentException("소수 자릿수(decimals)는 0~6 입니다.");
        }
        if (members.isEmpty()) throw new IllegalArgumentException("참조할 카드를 하나 이상 지정하세요.");

        Set<String> seen = new HashSet<>();
        Set<Long> refIds = new HashSet<>();
        boolean hasNumerator = false, hasDenominator = false;
        for (MemberRequest m : members) {
            if (m.memberType() != MemberType.ITEM || m.refItemId() == null) {
                throw new IllegalArgumentException("자립률의 구성 대상은 금일 에너지 사용·생산 카드(ITEM, refItemId)입니다.");
            }
            if (m.role() == null) {
                throw new IllegalArgumentException("자립률 구성 대상에는 분자/분모(role)를 지정해야 합니다.");
            }
            for (BigDecimal f : factorsOf(m)) {
                if (f == null || f.signum() <= 0) throw new IllegalArgumentException("계수(계수식의 각 숫자)는 0보다 커야 합니다.");
            }
            if (!seen.add(m.role() + "/" + m.refItemId())) {
                throw new IllegalArgumentException("같은 카드가 " + (m.role() == Role.NUMERATOR ? "분자" : "분모") + "에 두 번 들어 있습니다. itemId=" + m.refItemId());
            }
            refIds.add(m.refItemId());
            hasNumerator |= m.role() == Role.NUMERATOR;
            hasDenominator |= m.role() == Role.DENOMINATOR;
        }
        if (!hasNumerator || !hasDenominator) {
            throw new IllegalArgumentException("자립률에는 분자(생산)와 분모(소비) 카드가 각각 하나 이상 필요합니다.");
        }

        Map<Long, OverviewItem> refs = new HashMap<>();
        repository.findAllById(refIds).forEach(i -> refs.put(i.getItemId(), i));
        for (Long id : refIds) {
            OverviewItem ref = refs.get(id);
            if (ref == null) throw new IllegalArgumentException("참조할 카드를 찾을 수 없습니다. itemId=" + id);
            if (ref.getSection() != Section.ENERGY_USAGE && ref.getSection() != Section.ENERGY_PRODUCTION) {
                throw new IllegalArgumentException("자립률은 금일 에너지 사용·생산 카드만 참조할 수 있습니다: " + ref.getLabel());
            }
        }
    }

    /** 계수식 — factors 가 있으면 그대로, 없으면 coefficient 1개, 둘 다 없으면 1 */
    private static List<BigDecimal> factorsOf(MemberRequest m) {
        if (m.factors() != null && !m.factors().isEmpty()) return m.factors();
        return List.of(m.coefficient() != null ? m.coefficient() : BigDecimal.ONE);
    }

    /** 저장된 계수식("10.5*1.1") → 숫자 목록. 비었으면 coefficient 1개 */
    static List<BigDecimal> parseExpr(String expr, BigDecimal coefficient) {
        if (expr == null || expr.isBlank()) return List.of(coefficient != null ? coefficient : BigDecimal.ONE);
        List<BigDecimal> out = new ArrayList<>();
        for (String s : expr.split("\\*")) {
            if (!s.isBlank()) out.add(new BigDecimal(s.trim()));
        }
        return out.isEmpty() ? List.of(coefficient != null ? coefficient : BigDecimal.ONE) : out;
    }

    private void apply(OverviewItem item, ItemRequest req) {
        boolean infra = req.section() == Section.INFRA_STATUS;
        boolean ratio = req.section() == Section.ENERGY_INDEPENDENCE;
        boolean noSource = infra || ratio; // 통신 상태·자립률은 값 출처·기간이 없다(자립률은 카드가 갖고 있음)
        item.setSection(req.section());
        item.setLabel(req.label().trim());
        item.setValueSource(noSource ? null : req.valueSource());
        item.setPeriod(noSource || req.valueSource() == ValueSource.CURRENT_VALUE ? null : req.period());
        // 자립률은 결과가 % 라 단위·배율을 고정한다(환산은 카드별 계수로)
        item.setUnit(ratio ? "%" : infra || req.unit() == null || req.unit().isBlank() ? null : req.unit().trim());
        item.setScale(noSource || req.scale() == null ? BigDecimal.ONE : req.scale());
        item.setDecimals(infra ? 0 : req.decimals() == null ? 1 : req.decimals());
        item.setActive(req.active() == null || req.active());

        List<OverviewItemMember> next = new ArrayList<>();
        for (MemberRequest m : req.members()) {
            OverviewItemMember mem = new OverviewItemMember();
            mem.setMemberType(m.memberType());
            if (ratio) {
                List<BigDecimal> factors = factorsOf(m);
                mem.setRole(m.role());
                mem.setCoefficient(factors.stream().reduce(BigDecimal.ONE, BigDecimal::multiply));
                mem.setCoefficientExpr(factors.stream().map(BigDecimal::toPlainString).reduce((a, b) -> a + "*" + b).orElse("1"));
            } else {
                mem.setRole(null);
                mem.setCoefficient(BigDecimal.ONE);
                mem.setCoefficientExpr(null);
            }
            switch (m.memberType()) {
                case POINT -> mem.setPointId(m.pointId());
                case CATEGORY -> {
                    mem.setCategoryId(m.categoryId());
                    mem.setTagName(m.tagName().trim());
                }
                case INFRA -> mem.setInfraId(m.infraId());
                case ITEM -> mem.setRefItemId(m.refItemId());
            }
            next.add(mem);
        }
        item.replaceMembers(next);
    }

    /* ───────────────────────── 응답(표시 정보 포함) ───────────────────────── */

    private List<ItemResponse> toResponses(List<OverviewItem> items) {
        Set<Long> pointIds = new HashSet<>();
        Set<Long> infraIds = new HashSet<>();
        Set<Long> refItemIds = new HashSet<>();
        items.forEach(i -> i.getMembers().forEach(m -> {
            if (m.getPointId() != null) pointIds.add(m.getPointId());
            if (m.getInfraId() != null) infraIds.add(m.getInfraId());
            if (m.getRefItemId() != null) refItemIds.add(m.getRefItemId());
        }));
        Map<Long, OverviewLookup.PointInfo> points = lookup.pointInfo(pointIds);
        Map<Long, OverviewLookup.InfraInfo> infras = lookup.infraInfo(infraIds);
        Map<Long, OverviewItem> refItems = new HashMap<>();
        if (!refItemIds.isEmpty()) repository.findAllById(refItemIds).forEach(r -> refItems.put(r.getItemId(), r));
        Map<String, OverviewLookup.CategoryInfo> categoryCache = new HashMap<>();

        List<ItemResponse> out = new ArrayList<>(items.size());
        for (OverviewItem i : items) {
            boolean ratio = i.getSection() == Section.ENERGY_INDEPENDENCE;
            List<MemberResponse> members = new ArrayList<>();
            for (OverviewItemMember m : i.getMembers()) {
                List<BigDecimal> factors = ratio ? parseExpr(m.getCoefficientExpr(), m.getCoefficient()) : null;
                members.add(switch (m.getMemberType()) {
                    case POINT -> {
                        OverviewLookup.PointInfo pi = points.get(m.getPointId());
                        yield new MemberResponse(m.getMemberId(), m.getMemberType(), m.getPointId(), null, null, null,
                                pi != null ? pi.pointKey() : "포인트 #" + m.getPointId(),
                                pi != null ? pi.deviceName() : null,
                                pi != null ? pi.accumulate() : null,
                                null, pi == null, m.getRole(), m.getCoefficient(), null, factors);
                    }
                    case CATEGORY -> {
                        OverviewLookup.CategoryInfo ci = categoryCache.computeIfAbsent(
                                m.getCategoryId() + ":" + m.getTagName(),
                                k -> lookup.categoryInfo(m.getCategoryId(), m.getTagName()));
                        yield new MemberResponse(m.getMemberId(), m.getMemberType(), null, m.getCategoryId(), m.getTagName(), null,
                                ci.exists() && ci.path() != null ? ci.path() : "분류 #" + m.getCategoryId(),
                                m.getTagName(),
                                ci.accumulate(),
                                ci.pointCount(),
                                !ci.exists() || !ci.hasTag(), m.getRole(), m.getCoefficient(), null, factors);
                    }
                    case INFRA -> {
                        OverviewLookup.InfraInfo ii = infras.get(m.getInfraId());
                        yield new MemberResponse(m.getMemberId(), m.getMemberType(), null, null, null, m.getInfraId(),
                                ii != null ? ii.infraName() : "인프라 #" + m.getInfraId(),
                                ii != null ? ii.refInfraCode() : null,
                                null, null, ii == null, m.getRole(), m.getCoefficient(), null, factors);
                    }
                    case ITEM -> {
                        // 표시 이름 = 참조 카드 이름, 보조 = "생산 · kWh"
                        OverviewItem ref = refItems.get(m.getRefItemId());
                        yield new MemberResponse(m.getMemberId(), m.getMemberType(), null, null, null, null,
                                ref != null ? ref.getLabel() : "카드 #" + m.getRefItemId(),
                                ref != null ? sectionShort(ref.getSection()) + (ref.getUnit() != null ? " · " + ref.getUnit() : "") : null,
                                null, null, ref == null, m.getRole(), m.getCoefficient(), m.getRefItemId(), factors);
                    }
                });
            }
            out.add(new ItemResponse(i.getItemId(), i.getSection(), i.getLabel(), i.getValueSource(), i.getPeriod(),
                    i.getUnit(), i.getScale(), i.getDecimals(), i.getSortOrder(), i.isActive(), members,
                    i.getCreatedAt(), i.getUpdatedAt()));
        }
        return out;
    }

    private static String sectionShort(Section s) {
        return switch (s) {
            case ENERGY_USAGE -> "사용";
            case ENERGY_PRODUCTION -> "생산";
            case INFRA_STATUS -> "통신";
            case ENERGY_INDEPENDENCE -> "자립률";
        };
    }
}
