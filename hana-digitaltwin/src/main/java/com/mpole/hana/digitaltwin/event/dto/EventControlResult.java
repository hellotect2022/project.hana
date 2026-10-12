package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.constant.ControlTrigger;

import java.util.List;
import java.util.UUID;

/**
 * 이벤트 연동 디바이스 제어 실행 결과 (2026-10-12). 내부 API 응답·로그용.
 *
 * @param simulated 모의 이벤트였는가. true 면 장비는 움직이지 않았고 {@code items} 는 "실행했을 목록" 이다
 * @param items     제어 항목별 결과. 이 시점에 켜진 제어가 없으면 빈 목록
 */
public record EventControlResult(
        UUID eventId,
        ControlTrigger trigger,
        boolean simulated,
        List<Item> items
) {

    /**
     * @param executed 실제로 XN 에 명령을 보냈는가(모의면 false)
     * @param success  보냈고 성공했는가. 모의면 false
     * @param message  성공 메시지 또는 실패 사유
     */
    public record Item(Long commandPointId, String value, boolean executed, boolean success, String message) {
    }

    public long successCount() {
        return items.stream().filter(Item::success).count();
    }
}
