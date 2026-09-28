package com.mpole.hana.digitaltwin.external.emblaze.button;

import com.mpole.hana.digitaltwin.external.emblaze.amqp.EmblazeTrace;
import com.mpole.hana.digitaltwin.external.emblaze.mapping.AmqpPointMappingCache;
import com.mpole.hana.digitaltwin.external.emblaze.mapping.AmqpPointTarget;
import com.mpole.hana.digitaltwin.external.hqs.outbound.WcButtonPress;
import com.mpole.hana.digitaltwin.external.hqs.outbound.WcButtonType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.RejectedExecutionException;

/**
 * 물리버튼 누름 1건의 흐름 제어 — <b>벤더 식별자를 우리 식별자로 바꾸고 할 일들을 부른다.</b>
 *
 * <pre>
 *   RabbitMQ enocean(device_uuid, switch_number, index, timestamp)
 *     → EmblazeMonitoringListeners.onEnocean
 *         ① EnOceanIndexDeduplicator  중복 수신 제거(같은 index = 같은 이벤트)
 *         ② EmblazeStatusRouter       switch_number·index 원본을 TB 장비에 기록
 *         ③ handle                    ← 여기
 *              → resolve(uuid, "switch_number")  어느 부스인지
 *              → ButtonPressDispatcher.dispatch  HQS 전송        (전송 스레드로 위임)
 * </pre>
 *
 * <p>요청 종류별 누적 집계는 여기서 하지 않는다. 눌림을 Kafka 로 event 서버에 넘겨
 * 거기서 기록하는 쪽으로 설계가 잡혀 있어, 현재값에 직접 카운트를 쓰던 경로를 걷어냈다.</p>
 *
 * <h3>리스너 스레드는 HQS 를 기다리지 않는다</h3>
 * <p>enocean 컨슈머는 1개뿐이라, 여기서 외부 HTTP 를 기다리면 <b>그동안 다음 버튼 메시지를
 * 손도 못 댄다</b> — POST 만 밀리는 게 아니라 그 큐의 수신 전체가 멈춘다.
 * 전송은 {@link ButtonPressDispatcher} 로 넘기고 즉시 반환한다.</p>
 *
 * <h3>왜 이 클래스가 생겼나</h3>
 * <p>예전에는 이 해석까지 {@code WcButtonService}(HQS 전송)가 했다. 그래서 HQS 전송 코드가
 * {@code AmqpPointMappingCache}·{@code AmqpPointTarget} 을 알아야 했고 회사별 패키지 경계가
 * {@code hqs → emblaze} 로 뒤집혔다. <b>벤더 식별자를 아는 것은 수신 쪽 일</b>이므로
 * 여기로 옮겼다. 경계를 넘어가는 것은 {@link WcButtonPress}(식별자 몇 개)뿐이다.</p>
 *
 * <h3>DB 를 치지 않는다</h3>
 * <p>부스 신원({@code groupCode}·{@code wcCode}·{@code gender}·{@code floorName})은
 * {@link AmqpPointMappingCache} 가 기동 시 한 번 읽어 둔 것을 그대로 쓴다. 버튼은 사람이
 * 누르는 만큼 들어오므로, 누를 때마다 조회하면 그 빈도만큼 쿼리가 나간다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ButtonPressHandler {

    /**
     * 버튼 장비를 찾을 때 쓰는 벤더 필드명.
     * <p>uuid 하나에 장비가 3대 걸리므로 uuid 만으로는 특정할 수 없다 —
     * enocean 큐의 이 필드가 물리버튼(TB) 장비를 가리킨다.</p>
     */
    private static final String FIELD_SWITCH = "switch_number";

    private final AmqpPointMappingCache amqpPointMappingCache;
    private final ButtonPressDispatcher buttonPressDispatcher;

    /**
     * 버튼 누름 1건 처리.
     *
     * @param deviceUuid   emblaze 조명제어기 uuid
     * @param switchNumber 눌린 스위치 번호(0·1=점검요청, 2·3=청소요청)
     * @param pressedAt    센서 타임스탬프
     * @return 전송을 비동기로 넘겼는지. <b>{@code true} 면 호출부는 trace 를 flush 하지 않는다</b>
     *         — 전송 스레드가 결과까지 담아 찍는다. {@code false} 면 여기서 끝난 것이므로
     *         호출부가 찍어야 한다.
     */
    public boolean handle(String deviceUuid, String switchNumber, OffsetDateTime pressedAt,
                          EmblazeTrace trace) {
        WcButtonType type = WcButtonType.fromSwitchNumber(switchNumber);
        if (type == null) {
            trace.fail("type", "정의되지 않은 switch_number");
            return false;
        }
        trace.ok("type", type);

        // map= 단계는 EmblazeStatusRouter 가 이미 남겼다(세 큐 공통). 여기서 또 찍지 않는다.
        AmqpPointTarget target = amqpPointMappingCache.resolve(deviceUuid, FIELD_SWITCH);
        if (target == null) {
            trace.fail("button", "미등록 uuid — tbl_device_point.ref_device_code 없음");
            return false;
        }

        OffsetDateTime at = pressedAt == null ? OffsetDateTime.now() : pressedAt;

        WcButtonPress press = new WcButtonPress(
                target.floorName(), target.wcCode(), target.groupCode(),
                target.gender(), switchNumber, at);
        try {
            buttonPressDispatcher.dispatch(press, trace);
            return true;
        } catch (RejectedExecutionException e) {
            // 전송 큐 포화 — HQS 가 우리 발생 속도보다 느리다는 뜻이다.
            // 수신을 막느니 이 누름을 버린다(버튼 누름은 재전송 큐가 없다는 기존 설계와 같은 노선).
            trace.fail("hqs", "전송큐 포화 — 버림(" + e.getClass().getSimpleName() + ")");
            return false;
        }
    }
}
