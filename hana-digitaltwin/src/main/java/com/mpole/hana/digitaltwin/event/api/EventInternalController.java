package com.mpole.hana.digitaltwin.event.api;

import com.mpole.hana.common.constant.ControlTrigger;
import com.mpole.hana.digitaltwin.event.dto.EventControlResult;
import com.mpole.hana.digitaltwin.event.service.EventControlExecutor;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 서버 간 내부 API — event 서버 전용 (2026-10-12). 클라이언트(Unity/웹)용이 아니어서 Swagger 에서 숨긴다.
 *
 * <p>event 서버는 이벤트 발생·확인을 알지만 제어 로직(제어포인트·XN 어댑터)은 digitaltwin 에 있다.
 * 그래서 발생 INSERT 직후(ON_OCCUR)·웹소켓 ACK 반영 직후(ON_ACK) event 서버가 이 API 를 불러 제어를 맡긴다.</p>
 *
 * <p>경로가 {@code /api/**} 밖이라 SecurityConfig 에서 {@code /internal/**} 를 따로 permitAll 했다.
 * 외부에 노출하지 않는 내부망 전용 경로로 운영한다(리버스 프록시에서 /internal 을 막을 것).</p>
 */
@Hidden
@Slf4j
@RestController
@RequestMapping("/internal/event")
@RequiredArgsConstructor
public class EventInternalController {

    private final EventControlExecutor controlExecutor;

    /** 이벤트의 outputs 중 해당 시점(trigger) 의 켜진 디바이스 제어를 실행한다. 한 항목 실패는 응답 items 에 실패로 남는다. */
    @PostMapping("/{eventId}/execute-control")
    public ResponseEntity<ApiResponse<EventControlResult>> executeControl(
            @PathVariable UUID eventId,
            @RequestParam ControlTrigger trigger
    ) {
        EventControlResult result = controlExecutor.execute(eventId, trigger);
        return ResponseEntity.ok(ApiResponse.success("디바이스 제어 실행", result));
    }
}
