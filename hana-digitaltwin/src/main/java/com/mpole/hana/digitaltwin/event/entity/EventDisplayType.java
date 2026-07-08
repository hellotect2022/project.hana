package com.mpole.hana.digitaltwin.event.entity;

public enum EventDisplayType {
    /** 팝업만 표시(단순). */
    SIMPLE,
    /** SOP 표시. */
    SOP,
    /** 팝업 + SOP 동시 표시(알람 정책 경보 표시용). */
    POPUP_SOP
}
