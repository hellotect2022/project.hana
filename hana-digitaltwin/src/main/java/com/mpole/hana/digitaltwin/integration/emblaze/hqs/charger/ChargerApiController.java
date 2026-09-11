package com.mpole.hana.digitaltwin.integration.emblaze.hqs.charger;

import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsApiResponse;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.charger.dto.HqsStationItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HQS 전기차 충전기 조회 API (HQS 명세 URI 준수).
 *
 * <p>충전소는 별도 테이블이 아니라 <b>구역(zone)</b> 이다 — 충전기가 배치된 구역이 곧 충전소이고,
 * 그 구역의 {@code zone_code} 가 {@code stationId} 다.</p>
 */
@RestController
@RequestMapping("/api/v1/charger")
@RequiredArgsConstructor
@Tag(name = "HQS-Charger", description = "HQS 연동 — 전기차 충전기 조회")
public class ChargerApiController {

    private final ChargerApiService chargerApiService;

    //region swagger 🔄 충전기 목록 조회 명세서
    @Operation(
            summary = "충전소별 충전기 목록 조회",
            description = """
                    충전소(구역) 하위의 충전기와 상태를 반환합니다.

                    **매핑**
                    · `stationId` = 구역의 `zone_code` (구역명은 층마다 중복되어 키로 쓸 수 없음)
                    · `chargerId` = 장비의 `device_key` · `chargerName` = `device_name`
                    · `type` = 카테고리 `DCC`→`DC_FAST`(급속), `ACC`→`AC_SLOW`(완속)

                    **상태 매핑** — 수집값 `st` 8종을 HQS 3종으로 접습니다.
                    · `1 충전대기` → `Available`
                    · `2 충전중` → `Charging`
                    · 그 외(`0 미연결`·`3 점검중`·`4 실험중`·`5 운영중지`·`6 전원off`·`7 통신불량`·값없음) → `Faulted`
                      — 고장이 아니어도 **이용 불가**이며, 모를 때 Available 로 보내면 이용자가 헛걸음합니다.

                    **원 명세 대비 제외된 필드** — 현재 수집되지 않아 항상 null 이 되므로 응답에서 뺐습니다.
                    · `connectorId` · `connectorType` · `remainingTime`
                    `connectors` 배열 구조는 유지했습니다. 추후 XN 에서 충전구별 포인트를 받으면
                    필드만 채우면 되고, 지금은 **충전기 1대당 항목이 정확히 1개**입니다.
                    """,
            parameters = {
                    @Parameter(name = "stationId", description = "충전소 구역 코드(zone_code)",
                            example = "EVCS_B2F_01", required = true)
            }
    )
    //endregion
    @GetMapping("/list/{stationId}")
    public ResponseEntity<HqsApiResponse<List<HqsStationItem>>> getChargerList(
            @PathVariable String stationId) {
        List<HqsStationItem> data = chargerApiService.getChargerList(stationId);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }

    //region swagger 🔄 충전기 전체 목록 조회 명세서
    @Operation(
            summary = "충전기 전체 목록 조회 (충전소 무관)",
            description = """
                    stationId 없이 전 충전소의 충전기를 반환합니다. 충전소 코드를 모르는 상태에서
                    조회하거나 목록을 확인할 때 씁니다. 응답 구조는 충전소별 조회와 동일합니다.

                    배치되지 않은 충전기는 소속 충전소를 알 수 없어 **제외**됩니다.
                    """
    )
    //endregion
    @GetMapping("/list")
    public ResponseEntity<HqsApiResponse<List<HqsStationItem>>> getAllChargerList() {
        return ResponseEntity.ok(HqsApiResponse.success(chargerApiService.getChargerList(null)));
    }
}
