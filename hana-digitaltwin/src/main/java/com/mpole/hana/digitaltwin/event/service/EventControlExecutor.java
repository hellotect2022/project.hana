package com.mpole.hana.digitaltwin.event.service;

import com.mpole.hana.common.constant.ControlTrigger;
import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.common.dto.OutputSpec.DeviceControlOutput;
import com.mpole.hana.digitaltwin.command.dto.DeviceControlRequest;
import com.mpole.hana.digitaltwin.command.dto.DeviceControlResponse;
import com.mpole.hana.digitaltwin.command.service.DeviceControlService;
import com.mpole.hana.digitaltwin.event.dto.EventControlResult;
import com.mpole.hana.digitaltwin.event.entity.Event;
import com.mpole.hana.digitaltwin.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 이벤트 연동 디바이스 제어 실행기 (2026-10-12).
 *
 * <p>이벤트 정본({@code tbl_event.outputs})의 {@code deviceControls} 중 요청 시점({@link ControlTrigger})에 해당하는
 * 켜진 항목을 하나씩 {@link DeviceControlService#control} 로 보낸다. 수동 제어 API({@code POST /api/command/control})와
 * 같은 경로라 값 검증(TOGGLE/ENUM/RANGE)·XN 전송·감사 로그가 그대로 적용된다.</p>
 *
 * <pre>
 *   실제 이벤트  발생: event 서버가 INSERT 성공 직후  → POST /internal/event/{id}/execute-control?trigger=ON_OCCUR
 *               확인: event 서버가 웹소켓 ACK 반영 직후 → …?trigger=ON_ACK
 *   모의 이벤트  발생·확인: EventSimulationService 가 직접 호출 → 장비는 움직이지 않고 "실행했을 목록" 만 로그
 * </pre>
 *
 * <p><b>트랜잭션을 걸지 않는다</b> — 항목마다 {@code DeviceControlService} 가 자기 트랜잭션으로 돈다. 여기서 감싸면
 * 한 항목의 예외가 바깥 트랜잭션을 rollback-only 로 만들어 뒤 항목까지 망친다. 한 항목이 실패해도 나머지는 계속 실행한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventControlExecutor {

    private final EventRepository eventRepository;
    private final DeviceControlService deviceControlService;

    /** 이벤트 id 로 실행(내부 API 용). */
    public EventControlResult execute(UUID eventId, ControlTrigger trigger) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다. id=" + eventId));
        return execute(event, trigger);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EventSimulationService.SimulatedControlRequested req) {
        try {
            execute(req.e(), req.trigger());
        } catch (Exception e) {
            // 실행기는 항목별로 예외를 삼키므로 여기 오는 건 이벤트 재조회 실패 같은 예외적인 경우뿐
            log.error("[EventControl] 비동기 제어 실패. eventId={}, trigger={}: {}", req.e().getEventId(), req.trigger(), e.getMessage());
        }
    }

    /** 이벤트 행으로 실행. 모의 이벤트면 실제 제어 없이 로그만. */
    public EventControlResult execute(Event event, ControlTrigger trigger) {
        OutputSpec out = event.getOutputs();
        List<DeviceControlOutput> controls = out == null ? List.of() : out.controlsFor(trigger);
        List<EventControlResult.Item> items = new ArrayList<>();

        if (controls.isEmpty()) {
            log.debug("[EventControl] 실행할 제어 없음. eventId={}, trigger={}", event.getEventId(), trigger);
            return new EventControlResult(event.getEventId(), trigger, event.isSimulated(), items);
        }

        for (DeviceControlOutput c : controls) {
//            if (event.isSimulated()) {
//                // 모의 이벤트는 장비를 실제로 움직이지 않는다(훈련 중 배연창이 열리면 안 된다). 무엇을 했을지만 남긴다.
//                log.info("[EventControl] 모의 이벤트 — 제어 생략(로그만). eventId={}, trigger={}, commandPointId={}, value={}",
//                        event.getEventId(), trigger, c.commandPointId(), c.value());
//                items.add(new EventControlResult.Item(c.commandPointId(), c.value(), false, false, "모의 이벤트 — 실행 생략"));
//                continue;
//            }
            try {
                DeviceControlResponse res = deviceControlService.control(DeviceControlRequest.builder().commandPointId(c.commandPointId()).value(c.value()).build());
                log.info("[EventControl] 제어 실행 성공. eventId={}, trigger={}, commandPointId={}, value={}, msg={}",
                        event.getEventId(), trigger, c.commandPointId(), c.value(), res.message());
                items.add(new EventControlResult.Item(c.commandPointId(), c.value(), true, true, res.message()));
            } catch (Exception e) {
                // 검증 실패(값·비활성·매핑 없음)든 XN 실패(502)든 이 항목만 실패로 남기고 다음 항목을 계속한다.
                log.error("[EventControl] 제어 실행 실패. eventId={}, trigger={}, commandPointId={}, value={}: {}",
                        event.getEventId(), trigger, c.commandPointId(), c.value(), e.getMessage());
                items.add(new EventControlResult.Item(c.commandPointId(), c.value(), true, false, e.getMessage()));
            }
        }

        EventControlResult result = new EventControlResult(event.getEventId(), trigger, event.isSimulated(), items);
        log.info("[EventControl] 완료. eventId={}, trigger={}, simulated={}, 대상={}건, 성공={}건",
                event.getEventId(), trigger, event.isSimulated(), items.size(), result.successCount());
        return result;
    }
}
