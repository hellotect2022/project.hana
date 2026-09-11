package com.mpole.hana.digitaltwin.event.entity;

import lombok.Getter;

/**
 * 이벤트 규칙 적용 범위.
 * - CATEGORY: 특정 카테고리(소분류) 소속 장비 전체
 * - DEVICE: 특정 단일 장비
 *
 * <p>DB 에는 상수명 문자열(CATEGORY/DEVICE)로 저장된다 — 클래스명이 바뀌어도 데이터는 그대로다.</p>
 */
@Getter
public enum RuleScope {
    CATEGORY("카테고리"),
    DEVICE("디바이스");

    private final String description;

    RuleScope(String description) {
        this.description = description;
    }

}
