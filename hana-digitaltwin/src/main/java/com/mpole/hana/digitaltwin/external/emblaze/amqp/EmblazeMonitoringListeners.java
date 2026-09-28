package com.mpole.hana.digitaltwin.external.emblaze.amqp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.digitaltwin.external.emblaze.booth.BoothOccupancyService;
import com.mpole.hana.digitaltwin.external.emblaze.button.ButtonPressHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * emblaze monitoring 큐 리스너 (light · space · enocean).
 *
 * <h3>공통 처리</h3>
 * <p>세 큐 모두 {@code {device_uuid, timestamp, status:{...}}} 형태고, 벤더는
 * <b>센서·스위치의 구별용 ID 를 주지 않고 전부 조명제어기 UUID 로</b> 보낸다.
 * 그래서 큐마다 파싱을 따로 두지 않고 {@link EmblazeStatusRouter} 가
 * {@code (uuid, status 의 필드명)} → 포인트 매핑으로 기록을 끝낸다.</p>
 *
 * <pre>
 *   light   present_level / target_level / remaining_time → 조명(RL) 장비
 *   space   pir_accumulation                              → 재실센서(OCC) 장비
 *   enocean switch_number / index                         → 버튼(TB) 장비
 * </pre>
 *
 * <h3>로그</h3>
 * <p>메시지 1건이 여러 클래스를 거치므로 {@link EmblazeTrace} 에 단계별 결과를 모아
 * <b>한 줄로</b> 내보낸다. 중간에 어디서 끝나든 {@code finally} 에서 반드시 찍히므로
 * "실패한 건지 아예 안 탄 건지"가 한 줄에 드러난다.</p>
 *
 * <p>{@code space} 만 예외다 — 재실 판정이 단일 스레드 executor 로 넘어가므로
 * trace 도 함께 넘겨 <b>그쪽에서 flush 한다</b>. 여기서 찍으면 판정 결과가 빠진다.</p>
 *
 * <p>{@code hdt.integration.emblaze.enabled=true} 일 때만 기동(기본 OFF).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "hdt.integration.emblaze.enabled", havingValue = "true", matchIfMissing = false)
public class EmblazeMonitoringListeners {

    private final ObjectMapper objectMapper;
    private final EmblazeStatusRouter emblazeStatusRouter; // (uuid, 필드) → current_value 기록
    private final BoothOccupancyService boothOccupancyService;
    private final ButtonPressHandler buttonPressHandler; // 물리버튼: 부스 해석 → HQS 전송 + 파생 집계
    private final EnOceanIndexDeduplicator enOceanIndexDeduplicator; // onEnocean 중복수신 제거

    /** 조명 메시지 → 밝기 기록 (부가 처리 없음) */
    @RabbitListener(queues = MonitoringQueue.Names.LIGHT, containerFactory = "monitoringContainerFactory")
    public void onLight(String body) {
        log.info("[{}] : {}", MonitoringQueue.Names.LIGHT, body);
        EmblazeTrace trace = null;
        try {
            JsonNode node = objectMapper.readTree(body);
            String deviceUuid = node.path("device_uuid").asText(null);
            trace = EmblazeTrace.start(MonitoringQueue.Names.LIGHT, deviceUuid);

            OffsetDateTime eventAt = EmblazeStatusRouter.parseTimestamp(node.path("timestamp").asText(null));
            int written = emblazeStatusRouter.route(deviceUuid, node.get("status"), eventAt, trace);
            recordRoute(trace, written);

        } catch (JsonProcessingException e) {
            trace = failedTrace(trace, MonitoringQueue.Names.LIGHT, "본문 파싱 오류: " + e.getOriginalMessage());
        } catch (Exception e) {
            trace = failedTrace(trace, MonitoringQueue.Names.LIGHT, describe(e));
        } finally {
            if (trace != null) {
                trace.flush(log);
            }
        }
    }

    /** 공간(PIR 누적값) 메시지 → 원본 기록 + 재실 판정 */
    @RabbitListener(queues = MonitoringQueue.Names.SPACE, containerFactory = "monitoringContainerFactory")
    public void onSpace(String body) {
        log.info("[{}] : {}", MonitoringQueue.Names.SPACE, body);
        EmblazeTrace trace = null;
        boolean handedOff = false; // 재실 판정 쪽으로 trace 를 넘겼는가
        try {
            JsonNode node = objectMapper.readTree(body);
            String deviceUuid = node.path("device_uuid").asText(null);
            JsonNode status = node.get("status");
            trace = EmblazeTrace.start(MonitoringQueue.Names.SPACE, deviceUuid);

            OffsetDateTime eventAt = EmblazeStatusRouter.parseTimestamp(node.path("timestamp").asText(null));

            // ① 원본(pir_accumulation) 그대로 기록
            recordRoute(trace, emblazeStatusRouter.route(deviceUuid, status, eventAt, trace));

            // ② 누적값 증감으로 재실(occ) 판정 — 움직임이 있을 때만 기록한다
            if (status == null || !status.hasNonNull("pir_accumulation")) {
                trace.fail("occ", "pir_accumulation 없음");
                return;
            }
            long pir = status.path("pir_accumulation").asLong();
            trace.key("pir", pir);

            // 판정은 executor 에서 끝나므로 trace 도 넘긴다 — 결과까지 한 줄에 담기 위함이다.
            handedOff = true;
            boothOccupancyService.saveBoothStatusData(deviceUuid, pir, trace);

        } catch (JsonProcessingException e) {
            trace = failedTrace(trace, MonitoringQueue.Names.SPACE, "본문 파싱 오류: " + e.getOriginalMessage());
        } catch (Exception e) {
            handedOff = false;
            trace = failedTrace(trace, MonitoringQueue.Names.SPACE, describe(e));
        } finally {
            // 넘겼으면 저쪽이 찍는다. flush 는 두 번 불려도 한 번만 나가지만, 여기서 먼저 찍으면
            // 판정 결과가 빠진 줄이 나가므로 넘긴 경우엔 건드리지 않는다.
            if (trace != null && !handedOff) {
                trace.flush(log);
            }
        }
    }

