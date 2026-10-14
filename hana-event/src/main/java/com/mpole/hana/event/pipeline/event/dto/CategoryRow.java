package com.mpole.hana.event.pipeline.event.dto;

/**
 * 카테고리 트리 노드. CATEGORY 규칙을 하위 소분류로 전개할 때 쓴다.
 * depth: 대분류(0) / 중분류(1) / 소분류(2) — 설계 기준.
 */
public record CategoryRow(
        Long categoryId,
        Long parentId,
        Integer depth
) {
}
