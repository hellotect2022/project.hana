package com.mpole.hana.digitaltwin.overview.service;

import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.ItemRequest;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.ItemResponse;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.MemberRequest;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.MemberResponse;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.OrderRequest;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.MemberType;
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

    @Transactional
    public void delete(Long itemId) {
        repository.delete(find(itemId));
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

    private void apply(OverviewItem item, ItemRequest req) {
        boolean infra = req.section() == Section.INFRA_STATUS;
        item.setSection(req.section());
        item.setLabel(req.label().trim());
        item.setValueSource(infra ? null : req.valueSource());
        item.setPeriod(infra || req.valueSource() == ValueSource.CURRENT_VALUE ? null : req.period());
        item.setUnit(infra || req.unit() == null || req.unit().isBlank() ? null : req.unit().trim());
        item.setScale(infra || req.scale() == null ? BigDecimal.ONE : req.scale());
        item.setDecimals(infra ? 0 : req.decimals() == null ? 1 : req.decimals());
        item.setActive(req.active() == null || req.active());

        List<OverviewItemMember> next = new ArrayList<>();
        for (MemberRequest m : req.members()) {
            OverviewItemMember mem = new OverviewItemMember();
            mem.setMemberType(m.memberType());
            switch (m.memberType()) {
                case POINT -> mem.setPointId(m.pointId());
                case CATEGORY -> {
                    mem.setCategoryId(m.categoryId());
                    mem.setTagName(m.tagName().trim());
                }
                case INFRA -> mem.setInfraId(m.infraId());
            }
            next.add(mem);
        }
        item.replaceMembers(next);
    }

    /* ───────────────────────── 응답(표시 정보 포함) ───────────────────────── */

    private List<ItemResponse> toResponses(List<OverviewItem> items) {
        Set<Long> pointIds = new HashSet<>();
        Set<Long> infraIds = new HashSet<>();
        items.forEach(i -> i.getMembers().forEach(m -> {
            if (m.getPointId() != null) pointIds.add(m.getPointId());
            if (m.getInfraId() != null) infraIds.add(m.getInfraId());
        }));
        Map<Long, OverviewLookup.PointInfo> points = lookup.pointInfo(pointIds);
        Map<Long, OverviewLookup.InfraInfo> infras = lookup.infraInfo(infraIds);
        Map<String, OverviewLookup.CategoryInfo> categoryCache = new HashMap<>();

        List<ItemResponse> out = new ArrayList<>(items.size());
        for (OverviewItem i : items) {
            List<MemberResponse> members = new ArrayList<>();
            for (OverviewItemMember m : i.getMembers()) {
                members.add(switch (m.getMemberType()) {
                    case POINT -> {
                        OverviewLookup.PointInfo pi = points.get(m.getPointId());
                        yield new MemberResponse(m.getMemberId(), m.getMemberType(), m.getPointId(), null, null, null,
                                pi != null ? pi.pointKey() : "포인트 #" + m.getPointId(),
                                pi != null ? pi.deviceName() : null,
                                pi != null ? pi.accumulate() : null,
                                null, pi == null);
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
                                !ci.exists() || !ci.hasTag());
                    }
                    case INFRA -> {
                        OverviewLookup.InfraInfo ii = infras.get(m.getInfraId());
                        yield new MemberResponse(m.getMemberId(), m.getMemberType(), null, null, null, m.getInfraId(),
                                ii != null ? ii.infraName() : "인프라 #" + m.getInfraId(),
                                ii != null ? ii.refInfraCode() : null,
                                null, null, ii == null);
                    }
                });
            }
            out.add(new ItemResponse(i.getItemId(), i.getSection(), i.getLabel(), i.getValueSource(), i.getPeriod(),
                    i.getUnit(), i.getScale(), i.getDecimals(), i.getSortOrder(), i.isActive(), members,
                    i.getCreatedAt(), i.getUpdatedAt()));
        }
        return out;
    }
}
