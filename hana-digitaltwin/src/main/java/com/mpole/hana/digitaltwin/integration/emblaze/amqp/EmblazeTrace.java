package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

import org.slf4j.Logger;

/**
 * emblaze 메시지 1건의 처리 과정을 모아 <b>로그 한 줄</b>로 내보낸다.
 *
 * <h3>왜 필요한가</h3>
 * <p>버튼 누름 하나가 리스너 → {@code WcButtonService} → {@code HqsClient} 세 클래스에 걸쳐 있어
 * 로그가 최대 6줄로 흩어진다. 게다가 중간에 {@code return} 으로 빠지면 <b>그 지점 로그만</b> 남아,
 * 뒤 단계가 "실패한 건지 아예 안 탄 건지" 구분이 안 된다.</p>
 *
 * <p>여기에 단계별 결과를 쌓아두고 {@code finally} 에서 한 번 {@link #flush(Logger)} 하면,
 * 어디서 끝났든 <b>반드시 한 줄이 남는다.</b></p>
 *
 * <pre>
 *   [ENOCEAN] uuid=776ef4d2-… idx=8821 sw=2 | dedup=PASS | type=CLEAN
 *             | map=WC_4F_M01_B01/4F | point=2 | hqs=OK(IF000) trx=3f2b… | cnt=12 | 84ms
 *
 *   [ENOCEAN] uuid=aaaa1111-… idx=91 sw=2 | dedup=PASS | type=CLEAN
 *             | map=실패(미등록 uuid — tbl_device_point.ref_device_code 없음) | 3ms
 * </pre>
 *
 * <h3>로그 레벨</h3>
 * <p>단계 결과에 따라 <b>자동으로 올라간다</b> — 정상 INFO, 건너뜀 WARN, 실패 ERROR.
 * 한 줄 안에 최종 수준이 반영되므로 {@code grep ERROR} 로 실패만 뽑을 수 있다.</p>
 *
 * <p><b>스레드</b>: 메시지 1건에 하나씩 만들어 그 처리 흐름에서만 쓴다(공유하지 않는다).
 * {@code space} 는 재실 판정이 단일 스레드 executor 로 넘어가므로 trace 도 함께 넘겨
 * <b>그쪽에서 flush 한다</b> — 그래야 판정 결과까지 한 줄에 담긴다.</p>
 */
public final class EmblazeTrace {

    private static final int LEVEL_INFO = 0;
    private static final int LEVEL_WARN = 1;
    private static final int LEVEL_ERROR = 2;

    private final String queue;
    private final StringBuilder head = new StringBuilder();
    private final StringBuilder stages = new StringBuilder();
    private final long startNanos = System.nanoTime();

    private int level = LEVEL_INFO;
    private boolean flushed;

    private EmblazeTrace(String queue) {
        this.queue = queue;
    }

    /** 메시지 수신 시점에 만든다. {@code uuid} 가 없으면 {@code ?} 로 남겨 그 자체가 단서가 된다. */
    public static EmblazeTrace start(String queue, String deviceUuid) {
        EmblazeTrace t = new EmblazeTrace(queue);
        t.head.append("uuid=").append(deviceUuid == null ? "?" : deviceUuid);
        return t;
    }

    /** 앞머리에 식별값을 덧붙인다(idx, sw 등). 단계가 아니라 "이 메시지가 무엇인가" 쪽이다. */
    public EmblazeTrace key(String name, Object value) {
        head.append(' ').append(name).append('=').append(value);
        return this;
    }

    /** 정상 통과한 단계. */
    public EmblazeTrace ok(String stage, Object detail) {
        return append(stage, detail);
    }

    /**
     * 처리를 건너뛴 단계 — 설정 누락, 대상 없음 등. <b>레벨이 WARN 으로 올라간다.</b>
     * <p>장애는 아니지만 기대한 동작이 일어나지 않은 경우다.</p>
     */
    public EmblazeTrace skip(String stage, String reason) {
        raise(LEVEL_WARN);
        return append(stage, "생략(" + reason + ")");
    }

    /** 실패한 단계. <b>레벨이 ERROR 로 올라간다.</b> */
    public EmblazeTrace fail(String stage, String reason) {
        raise(LEVEL_ERROR);
        return append(stage, "실패(" + reason + ")");
    }

    /**
     * 한 줄로 출력한다. <b>두 번 호출해도 한 번만 나간다</b> —
     * 비동기로 넘긴 경우 양쪽에서 flush 가 불려도 중복되지 않게 한다.
     */
    public void flush(Logger log) {
        if (flushed) {
            return;
        }
        flushed = true;

        String message = "[" + queue + "] " + head + stages
                + " | " + ((System.nanoTime() - startNanos) / 1_000_000L) + "ms";

        switch (level) {
            case LEVEL_ERROR -> log.error(message);
            case LEVEL_WARN -> log.warn(message);
            default -> log.info(message);
        }
    }

    private EmblazeTrace append(String stage, Object detail) {
        stages.append(" | ").append(stage);
        if (detail != null) {
            stages.append('=').append(detail);
        }
        return this;
    }

    private void raise(int candidate) {
        if (candidate > level) {
            level = candidate;
        }
    }
}
