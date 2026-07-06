package com.mpole.hana.event.alarm;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 알람 평가용 불변 인덱스 스냅샷.
 *
 * <p>{@link AlarmPolicyCache} 가 주기적으로 새 인스턴스를 만들어 volatile 참조로 통째 교체한다
 * (부분 갱신으로 인한 읽기 불일치 방지). 필드는 모두 불변 맵으로 보관한다.</p>
 *
 * <ul>
 *   <li>categoryIndex : key "C|{소분류categoryId}|{tagName}" → 매칭 CATEGORY 정책 목록.
 *       CATEGORY 정책의 category_id 는 카테고리 트리에서 하위 소분류(depth=2) 집합으로 전개되어
 *       각 소분류 키에 등록된다.</li>
 *   <li>deviceIndex : key "D|{deviceId}|{tagName}" → 매칭 DEVICE 정책 목록.</li>
 *   <li>deviceCategoryMap : deviceId → (소분류)categoryId. 포인트의 deviceId 로 소분류를 찾아
 *       categoryIndex 조회에 사용.</li>
 * </ul>
 */
public record PolicyIndex(
        Map<String, List<PolicyView>> categoryIndex,
        Map<String, List<PolicyView>> deviceIndex,
        Map<Long, Long> deviceCategoryMap
) {

    private static final PolicyIndex EMPTY =
            new PolicyIndex(Map.of(), Map.of(), Map.of());

    /**
     * 초기 로드 이전(캐시 미준비) 안전값. 매칭 0 을 보장한다.
     */
    public static PolicyIndex empty() {
        return EMPTY;
    }

    public static String categoryKey(Long smallCategoryId, String tagName) {
        return "C|" + smallCategoryId + "|" + tagName;
    }

    public static String deviceKey(Long deviceId, String tagName) {
        return "D|" + deviceId + "|" + tagName;
    }

    public List<PolicyView> categoryPolicies(Long smallCategoryId, String tagName) {
        return categoryIndex.getOrDefault(categoryKey(smallCategoryId, tagName), Collections.emptyList());
    }

    public List<PolicyView> devicePolicies(Long deviceId, String tagName) {
        return deviceIndex.getOrDefault(deviceKey(deviceId, tagName), Collections.emptyList());
    }

    public Long smallCategoryOf(Long deviceId) {
        return deviceCategoryMap.get(deviceId);
    }
}