    /** EnOcean 무선버튼 메시지 → 원본 기록 + HQS wcButton 전송 */
    @RabbitListener(queues = MonitoringQueue.Names.ENOCEAN, containerFactory = "monitoringContainerFactory")
    public void onEnocean(String body) {
        log.info("[{}] : {}", MonitoringQueue.Names.ENOCEAN, body);
        EmblazeTrace trace = null;
        boolean handedOff = false; // HQS 전송 스레드로 trace 를 넘겼는가
        try {
            JsonNode node = objectMapper.readTree(body);
            String deviceUuid = node.path("device_uuid").asText(null);
            JsonNode status = node.get("status");
            trace = EmblazeTrace.start(MonitoringQueue.Names.ENOCEAN, deviceUuid);

            if (status == null) {
                trace.fail("recv", "status 없음");
                return;
            }
            OffsetDateTime eventAt = EmblazeStatusRouter.parseTimestamp(node.path("timestamp").asText(null));

            // 같은 index 는 같은 이벤트다(벤더 명세) — 저장·전송 전에 걸러낸다
            long index = status.path("index").asLong();
            String switchNumber = status.path("switch_number").asText();
            trace.key("idx", index).key("sw", switchNumber.isEmpty() ? "?" : switchNumber);

            if (enOceanIndexDeduplicator.isDuplicate(deviceUuid, index)) {
                trace.ok("dedup", "DUP");
                return;
            }
            trace.ok("dedup", "PASS");

            // ① 원본(switch_number, index) 기록
            recordRoute(trace, emblazeStatusRouter.route(deviceUuid, status, eventAt, trace));

            // ② HQS 전송 + 요청 종류별 누적 집계
            if (switchNumber.isEmpty()) {
                trace.fail("type", "switch_number 없음");
                return;
            }
            // 전송이 전송 스레드로 넘어갔으면 그쪽이 결과까지 담아 찍는다.
            handedOff = buttonPressHandler.handle(deviceUuid, switchNumber, eventAt, trace);

        } catch (JsonProcessingException e) {
            trace = failedTrace(trace, MonitoringQueue.Names.ENOCEAN, "본문 파싱 오류: " + e.getOriginalMessage());
        } catch (Exception e) {
            handedOff = false;
            trace = failedTrace(trace, MonitoringQueue.Names.ENOCEAN, describe(e));
        } finally {
            // 넘겼으면 저쪽이 찍는다. flush 는 두 번 불려도 한 번만 나가지만, 여기서 먼저 찍으면
            // HQS 결과가 빠진 줄이 나가므로 넘긴 경우엔 건드리지 않는다(onSpace 와 같은 규칙).
            if (trace != null && !handedOff) {
                trace.flush(log);
            }
        }
    }

    /**
     * 기록된 포인트 수를 남긴다. <b>0 이면 매핑이 안 된 것</b>이라 실패로 본다 —
     * 메시지는 왔는데 어디에도 안 남은 상태가 조용히 지나가면 안 된다.
     */
    private static void recordRoute(EmblazeTrace trace, int written) {
        if (written > 0) {
            trace.ok("point", written);
        } else {
            trace.fail("point", "매핑된 필드 없음");
        }
    }

    /** 본문 파싱 전에 터져 trace 가 없을 수도 있다 — 그때도 한 줄은 남긴다. */
    private static EmblazeTrace failedTrace(EmblazeTrace trace, String queue, String reason) {
        EmblazeTrace t = trace != null ? trace : EmblazeTrace.start(queue, null);
        t.fail("recv", reason);
        return t;
    }

    private static String describe(Exception e) {
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    // ────────────────────────────────────────────────────────────────
    // [보존] 기존 하드코딩 전송 — 부스 매핑 도입으로 대체됨. 참고용으로 남긴다.
    // ────────────────────────────────────────────────────────────────
    // String result = hqsClient.postWcButton(Map.of(
    //         "bldgId", "aaaaa",
    //         "florId", "bbbbbb",
    //         "wcId", "ccccc",
    //         "wcBoothId", 9,
    //         "buttonType", switchNumber,
    //         "timestamp", timestamp
    // ));
    // log.info("HQS wcButton 호출 성공: {}", result);
}
