package com.mpole.hana.digitaltwin.external.hqs.outbound;

import java.time.OffsetDateTime;

/**
 * HQS 로 보낼 버튼 누름 1건 — <b>회사 경계를 넘는 평면 데이터</b>.
 *
 * <p>예전에는 {@code WcButtonService} 가 {@code AmqpPointTarget}(emblaze 해석 결과)을 그대로
 * 받았다. 그러면 HQS 전송 코드가 벤더 수신 구조를 알아야 해서 {@code hqs → emblaze} 참조가
 * 생긴다. 필요한 값은 식별자 몇 개뿐이므로 <b>그것만 담아 넘긴다</b> — 벤더가 바뀌어도
 * 전송 코드는 그대로다.</p>
 *
 * <p>식별자의 정본은 {@code tbl_device_group}(부스 그룹)이다. <b>조회 API(boothStatus)가
 * 같은 출처를 쓴다</b> — 푸시와 조회가 같은 부스를 다른 이름으로 부르면 HQS 가 둘을 짝지을 수
 * 없다. zone 계층은 쓰지 않는다(부스 zone 이 3개 층에만 있어 나머지 층에서 신원을 못 만든다).</p>
 *
 * @param florId       층 이름 ({@code 4F}) — HQS {@code florId}
 * @param wcId         화장실 코드 ({@code WC_4F_M01}) — HQS {@code wcId}
 * @param wcBoothId    부스 그룹 코드 ({@code WC_4F_M01_B01}) — HQS {@code wcBoothId}
 * @param gender       {@code M} 또는 {@code F} — HQS {@code gender}
 * @param switchNumber 눌린 스위치 번호 원본 — HQS {@code buttonType} 으로 그대로 나간다
 * @param pressedAt    센서 타임스탬프
 */
public record WcButtonPress(
        String florId,
        String wcId,
        String wcBoothId,
        String gender,
        String switchNumber,
        OffsetDateTime pressedAt
) {
}
