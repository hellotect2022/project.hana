package com.mpole.hana.digitaltwin.external.hqs.outbound;

import com.mpole.hana.digitaltwin.external.common.StageTrace;
import com.mpole.hana.digitaltwin.external.hqs.client.HqsClient;
import com.mpole.hana.digitaltwin.external.hqs.common.HqsCallResult;
import com.mpole.hana.digitaltwin.external.hqs.config.HqsProperties;
import com.mpole.hana.digitaltwin.external.hqs.outbound.dto.HqsWcButtonRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 화장실 부스 물리버튼 <b>HQS 전송</b>.
 *
 * <p>이 클래스는 <b>HQS 로 보내는 일만</b> 한다. 예전에는 같은 클래스가 벤더 uuid 를 부스로
 * 해석하고, 현재값에 누적 카운트를 쓰고, HQS 로 보내는 셋을 다 했다. 그래서 HQS 전송 코드가
 * emblaze 수신 구조({@code AmqpPointMappingCache}·{@code AmqpPointTarget}·
 * {@code BoothCurrentValueWriter}·{@code EmblazeTrace})를 알아야 했고, 회사별로 가른 패키지
 * 경계가 {@code hqs → emblaze} 로 뒤집혀 있었다.</p>
 *
 * <pre>
 *   해석 · 흐름 제어   emblaze/button/ButtonPressHandler
 *   스레드 분리        emblaze/button/ButtonPressDispatcher
 *   HQS 전송           여기
 * </pre>
 *
 * <p>경계를 넘어오는 것은 {@link WcButtonPress}(식별자 넷)와 {@link StageTrace}(단계 기록)뿐이다
 * — 둘 다 벤더와 무관하다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WcButtonService {

    private final HqsProperties hqsProperties;
    private final HqsClient hqsClient;

    /**
     * 버튼 누름 1건을 HQS 로 보낸다.
     *
     * <p>실패해도 예외를 던지지 않는다 — 버튼 누름은 재전송 큐가 없고, 전송 실패가
     * 누적 집계를 막아서는 안 된다. 사유는 {@code trace} 에 남는다.</p>
     */
    public void push(WcButtonPress press, StageTrace trace) {
        try {
            String buildingId = hqsProperties.getBuildingId();

            HqsWcButtonRequest body = HqsWcButtonRequest.builder()
                    .bldgId(buildingId == null ? "" : buildingId)
                    .florId(press.florId())
                    .wcId(press.wcId())
                    .gender(press.gender())
                    .wcBoothId(press.wcBoothId())
                    .buttonType(press.switchNumber())
                    .timestamp(press.pressedAt().toString())
                    .build();

            if (!StringUtils.hasText(body.wcId()) || !StringUtils.hasText(body.wcBoothId())
                    || !StringUtils.hasText(body.florId())) {
                // 필수값이 비었으면 HQS 가 IF110(파라미터 오류)을 줄 뿐이다 — 보내기 전에 원인을 남긴다.
                trace.fail("hqs", "필수값 누락(부스 구역 배치 확인) florId=" + body.florId()
                        + " wcId=" + body.wcId() + " wcBoothId=" + body.wcBoothId());
                return;
            }
            if (!StringUtils.hasText(buildingId)) {
                trace.ok("bldgId", "미설정(hdt.integration.hqs.building-id)");
            }

            HqsCallResult result = hqsClient.postWcButton(body);
            if (result.isSuccess()) {
                trace.ok("hqs", result.describe() + " trx=" + result.trxId());
            } else {
                trace.fail("hqs", result.describe() + " trx=" + result.trxId());
            }
        } catch (Exception e) {
            trace.fail("hqs", e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

}
