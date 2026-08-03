package com.mpole.hana.digitaltwin.event.entity;

public enum EventDisplayType {
    /** 팝업만 표시(단순). */
    SIMPLE("팝업"),
    /** SOP 표시. */
    SOP("SOP"),
    /** 팝업 + SOP 동시 표시(알람 정책 경보 표시용). */
    POPUP_SOP("팝업+SOP");

    private final String description;

    EventDisplayType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
