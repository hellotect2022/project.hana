package com.mpole.hana.digitaltwin.sop.enums;

/**
 * 의미색 토큰 (v2). 각 상수가 실제 hex(단일 고정 팔레트)를 소유 — theme 없음.
 * hex 는 /api/sop/meta 의 variantColors 로 노출되어 클라이언트(미리보기/Unity)가 동일 색을 쓴다.
 */
public enum Variant {
    PRIMARY("#3557a6"),   // 기본 강조
    SUCCESS("#1c8f74"),   // 정상/진행(teal)
    DANGER("#d83b3b"),    // 위험/경보(red)
    WARN("#d99a2b"),      // 주의(amber)
    INFO("#3f5ad6"),      // 정보(blue)
    NEUTRAL("#3f4a5e");   // 비활성/보조(gray)

    private final String hex;

    Variant(String hex) {
        this.hex = hex;
    }

    public String getHex() {
        return hex;
    }
}
