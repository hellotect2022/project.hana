package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.HqsApiService;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.WcButtonService;
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
 * <p>큐별 리스너에는 <b>원본 저장 이후의 부가 처리만</b> 남긴다 — 재실 판정(space),
 * 중복 제거·HQS 전송(enocean). 조명은 부가 처리가 없어 저장만 한다.</p>
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
    private final HqsApiService hqsApiService;
    private final WcButtonService wcButtonService; // 물리버튼: HQS 전송 + 파생 집계
    private final EnOceanIndexDeduplicator enOceanIndexDeduplicator; // onEnocean 중복수신 제거

    /** 조명 메시지 → 밝기 기록 (부가 처리 없음) */
    @RabbitListener(queues = MonitoringQueue.Names.LIGHT, containerFactory = "monitoringContainerFactory")
    public void onLight(String body) {
        try {
            log.info("onLight : {}",body);
            JsonNode node = objectMapper.readTree(body);
            String deviceUuid = node.path("device_uuid").asText(null);
            OffsetDateTime eventAt = EmblazeStatusRouter.parseTimestamp(node.path("timestamp").asText(null));

            emblazeStatusRouter.route(deviceUuid, node.get("status"), eventAt);
        } catch (JsonProcessingException e) {
            log.error("onLight 파싱 오류", e);
        } catch (Exception e) {
            log.error("onLight 처리 중 오류: {}", e.getMessage(), e);
        }
    }

    /** 공간(PIR 누적값) 메시지 → 원본 기록 + 재실 판정 */
    @RabbitListener(queues = MonitoringQueue.Names.SPACE, containerFactory = "monitoringContainerFactory")
    public void onSpace(String body) {
        try {
            log.info("onSpace : {}",body);
            JsonNode node = objectMapper.readTree(body);
            String deviceUuid = node.path("device_uuid").asText(null);
            JsonNode status = node.get("status");
            OffsetDateTime eventAt = EmblazeStatusRouter.parseTimestamp(node.path("timestamp").asText(null));

            // ① 원본(pir_accumulation) 그대로 기록
            emblazeStatusRouter.route(deviceUuid, status, eventAt);

            // ② 누적값 증감으로 재실(occ) 판정 — 상태가 바뀔 때만 기록한다
            if (status == null || !status.hasNonNull("pir_accumulation")) {
                log.warn("space 메시지에 pir_accumulation 없음 device={}", deviceUuid);
                return;
            }

            // occ 로 변환해서 처리함
            hqsApiService.saveBoothStatusData(deviceUuid, status.path("pir_accumulation").asLong());
        } catch (JsonProcessingException e) {
            log.error("onSpace 파싱 오류", e);
        } catch (Exception e) {
            log.error("onSpace 처리 중 오류: {}", e.getMessage(), e);
        }
    }

    /** EnOcean 무선버튼 메시지 → 원본 기록 + HQS wcButton 전송 */
    @RabbitListener(queues = MonitoringQueue.Names.ENOCEAN, containerFactory = "monitoringContainerFactory")
    public void onEnocean(String body) {
        try {
            log.info("onEnocean : {}",body);
            JsonNode node = objectMapper.readTree(body);
            String deviceUuid = node.path("device_uuid").asText(null);
            JsonNode status = node.get("status");
            if (status == null) {
                log.warn("enocean 메시지에 status 없음 device={}", deviceUuid);
                return;
            }
            OffsetDateTime eventAt = EmblazeStatusRouter.parseTimestamp(node.path("timestamp").asText(null));

            // 같은 index 는 같은 이벤트다(벤더 명세) — 저장·전송 전에 걸러낸다
            long index = status.path("index").asLong();
            if (enOceanIndexDeduplicator.isDuplicate(deviceUuid, index)) {
                log.debug("EnOcean 중복 무시 device={} index={}", deviceUuid, index);
                return;
            }

            // ① 원본(switch_number, index) 기록
            emblazeStatusRouter.route(deviceUuid, status, eventAt);

            // ② HQS 전송 + 요청 종류별 누적 집계
            String switchNumber = status.path("switch_number").asText();
            if (!switchNumber.isEmpty()) {
                log.info("Switch 눌림 device={} switch_number={}", deviceUuid, switchNumber);
                wcButtonService.handleButtonPress(deviceUuid, switchNumber, eventAt);
            }
        } catch (JsonProcessingException e) {
            log.error("onEnocean 파싱 오류", e);
        } catch (Exception e) {
            log.error("onEnocean 처리 중 오류: {}", e.getMessage(), e);
        }
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
