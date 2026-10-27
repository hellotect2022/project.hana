package com.mpole.hana.digitaltwin.overview.entity;

/**
 * 종합 현황(overview) enum 모음 (2026-10-16). DB 에는 상수명 문자열로 저장한다.
 */
public final class OverviewEnums {

    private OverviewEnums() {
    }

    /** 클라이언트 종합 현황 패널의 영역 */
    public enum Section {
        /** 금일 에너지 사용 현황 */
        ENERGY_USAGE,
        /** 금일 에너지 생산 현황 */
        ENERGY_PRODUCTION,
        /** 건축설비 제어 통신 상태 현황 */
        INFRA_STATUS,
        /** 에너지 자립률 — Σ(분자 카드 값 × 계수) ÷ Σ(분모 카드 값 × 계수) × 100 (2026-10-23) */
        ENERGY_INDEPENDENCE
    }

    /** ENERGY_INDEPENDENCE 구성 대상의 역할 (2026-10-23) */
    public enum Role {
        /** 분자 — 생산(태양광·지열 등) */
        NUMERATOR,
        /** 분모 — 소비(전력·가스·지역난방 등) */
        DENOMINATOR
    }

    /** 값 출처 — 어느 테이블에서 값을 가져오는지 */
    public enum ValueSource {
        /** tbl_device_current_value 현재값 그대로(합) */
        CURRENT_VALUE,
        /** 현재값 − tbl_energy_snapshot 기간 시작 값(합, 음수는 0). 누적 포인트만 */
        SNAPSHOT_DELTA
    }

    /** SNAPSHOT_DELTA 기간 시작(KST) */
    public enum Period {
        /** 오늘 00:00 */
        TODAY,
        /** 이달 1일 00:00 */
        MONTH,
        /** 1월 1일 00:00 */
        YEAR
    }

    /** 구성 대상 종류 */
    public enum MemberType {
        /** 포인트 1개 */
        POINT,
        /** 소분류 장비 전체의 같은 태그 합산 */
        CATEGORY,
        /** 인프라 통신 상태 */
        INFRA,
        /** 다른 종합 현황 카드(금일 에너지 사용·생산 항목) 참조 — 에너지 자립률 전용 (2026-10-23) */
        ITEM
    }
}
