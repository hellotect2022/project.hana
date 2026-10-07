package com.mpole.hana.event.pipeline.event;

import com.mpole.hana.event.pipeline.event.dto.CategoryRow;
import com.mpole.hana.event.pipeline.event.dto.RuleLevelRow;
import com.mpole.hana.event.pipeline.event.dto.RuleIndex;
import com.mpole.hana.event.pipeline.event.dto.RuleRow;
import com.mpole.hana.event.pipeline.event.dto.RuleView;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 이벤트 규칙 인메모리 캐시.
 *
 * <p>{@link RuleIndex} 불변 스냅샷을 만들어 volatile 참조로 통째 교체한다
 * (ConcurrentHashMap 부분 갱신을 지양하여 평가 중 부분 읽기 불일치를 방지).</p>
 *
 * <p>리프레시 주기({@code hdt.event.rule-refresh-ms})가 곧 규칙 변경의 최대 반영 지연이다.
 * 예: digitaltwin 에서 규칙을 수정/비활성화해도 다음 리프레시까지는 이전 인덱스로 평가된다.</p>
 *
 * <p>리프레시 실패 시 이전 스냅샷을 그대로 유지한다(다음 주기 재시도).</p>
 *
 * <h3>동작 요약</h3>
 * <pre>
 * loadOnStartup() ─ 기동 시 1회 refresh().block()   (초기 로드, gateway 골격 적재와 같은 방식)
 * refreshRules()  ─ @Scheduled 30초마다 refresh()      (eventTaskScheduler 전용 스레드, 실패해도 다음 주기 재시도)
 *
 * refresh() ─ 5개 쿼리 동시 로드(Mono.zip)
 *              ├ 활성 규칙 · 레벨(규칙별) · device→category · 카테고리 트리 · 장비 알람 스위치
 *           → buildIndex()로 불변 RuleIndex 생성 → volatile 통째 교체
 * </pre>
 *
 * <h3>왜 인덱스를 미리 만드나</h3>
 * 고volume 스트림에서 포인트마다 DB를 조회하면 감당 불가. 그래서 "포인트로 규칙을 즉시 찾는"
 * 역인덱스를 메모리에 올려둔다. 평가기는 포인트 (deviceId, tagName, value) 로 다음을 O(1) 조회한다:
 * <ol>
 *   <li>deviceCategoryMap[deviceId] → 소분류 categoryId</li>
 *   <li>categoryIndex["C|{소분류}|{tag}"] → 카테고리 규칙들</li>
 *   <li>deviceIndex["D|{deviceId}|{tag}"] → 개별 규칙들</li>
 * </ol>
 *
 * <h3>구체적 예시</h3>
 * <pre>
 * 카테고리 트리:
 *   기계설비(id=1, depth0)
 *    └ 공조(id=10, depth1)
 *       ├ 냉동기(id=100, depth2=소분류)
 *       └ AHU  (id=101, depth2=소분류)
 * 디바이스: Chiller-01(deviceId=5001, category_id=100), AHU-01(deviceId=5003, category_id=101)
 *
 * 규칙: "고온경보" scope=CATEGORY, category_id=10(공조=중분류), tag="TEMP",
 *       conditionKind=THRESHOLD, operator=GTE, levels: 주의≥10 / 경계≥11 / 심각≥12
 *
 * ── buildIndex() 결과 ──────────────────────────────────────────
 *  expandToSmallCategories(10) : 공조(10) DFS → 하위 소분류 {100, 101}
 *      (중분류 규칙 1건이 하위 소분류 2개로 전개됨)
 *  categoryIndex:
 *      "C|100|TEMP" → [고온경보]
 *      "C|101|TEMP" → [고온경보]
 *  deviceCategoryMap: {5001→100, 5003→101}
 *
 * ── 이후 포인트 (deviceId=5001, tag="TEMP", value=12.5) 평가 ──────
 *  1) deviceCategoryMap[5001] = 100
 *  2) categoryIndex["C|100|TEMP"] → [고온경보]  (매칭)
 *  3) THRESHOLD GTE: 12.5 ≥ 12 → 심각(CRITICAL) 발생
 * </pre>
 * → 중분류에 규칙 1건만 걸어도 하위 냉동기·AHU 디바이스 전체에 자동 적용된다
 *   (장비별 개별 설정 불필요). 이 "전개"가 {@link #expandToSmallCategories}의 역할.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventRuleCache {

    /** 사이클 blocking 상한 — 초과 시 예외로 끊어 스케줄러 스레드 점유를 막는다. */
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    /** 소분류 depth (설계 기준: 대분류 0 / 중분류 1 / 소분류 2). */
    private static final int SMALL_CATEGORY_DEPTH = 2;

    private final EventRuleReader reader;

    /** 평가 시 읽는 현재 인덱스. 초기값은 안전한 빈 인덱스. */
    private volatile RuleIndex index = RuleIndex.empty();

    public RuleIndex current() {
        return index;
    }

    /**
     * 기동 시 규칙 인덱스 초기 적재(블로킹). 실패하면 기동을 실패시키는 편이 낫다 —
     * 빈 인덱스로 뜨면 어떤 포인트도 매칭되지 않은 채 조용히 도는 상태가 된다.
     */
    @PostConstruct
    public void loadOnStartup() {
        refresh().block();
        log.info("[EventRuleCache] 초기 규칙 로드 완료");
    }

    /**
     * 주기 리프레시 — digitaltwin 에서 규칙을 바꿔도 재기동 없이 반영된다.
     * 실패하면 이전 스냅샷을 그대로 유지한다(다음 주기 재시도).
     */
    @Scheduled(
            fixedDelayString = "${hdt.event.rule-refresh-ms:30000}",
            initialDelayString = "${hdt.event.rule-refresh-ms:30000}",
            scheduler = "eventTaskScheduler"
    )
    public void refreshRules() {
        try {
            refresh().block(BLOCK_TIMEOUT);
        } catch (Exception e) {
            log.error("[EventRuleCache] 주기 리프레시 실패(이전 스냅샷 유지): {}", e.getMessage());
        }
    }

    /**
     * 규칙/레벨/디바이스맵/카테고리트리를 모두 로드해 새 RuleIndex 를 만들고 참조를 스왑한다.
     */
    private Mono<Void> refresh() {
        Mono<List<RuleRow>> rulesMono = reader.loadActiveRules().collectList();
        Mono<Map<Long, List<RuleLevelRow>>> levelsMono = reader.loadLevelsByRuleId();
        Mono<Map<Long, Long>> deviceCatMono = reader.loadDeviceCategoryMap();
        Mono<List<CategoryRow>> treeMono = reader.loadCategoryTree().collectList();
        Mono<Map<Long, Boolean>> alarmEnabledMono = reader.loadDeviceAlarmEnabled();

        // Mono.zip: 5개 쿼리를 병렬로 실행하고 모두 완료되면 하나로 합친다.
        return Mono.zip(rulesMono, levelsMono, deviceCatMono, treeMono, alarmEnabledMono)
                .map(t -> buildIndex(t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5()))
                .doOnNext(newIndex -> {
                    // 원자적 publish: buildIndex 가 완성한 불변 인덱스를 volatile 한 번에 대입.
                    // 평가 스레드는 항상 "이전 완성본" 또는 "새 완성본"만 보고, 반쯤 만들어진 상태는 못 본다.
                    this.index = newIndex;
                    log.info("[EventRuleCache] 인덱스 스왑 완료. category-keys={}, device-keys={}, device-map={}",
                            newIndex.categoryIndex().size(),
                            newIndex.deviceIndex().size(),
                            newIndex.deviceCategoryMap().size());
                })
                .then();
    }

    /**
     * 로드된 원본으로 불변 인덱스를 구성한다.
     */
    private RuleIndex buildIndex(List<RuleRow> rules,
                                 Map<Long, List<RuleLevelRow>> levelsByRule,
                                 Map<Long, Long> deviceCategoryMap,
                                 List<CategoryRow> categories,
                                 Map<Long, Boolean> deviceAlarmEnabled) {

        // ── 1단계: 카테고리 트리를 조회용 자료구조로 변환 ──
        // childrenByParent: 부모 id → 자식 id 목록 (서브트리 DFS 전개에 사용)
        // depthById: id → depth (소분류=2 판별에 사용)
        // 예) 공조(10)→[냉동기(100), AHU(101)], depthById{1:0, 10:1, 100:2, 101:2}
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        Map<Long, Integer> depthById = new HashMap<>();
        for (CategoryRow c : categories) {
            if (c.categoryId() == null) continue;
            depthById.put(c.categoryId(), c.depth());
            if (c.parentId() != null) {
                childrenByParent.computeIfAbsent(c.parentId(), k -> new ArrayList<>()).add(c.categoryId());
            }
        }

        // ── 2단계: 규칙마다 역인덱스 등록 ──
        // categoryIndex 키 "C|{소분류}|{tag}", deviceIndex 키 "D|{deviceId}|{tag}"
        Map<String, List<RuleView>> categoryIndex = new HashMap<>();
        Map<String, List<RuleView>> deviceIndex = new HashMap<>();

        for (RuleRow r : rules) {
            // 규칙 + 해당 규칙의 레벨들을 하나의 조회용 뷰로 결합.
            RuleView view = toView(r, levelsByRule.getOrDefault(r.ruleId(), List.of()));

            if (view.tagName() == null || view.tagName().isBlank()) {
                // tagName 없는 규칙은 어떤 포인트와도 매칭 불가 — 스킵.
                continue;
            }

            if ("CATEGORY".equals(r.scope())) {
                if (r.categoryId() == null) continue;
                // 규칙 카테고리(대/중/소 무엇이든)를 하위 소분류(depth=2) 집합으로 전개.
                // 예) category_id=10(공조) → {100, 101}
                Set<Long> smallCats = expandToSmallCategories(r.categoryId(), childrenByParent, depthById);
                for (Long smallCatId : smallCats) {
                    // 각 소분류마다 "C|{소분류}|{tag}" 키로 규칙 등록.
                    // 예) "C|100|TEMP" → [고온경보], "C|101|TEMP" → [고온경보]
                    categoryIndex
                            .computeIfAbsent(RuleIndex.categoryKey(smallCatId, view.tagName()), k -> new ArrayList<>())
                            .add(view);
                }
            } else if ("DEVICE".equals(r.scope())) {
                if (r.deviceId() == null) continue;
                // 개별 디바이스 규칙: "D|{deviceId}|{tag}" 키로 등록.
                deviceIndex
                        .computeIfAbsent(RuleIndex.deviceKey(r.deviceId(), view.tagName()), k -> new ArrayList<>())
                        .add(view);
            } else {
                log.warn("[EventRuleCache] 알 수 없는 scope 규칙 스킵. ruleId={}, scope={}", r.ruleId(), r.scope());
            }
        }

        return new RuleIndex(
                Map.copyOf(categoryIndex),
                Map.copyOf(deviceIndex),
                Map.copyOf(deviceCategoryMap),
                Map.copyOf(deviceAlarmEnabled)
        );
    }

    /**
     * 주어진 카테고리를 루트로 하는 서브트리에서 depth=소분류(2) 노드 집합을 전개한다(DFS).
     * 규칙 카테고리 자체가 이미 소분류면 그 자신만 반환한다.
     *
     * <p>예) rootCategoryId=10(공조, depth1) 이고 트리가 공조→{냉동기(100), AHU(101)} 이면:
     * 스택 [10] → 10 pop(depth1, 소분류 아님) → 자식 100,101 push →
     * 101 pop(depth2, result에 추가) → 100 pop(depth2, result에 추가) → 결과 {100, 101}.</p>
     *
     * <p>rootCategoryId=100(냉동기, 이미 소분류)이면 result={100}(자기 자신만).</p>
     */
    private Set<Long> expandToSmallCategories(Long rootCategoryId,
                                              Map<Long, List<Long>> childrenByParent,
                                              Map<Long, Integer> depthById) {
        Set<Long> result = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootCategoryId);
        Set<Long> visited = new HashSet<>();   // 순환 방어(잘못된 트리로 무한루프 방지)

        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (!visited.add(cur)) continue;   // 이미 방문한 노드면 스킵

            Integer depth = depthById.get(cur);
            if (depth != null && depth == SMALL_CATEGORY_DEPTH) {
                result.add(cur);
            }
            List<Long> children = childrenByParent.get(cur);
            if (children != null) {
                for (Long child : children) stack.push(child);
            }
        }

        // depth 정보가 전혀 없거나 트리 전개로 소분류를 못 찾은 경우:
        // 규칙 카테고리 자체를 키로 사용(방어). 이 경우 device 의 category_id 와 직접 매칭.
        if (result.isEmpty()) {
            result.add(rootCategoryId);
        }
        return result;
    }

    private RuleView toView(RuleRow r, List<RuleLevelRow> levels) {
        List<RuleView.RuleLevelView> levelViews = levels.stream()
                .map(l -> new RuleView.RuleLevelView(
                        l.level(), l.thresholdValue(), l.triggerValue(), l.icon(), l.effect(), l.displayOrder(), l.outputs()))
                .toList();

        return new RuleView(
                r.ruleId(),
                r.ruleName(),
                r.eventCode(),
                r.scope(),
                r.tagName(),
                r.pointType(),
                r.operator(),
                r.sustainSec(),
                r.deadband(),
                r.suppressMode(),
                levelViews
        );
    }
}
