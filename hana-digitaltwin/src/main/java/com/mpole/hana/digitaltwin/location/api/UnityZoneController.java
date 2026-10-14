package com.mpole.hana.digitaltwin.location.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityFloorOptionResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityLocationResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityZoneAdminResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityZoneRequest;
import com.mpole.hana.digitaltwin.location.service.UnityZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 콜라이더(구역) 관리 API.
 *
 * <p>구역은 <b>자기참조 트리</b>다 — 층 직속 구역(depth 0) 아래에 자식 구역(depth 1)을 둘 수 있다.
 * 예: 화장실 &gt; 부스, 충전소 &gt; …</p>
 */
@RestController
@RequestMapping("/api/unity-zones")
@RequiredArgsConstructor
@Tag(name = "UnityZone", description = "콜라이더(구역) 관리 API — 층 > 구역 > 자식구역 계층")
public class UnityZoneController {

    private final UnityZoneService unityZoneService;

    @Operation(
            summary = "Unity 위치 트리 조회",
            description = """
                    건물 › 층 › 구역 전체를 한 번에 내려준다. Unity 3D 뷰어가 기동 시 씬 구성·구역 판정에 쓴다.
                    · 건물: buildingId, buildingName, floorList
                    · 층: floorId, floorNum(실제 층수, 지하는 음수 — B2F = -2), sortOrder(0 = 최하층 B7F → 위로 증가, 배열은 이 순서로 정렬됨),
                      floorName, description(층 설명 — 입주사·용도, 예 하나은행 / 주차장. 없으면 null. 2026-10-14 추가), zoneList
                    · 구역: zoneId, zoneName, zoneMeshName(Unity 메시 이름, 항상 값 있음), parentId(상위 구역, 최상위면 null),
                      depth(0 = 최상위, 1 = 자식), zoneCode(외부 연동 식별자)
                    · zoneList 는 평면 배열이다. 중첩 구역은 parentId/depth 로 트리를 복원하고, 레이 판정 시 depth 가 가장 깊은 구역을 고른다.
                    """
    )
    @GetMapping("/location-info")
    public ResponseEntity<ApiResponse<List<UnityLocationResponse>>> getLocationInfo() {
        return ResponseEntity.ok(ApiResponse.success(
                "Unity 위치 트리", unityZoneService.findUnityLocationTree()));
    }

    //region swagger 🔄 층 목록 조회 명세서
    @Operation(
            summary = "층 선택용 옵션 목록",
            description = "구역 등록·수정 화면의 층 선택 드롭다운에 쓰는 목록입니다. 층 번호 오름차순으로 반환합니다."
    )
    //endregion
    @GetMapping("/floors")
    public ResponseEntity<ApiResponse<List<UnityFloorOptionResponse>>> getFloorOptions() {
        return ResponseEntity.ok(ApiResponse.success("층 목록 조회", unityZoneService.getFloorOptions()));
    }

