package com.mpole.hana.digitaltwin.external.hqs.outbound.dto;

import lombok.Builder;

/**
 * HQS {@code wcButton} 전송 본문 — 물리버튼 눌림 1건.
 *
 * <p>명세의 Request Body 7개 항목을 그대로 옮긴 것이다. 예전에는 {@code Map} 으로 만들었는데,
 * 필드명을 오타 내도 컴파일이 통과하고 <b>HQS 가 400 을 줄 때까지 모른다</b>. 레코드로 고정한다.</p>
 *
 * @param bldgId     건물 아이디 (필수)
 * @param florId     층 아이디 (필수)
 * @param wcId       화장실 아이디 (필수) — 우리 화장실 구역의 {@code zone_code}
 * @param gender     성별 정보 (선택) — {@code M} / {@code F}
 * @param wcBoothId  화장실 칸 아이디 (필수) — 우리 부스 구역의 {@code zone_code}
 * @param buttonType 버튼유형 (필수) — 눌린 {@code switch_number} 원본값 0~3
 * @param timestamp  발생일시정보 (선택) — 센서 타임스탬프(ISO-8601)
 */
@Builder
public record HqsWcButtonRequest(
        String bldgId,
        String florId,
        String wcId,
        String gender,
        String wcBoothId,
        String buttonType,
        String timestamp
) {
}
