package com.mpole.hana.common.dto;

/**
 * 이벤트 출력 채널(알림 방식). 레벨별로 조합해서 켤 수 있다.
 *
 * <p>표시 채널(NOTIFY/SOP/CCTV)은 클라이언트(Unity)가 렌더하고,
 * 서버 채널(SMS/BROADCAST/EMAIL)은 백엔드가 발송한다. 모듈 간 계약이라 hana-common 에 둔다.
 * 각 상수가 표시용 설명을 소유(클라이언트 메타 동기화용).</p>
 */
public enum OutputChannel {
    POPUP("POPUP", true),        // 기본 팝업/토스트 (클라이언트 표시)
    SOP("SOP", true),           // SOP 절차 패널 (클라이언트 표시)
    CCTV("CCTV", true),         // 주변 CCTV 표출 (클라이언트 표시)
    SMS("SMS(준비중)", false),   // 문자 발송 (서버 사이드, 슬롯)
    BROADCAST("방송(준비중)", false),  // 방송/텔레폰페이징 (서버 사이드, 슬롯)
    EMAIL("이메일(준비중)", false);    // 이메일 (서버 사이드, 슬롯)

    private final String description;
    /** true=클라이언트 표시 채널(Unity 렌더), false=서버 사이드 발송 채널. */
    private final boolean display;

    OutputChannel(String description, boolean display) {
        this.description = description;
        this.display = display;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDisplay() {
        return display;
    }
}
