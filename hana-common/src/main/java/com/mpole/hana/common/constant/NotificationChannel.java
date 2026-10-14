package com.mpole.hana.common.constant;

/**
 * 이벤트 발생 시 실행되는 출력 채널.
 *
 * <p>규칙 레벨의 {@code outputs} 에서 켜진 채널이 발생 시 {@code tbl_event.outputs} 로 스냅샷되고,
 * 클라이언트가 그걸 보고 팝업·SOP·CCTV 를 연다. (채널별 상태 행 {@code tbl_event_action} 은 2026-10-02 폐기.)</p>
 *
 * <p>클라이언트(Web/Unity)는 이 값을 하드코딩하지 않고 메타 API 로 동기화한다.
 * 화면에 필요한 세 가지를 여기서 다 소유한다 — 제목({@code label}), 부연({@code description}),
 * 그리고 <b>지금 실제로 동작하는가</b>({@code ready}).</p>
 *
 * <p>{@code ready=false} 는 규칙에 켤 수는 있지만 서버 쪽 실행기가 아직 없다는 뜻이다
 * (outputs 에 기록만 되고 아무도 처리하지 않는다).
 * 프론트가 "준비중" 여부를 스스로 판단할 수 없어 서버가 알려준다.</p>
 */
public enum NotificationChannel {
    /** 팝업·토스트 알림. 표출로 역할이 끝난다(ack 없음). */
    POPUP("알람 팝업", "발생 즉시 화면에 표출", true),
    /** 대응 절차 — 서버는 템플릿만 공급하고 진행/완료는 클라이언트가 보고한다. */
    SOP("SOP(표준대응절차) 화면 전환", "SOP 절차 패널 표출 후 완료 보고", true),
    /** 발생 장비 주변 카메라 표출. */
    //CCTV("주변 CCTV", "발생 장비 주변 카메라 자동 선정", true),
    /** 문자 발송(서버 사이드). 규칙 저장은 되지만 발송기 미구현. */
    //SMS("문자 발송", "발송 연동 예정", false),
    /** 이메일 발송(서버 사이드). */
    //EMAIL("이메일 발송", "연동 예정", false),
    /**
     * 디바이스 제어 명령 실행(서버 사이드). 2026-10-12 실행기 구현 — 제어포인트별로 발생 시(ON_OCCUR)·확인 후(ON_ACK) 자동 실행.
     * event 서버가 digitaltwin 내부 API 를 불러 DeviceControlService(XN 제어)로 보낸다. 모의 이벤트는 실행하지 않고 로그만.
     */
    DEVICE_CONTROL("디바이스 제어", "발생 시·확인 후 제어포인트 명령 자동 실행", true)
    ;

    private final String label;
    private final String description;
    private final boolean ready;

    NotificationChannel(String label, String description, boolean ready) {
        this.label = label;
        this.description = description;
        this.ready = ready;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isReady() {
        return ready;
    }
}
