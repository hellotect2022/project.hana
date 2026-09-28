package com.mpole.hana.digitaltwin.external.common;

/**
 * 처리 단계 기록 창구 — 한 건의 처리가 여러 패키지를 거칠 때 <b>로그를 한 줄로 모으기</b> 위한 것.
 *
 * <p><b>왜 인터페이스인가</b> — 구현체({@code EmblazeTrace})는 emblaze 수신 쪽에 있는데,
 * 단계를 기록하는 쪽에는 HQS 전송처럼 다른 회사 패키지도 있다. 구현체를 직접 쓰면
 * {@code hqs → emblaze} 참조가 생겨 회사별로 가른 경계가 무너진다. 양쪽이 이 인터페이스를
 * 바라보면 의존이 {@code common} 으로 모여 방향 문제가 사라진다.</p>
 *
 * <p>{@code key()}/{@code flush()} 는 여기 두지 않는다 — 앞머리 구성과 출력 시점은
 * <b>메시지를 받은 쪽만</b> 결정한다. 중간 단계는 결과를 남길 뿐이다.</p>
 */
public interface StageTrace {

    /** 정상 통과한 단계. */
    StageTrace ok(String stage, Object detail);

    /** 처리를 건너뛴 단계 — 설정 누락, 대상 없음 등. 장애는 아니지만 기대한 동작이 일어나지 않았다. */
    StageTrace skip(String stage, String reason);

    /** 실패한 단계. */
    StageTrace fail(String stage, String reason);
}
