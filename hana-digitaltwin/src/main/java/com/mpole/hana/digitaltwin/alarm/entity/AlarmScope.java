package com.mpole.hana.digitaltwin.alarm.entity;

/**
 * 알람 정책 적용 범위.
 * - CATEGORY: 특정 카테고리(소분류) 소속 장비 전체
 * - DEVICE: 특정 단일 장비
 */
public enum AlarmScope {
    CATEGORY,
    DEVICE
}
