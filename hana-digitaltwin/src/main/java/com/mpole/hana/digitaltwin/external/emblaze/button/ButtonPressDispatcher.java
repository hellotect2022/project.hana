package com.mpole.hana.digitaltwin.external.emblaze.button;

import com.mpole.hana.digitaltwin.external.emblaze.amqp.EmblazeTrace;
import com.mpole.hana.digitaltwin.external.hqs.config.HqsAsyncConfig;
import com.mpole.hana.digitaltwin.external.hqs.outbound.WcButtonPress;
import com.mpole.hana.digitaltwin.external.hqs.outbound.WcButtonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * HQS 전송을 리스너 스레드에서 떼어내는 경계.
 *
 * <p>{@code space} 큐가 재실 판정을 executor 로 넘기는 것과 같은 모양이다. 다른 점은
 * 넘기는 일이 <b>외부 HTTP</b>라 훨씬 오래 걸린다는 것이다 — 그래서 큐가 유한해야 한다
 * ({@link HqsAsyncConfig} 참고).</p>
 *
 * <h3>왜 클래스를 따로 두나</h3>
 * <p>{@code @Async} 는 프록시 기반이라 <b>같은 빈 안에서 부르면 동작하지 않는다</b>.
 * {@link ButtonPressHandler} 가 자기 메서드를 부르면 그냥 동기 호출이 된다.</p>
 *
 * <h3>왜 hqs 가 아니라 여기인가</h3>
 * <p>{@code @Async} 를 {@code WcButtonService.push()} 에 붙이면 <b>trace 를 누가 flush 할지</b>가
 * 꼬인다. {@code StageTrace} 에 일부러 {@code flush()} 를 두지 않았기 때문이다 — 출력 시점은
 * 메시지를 받은 쪽만 안다. 비동기로 넘기는 결정도 같은 이유로 수신 쪽 일이다.
 * hqs 의 {@code push()} 는 동기 그대로 두고, 스레딩을 모르게 한다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ButtonPressDispatcher {

    private final WcButtonService wcButtonService;

    /**
     * 전송 스레드에서 HQS 로 보내고 <b>그 결과까지 담아 한 줄로 찍는다</b>.
     *
     * <p>{@code finally} 에서 flush 하므로 전송이 어떻게 끝나든 로그 한 줄은 반드시 남는다.
     * 호출부(리스너)는 이 시점에 이미 반환했으므로 여기서 찍지 않으면 아무도 안 찍는다.</p>
     *
     * <p>큐가 차면 이 메서드는 <b>실행되지 않고</b> 호출부에서
     * {@code RejectedExecutionException} 이 던져진다 — 그쪽이 사유를 남긴다.</p>
     */
    @Async(HqsAsyncConfig.HQS_OUTBOUND_EXECUTOR)
    public void dispatch(WcButtonPress press, EmblazeTrace trace) {
        try {
            wcButtonService.push(press, trace);
        } finally {
            trace.flush(log);
        }
    }
}
