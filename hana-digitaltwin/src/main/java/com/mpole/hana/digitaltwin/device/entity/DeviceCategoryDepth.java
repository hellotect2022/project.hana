package com.mpole.hana.digitaltwin.device.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeviceCategoryDepth {
    LARGE(0, "대분류"),
    MEDIUM(1, "중분류"),
    SMALL(2, "소분류");

    public static final int MAX_DEPTH = SMALL.depth;
    private final int depth;

    private final String label;

    public static DeviceCategoryDepth fromDepth(int depth) {
        for (DeviceCategoryDepth d : values()) {
            if (d.depth == depth) {
                return d;
            }
        }
        throw new IllegalArgumentException("허용되지 않는 depth입니다. depth=" + depth);
    }

    public DeviceCategoryDepth child() {
        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException("소분류 아래에는 카테고리를 만들 수 없습니다.");
        }
        return fromDepth(depth + 1);
    }
}
