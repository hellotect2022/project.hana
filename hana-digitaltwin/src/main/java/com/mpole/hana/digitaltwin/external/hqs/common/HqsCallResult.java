package com.mpole.hana.digitaltwin.external.hqs.common;

/**
 * HQS 호출 1회의 결과 — <b>응답뿐 아니라 실패 사유와 추적 ID 까지</b> 담는다.
 *
 * <h3>왜 응답만으로 부족한가</h3>
 * <p>예전에는 호출부가 {@code HqsOutboundResponse} 또는 {@code null} 만 받았다. 그래서
 * 연결 실패·타임아웃·HTTP 500 이 전부 {@code null} 로 뭉개져 <b>"전송 실패" 라는 말밖에 못 남겼다.</b>
 * 원인은 {@code HqsClient} 안쪽 로그에만 있어 다른 줄을 찾아가야 했다.</p>
 *
 * <p>{@code trxId} 를 같이 주는 이유도 같다 — 요약 로그 한 줄에서 {@code HqsClient} 의 상세 줄로
 * 바로 건너뛸 수 있어야 한다.</p>
 *
 * @param response  HQS 응답 본문. 응답 자체를 못 받았으면 null
 * @param trxId     이 호출에 실린 {@code X-trxId}
 * @param failReason 전송 계층 실패 사유(연결 실패·타임아웃·HTTP 오류). 정상 응답을 받았으면 null
 *                   — <b>본문 code 가 실패인 것과는 다른 층위</b>다
 */
public record HqsCallResult(
        HqsOutboundResponse response,
        String trxId,
        String failReason
) {

    public static HqsCallResult of(HqsOutboundResponse response, String trxId) {
        return new HqsCallResult(response, trxId, null);
    }

    public static HqsCallResult transportFail(String trxId, String reason) {
        return new HqsCallResult(null, trxId, reason);
    }

    /** 응답을 받았고 본문 code 도 정상인가. */
    public boolean isSuccess() {
        return response != null && response.isSuccess();
    }

    /**
     * 요약 로그에 쓸 한 조각. 성공이면 {@code OK(IF000)},
     * 실패면 그 원인을 그대로 보여준다.
     */
    public String describe() {
        if (response == null) {
            return "실패(" + (failReason == null ? "응답없음" : failReason) + ")";
        }
        if (response.isSuccess()) {
            return "OK(" + response.code() + ")";
        }
        return "실패(" + response.code() + " " + response.message() + ")";
    }
}
