package com.mpole.hana.digitaltwin.location.service;

import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
import com.mpole.hana.digitaltwin.location.repository.UnityLocZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 구역 트리 해석 — "이 구역 아래 전부"를 구하는 곳.
 *
 * <p>{@code DeviceCategoryCascadeService} 와 같은 방식이다. 구역 수가 수백 규모라
 * 재귀 CTE 대신 <b>전체를 한 번 읽어 메모리에서 순회</b>한다 — 쿼리 1회로 끝나고
 * 호출부에서 트리 구조를 다룰 필요가 없다.</p>
 *
 * <p>왜 필요한가 — 화장실 구역을 선택했을 때 그 안의 부스 장비도 나와야 한다.
 * 카테고리 필터가 대분류를 고르면 하위 소분류 전체로 확장되는 것과 같은 기대다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneCascadeService {

    private final UnityLocZoneRepository zoneRepository;

    /**
     * 지정 구역과 그 하위 구역 전체의 id.
     *
     * @param rootZoneId null 이면 <b>null 반환</b>(= 필터 없음). 호출부는 "조건 미적용"으로 처리한다.
     * @return 자기 자신을 포함한 서브트리 id 목록. 존재하지 않는 구역이면 빈 목록(= 결과 0건)
     */
    @Transactional(readOnly = true)
    public List<Long> resolveSubtreeZoneIds(Long rootZoneId) {
        if (rootZoneId == null) {
            return null;
        }
        Map<Long, List<Long>> childrenByParent = loadChildIndex();
        if (!zoneRepository.existsById(rootZoneId)) {
            return List.of();
        }
        return collectSubtree(rootZoneId, childrenByParent);
    }

    /**
     * 여러 구역의 서브트리 합집합. 화면에서 구역을 복수 선택할 때 쓴다.
     */
    @Transactional(readOnly = true)
    public List<Long> resolveSubtreeZoneIds(List<Long> rootZoneIds) {
        if (rootZoneIds == null || rootZoneIds.isEmpty()) {
            return null;
        }
        Map<Long, List<Long>> childrenByParent = loadChildIndex();
        Set<Long> all = new LinkedHashSet<>();
        for (Long root : rootZoneIds) {
            all.addAll(collectSubtree(root, childrenByParent));
        }
        return new ArrayList<>(all);
    }

    /**
     * {@code parent} 를 {@code zoneId} 의 상위로 지정해도 되는지 검사한다.
     *
     * <p>DB 의 {@code ck_zone_parent_not_self} 는 자기 자신만 막는다. A→B→A 같은 순환은
     * CHECK 로 표현할 수 없어 여기서 막는다 — <b>대상의 후손을 부모로 지정</b>하려는 경우다.</p>
     *
     * @throws IllegalArgumentException 순환이 생기거나 depth 상한을 넘길 때
     */
    @Transactional(readOnly = true)
    public void assertParentAssignable(Long zoneId, UnityLocZone parent) {
        if (parent == null) {
            return;
        }
        if (zoneId != null && zoneId.equals(parent.getZoneId())) {
            throw new IllegalArgumentException("자기 자신을 상위 구역으로 지정할 수 없습니다.");
        }
        if (parent.getDepth() + 1 > UnityLocZone.MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "구역 계층은 최대 " + (UnityLocZone.MAX_DEPTH + 1) + "단까지입니다. "
                            + "선택한 상위 구역이 이미 depth=" + parent.getDepth() + " 입니다.");
        }
        if (zoneId == null) {
            return; // 신규 등록은 후손이 있을 수 없다
        }
        // 부모가 대상의 후손이면 순환이 된다.
        List<Long> subtree = collectSubtree(zoneId, loadChildIndex());
        if (subtree.contains(parent.getZoneId())) {
            throw new IllegalArgumentException(
                    "하위 구역을 상위 구역으로 지정할 수 없습니다(순환 참조). zoneId=" + zoneId);
        }
    }

    /* ──────────────────────────────────────────────────────────── */

    /** parentId → 자식 id 목록. 구역 전체를 한 번만 읽는다. */
    private Map<Long, List<Long>> loadChildIndex() {
        Map<Long, List<Long>> index = new HashMap<>();
        for (Object[] row : zoneRepository.findAllIdAndParentId()) {
            Long id = ((Number) row[0]).longValue();
            Long parentId = row[1] == null ? null : ((Number) row[1]).longValue();
            if (parentId != null) {
                index.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
            }
        }
        return index;
    }

    /** BFS — 자기 자신 포함. 순환 데이터가 있어도 방문 집합으로 무한루프를 막는다. */
    private List<Long> collectSubtree(Long rootId, Map<Long, List<Long>> childrenByParent) {
        Set<Long> visited = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            List<Long> children = childrenByParent.get(current);
            if (children != null) {
                queue.addAll(children);
            }
        }
        return new ArrayList<>(visited);
    }
}