    //region swagger 🔄 콜라이더(구역) 목록 조회 명세서
    @Operation(
            summary = "콜라이더(구역) 목록 조회",
            description = """
                    층/상위구역 필터로 구역 목록을 조회합니다.

                    구역에 **계층**이 생겨 자식 구역(부스 등)도 같은 목록에 섞여 나옵니다.
                    최상위만 필요하면 `rootOnly=true`, 특정 구역의 직속 자식만 필요하면 `parentId` 를 씁니다.

                    파라미터를 하나도 주지 않으면 이전과 동일하게 **전체**를 반환합니다.
                    """,
            parameters = {
                    @Parameter(name = "floorId", description = "층 ID 필터", example = "4"),
                    @Parameter(name = "parentId", description = "이 구역의 **직속 자식**만 조회 (신규)", example = "75"),
                    @Parameter(name = "rootOnly", description = "true 면 최상위 구역(depth 0)만 (신규)", example = "false")
            }
    )
    //endregion
    @GetMapping
    public ResponseEntity<ApiResponse<List<UnityZoneAdminResponse>>> getZones(
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false, defaultValue = "false") boolean rootOnly) {
        return ResponseEntity.ok(ApiResponse.success(
                "콜라이더 목록 조회", unityZoneService.getZones(floorId, parentId, rootOnly)));
    }

    //region swagger 🔄 콜라이더(구역) 단건 조회 명세서
    @Operation(
            summary = "콜라이더(구역) 단건 조회",
            description = "구역 ID로 단건 조회합니다. 상위 구역(parentId/parentName)과 depth 를 함께 반환합니다.",
            parameters = {
                    @Parameter(name = "zoneId", description = "조회할 구역 ID", example = "75", required = true)
            }
    )
    //endregion
    @GetMapping("/{zoneId}")
    public ResponseEntity<ApiResponse<UnityZoneAdminResponse>> getZone(@PathVariable Long zoneId) {
        return ResponseEntity.ok(ApiResponse.success("콜라이더 조회", unityZoneService.getZone(zoneId)));
    }

    //region swagger 🔄 콜라이더(구역) 등록 명세서
    @Operation(
            summary = "콜라이더(구역) 등록",
            description = """
                    구역을 등록합니다.

                    · `parentId` 를 주면 그 구역의 **자식 구역**이 됩니다(예: 화장실 → 부스).
                      계층은 **2단까지**라 자식 구역은 다시 부모가 될 수 없습니다.
                    · `zoneMeshName` 은 **필수이며 전체에서 유일**합니다. 자식 구역(부스 등)도 값을 가져야 합니다 —
                      클라이언트가 null 을 방어하지 않아도 되도록 계약을 단순하게 유지합니다.
                      실제 Unity 메시가 아직 없더라도 규칙에 맞는 이름을 부여하십시오(예: `collider_4f_men_wc_01_b01`).
                      매칭되는 메시가 없으면 3D 에서 그려지지 않을 뿐 동작에는 지장이 없습니다.
                    · `zoneCode` 는 외부 연동(HQS `stationId`/`wcId`/`wcBoothId`) 식별자입니다.
                      구역명은 층마다 중복되어 키로 쓸 수 없으므로 이 값을 씁니다. 값이 있으면 유일해야 합니다.
                    · `depth` 는 **요청에 담지 않습니다** — 상위 구역으로부터 서버가 파생합니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "등록할 구역 정보",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UnityZoneRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "최상위 구역(화장실) 등록",
                                            value = """
                                                    {
                                                      "floorId": 4,
                                                      "zoneName": "남자화장실",
                                                      "zoneMeshName": "collider_4f_men_wc_01",
                                                      "meshType": "3D",
                                                      "parentId": null,
                                                      "zoneCode": "WC_4F_M_01",
                                                      "attributes": { "type": "TOILET", "gender": "M" }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "자식 구역(부스) 등록",
                                            value = """
                                                    {
                                                      "floorId": 4,
                                                      "zoneName": "AM-01",
                                                      "zoneMeshName": "collider_4f_men_wc_01_b01",
                                                      "meshType": "2D",
                                                      "parentId": 75,
                                                      "zoneCode": "WC_4F_M_01_B01",
                                                      "attributes": { "type": "TOILET_BOOTH" }
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    )
    //endregion
    @PostMapping
    public ResponseEntity<ApiResponse<UnityZoneAdminResponse>> createZone(
            @Valid @RequestBody UnityZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success("콜라이더 등록", unityZoneService.createZone(request)));
    }

    //region swagger 🔄 콜라이더(구역) 수정 명세서
    @Operation(
            summary = "콜라이더(구역) 수정",
            description = """
                    구역 정보를 수정합니다. **요청에 담은 값으로 전부 덮어씁니다** — 바꾸지 않을 필드도 현재 값을 실어 보내야 합니다.

                    `parentId` 를 바꾸면 계층이 이동합니다. 다음은 거절됩니다.
                    · 자기 자신을 상위로 지정
                    · **하위 구역을 상위로 지정**(순환 참조)
                    · 계층 2단 초과 — 자식을 가진 구역은 다른 구역의 자식이 될 수 없습니다
                    """,
            parameters = {
                    @Parameter(name = "zoneId", description = "수정할 구역 ID", example = "501", required = true)
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "수정할 구역 정보",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UnityZoneRequest.class),
                            examples = @ExampleObject(
                                    name = "부스를 다른 화장실로 이동",
                                    value = """
                                            {
                                              "floorId": 4,
                                              "zoneName": "AM-01",
                                              "zoneMeshName": "collider_4f_men_wc_01_b01",
                                              "meshType": "2D",
                                              "parentId": 80,
                                              "zoneCode": "WC_4F_M_01_B01",
                                              "attributes": { "type": "TOILET_BOOTH" }
                                            }
                                            """
                            )
                    )
            )
    )
    //endregion
    @PutMapping("/{zoneId}")
    public ResponseEntity<ApiResponse<UnityZoneAdminResponse>> updateZone(
            @PathVariable Long zoneId,
            @Valid @RequestBody UnityZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success("콜라이더 수정", unityZoneService.updateZone(zoneId, request)));
    }
}
