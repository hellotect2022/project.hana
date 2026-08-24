package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.HqsApiService;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.HqsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * emblaze monitoring 큐 리스너. 원본의 실동작 리스너 2개(space·enocean)만 이식한다
 * (status/sensor/light/energy/datetime/system no-op 리스너는 생략).
 * <p>{@code hdt.integration.emblaze.enabled=true} 일 때만 기동(기본 OFF).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "hdt.integration.emblaze.enabled", havingValue = "true", matchIfMissing = false)
public class EmblazeMonitoringListeners {

    private final ObjectMapper objectMapper;
    private final HqsApiService hqsApiService;
    private final HqsClient hqsClient;
    private final EnOceanIndexDeduplicator enOceanIndexDeduplicator; // onEnocean 중복수신 제거

    /** 공간(PIR 누적값) 메시지 → 재실 판정 입력 */
    @RabbitListener(queues = MonitoringQueue.Names.SPACE, containerFactory = "monitoringContainerFactory")
    public void onSpace(String body) {
        try {
            log.info("onSpace ->{}", body);
            JsonNode node = objectMapper.readTree(body);
            String deviceUuid = node.path("device_uuid").asText(null);
            JsonNode status = node.get("status");

            if (status == null || !status.hasNonNull("pir_accumulation")) {
                log.warn("space 메시지에 pir_accumulation 없음 device={}", deviceUuid);
                return;
            }
            long currentPir = status.path("pir_accumulation").asLong();

            String timestamp = node.get("timestamp").asText();
            hqsApiService.saveBoothStatusData(deviceUuid, timestamp, currentPir);
        } catch (JsonProcessingException e) {
            log.error("onSpace 파싱 오류", e);
        }
    }

    /** EnOcean 무선버튼 메시지 → HQS wcButton 전송 */
    @RabbitListener(queues = MonitoringQueue.Names.ENOCEAN, containerFactory = "monitoringContainerFactory")
    public void onEnocean(String body) {
        try {
            log.debug("onEnocean ->{}", body);
            JsonNode node = objectMapper.readTree(body);
            String deviceUuid = node.get("device_uuid").asText();
            String timestamp = node.get("timestamp").asText();
            JsonNode status = node.get("status");
            long index = status.get("index").asLong();

            if (enOceanIndexDeduplicator.isDuplicate(deviceUuid, index)) {
                log.debug("EnOcean 중복 무시 device={} index={}", deviceUuid, index);
                return;
            }

            String switchNumber = status.path("switch_number").asText();
            if (!switchNumber.isEmpty()) {
                log.info("상태 변화 감지: Switch 눌림 (Device: {}, Switch Number: {})", deviceUuid, switchNumber);
                // TODO 실값 매핑(후속) — bldgId/florId/wcId/wcBoothId 하드코딩은 원본대로 유지
                String result = hqsClient.postWcButton(Map.of(
                        "bldgId", "aaaaa",
                        "florId", "bbbbbb",
                        "wcId", "ccccc",
                        "wcBoothId", 9,
                        "buttonType", switchNumber,
                        "timestamp", timestamp
                ));
                log.info("HQS wcButton 호출 성공: {}", result);
            }
        } catch (JsonProcessingException e) {
            log.error("onEnocean 파싱 오류", e);
        } catch (Exception e) {
            log.error("HQS wcButton 호출 중 오류: {}", e.getMessage());
        }
    }
}
