package com.mpole.hana.digitaltwin.infra.api;

import com.mpole.hana.common.constant.InfraType;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.AutoAssignRequest;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.BulkMappingRequest;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.CandidateDeviceResponse;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.ConsistencyResponse;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.MappedDeviceResponse;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.DeviceInfraRequest;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.DeviceInfraResponse;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.InfraMappingRequest;
import com.mpole.hana.digitaltwin.infra.service.DeviceInfraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/device-infra")
@RequiredArgsConstructor
@Tag(name = "Device Infra", description = "인프라 마스터(중계기/NVR/VMS/미들웨어) 관리 및 장비 매핑 API")
public class DeviceInfraController {

    private final DeviceInfraService deviceInfraService;

    @Operation(
            summary = "인프라 목록 조회",
            description = "인프라 마스터 전체(또는 타입별) 목록을 매핑 장비 수와 함께 조회합니다.",
            parameters = {
                    @Parameter(name = "type", description = "인프라 타입 필터(REPEATER/NVR/VMS/MIDDLEWARE/ETC)", required = false)
            }
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceInfraResponse>>> getAll(
            @RequestParam(name = "type", required = false) InfraType type) {
        return ResponseEntity.ok(ApiResponse.success("인프라 목록 조회", deviceInfraService.getAll(type)));
    }

    @Operation(
            summary = "인프라 등록",
            description = "인프라 마스터를 등록합니다. refInfraCode 는 외부(vwDigitalTwin_02) 매핑 키로 관리자가 직접 입력합니다. "
                    + "NVR/VMS 등 외부 뷰와 무관한 타입은 refInfraCode 를 비워둘 수 있습니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<DeviceInfraResponse>> create(@RequestBody DeviceInfraRequest request) {
        return ResponseEntity.ok(ApiResponse.success("인프라 등록", deviceInfraService.create(request)));
    }

    @Operation(summary = "인프라 수정", description = "이름/타입/ref 코드/활성/속성을 수정합니다(제공된 필드만 반영).")
    @PutMapping("/{infraId}")
    public ResponseEntity<ApiResponse<DeviceInfraResponse>> update(
            @PathVariable Long infraId,
            @RequestBody DeviceInfraRequest request) {
        return ResponseEntity.ok(ApiResponse.success("인프라 수정", deviceInfraService.update(infraId, request)));
    }

    @Operation(
            summary = "인프라 삭제",
            description = "인프라 행만 삭제합니다. 소속 장비는 삭제되지 않으며, 삭제 전에 해당 장비들의 infra_id 를 NULL 로 명시 해제합니다"
                    + "(FK ON DELETE SET NULL 과 동일 결과이며 FK 미적용 DB 에서도 안전). 해제된 장비는 정합성 리포트(미매핑)에 노출됩니다."
    )
    @DeleteMapping("/{infraId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long infraId) {
        deviceInfraService.delete(infraId);
        return ResponseEntity.ok(ApiResponse.success("인프라 삭제"));
    }

    @Operation(
            summary = "장비 소속 지정/해제",
            description = "장비의 인프라 소속을 수동 지정합니다(infraId=null 이면 해제). 수동 지정은 자동 산정이 덮지 않습니다."
    )
    @PutMapping("/mapping")
    public ResponseEntity<ApiResponse<Void>> setMapping(@RequestBody InfraMappingRequest request) {
        deviceInfraService.setMapping(request);
        return ResponseEntity.ok(ApiResponse.success("장비 인프라 매핑 반영"));
    }

    @Operation(
            summary = "인프라별 매핑 장비 목록",
            description = "이 인프라에 소속된 장비 목록(장비명/표시명/카테고리)을 조회합니다."
    )
    @GetMapping("/{infraId}/devices")
    public ResponseEntity<ApiResponse<List<MappedDeviceResponse>>> getMappedDevices(@PathVariable Long infraId) {
        return ResponseEntity.ok(ApiResponse.success("인프라 매핑 장비 목록", deviceInfraService.getMappedDevices(infraId)));
    }

    @Operation(
            summary = "매핑 후보 장비 검색",
            description = "이 인프라 소속이 아닌 장비를 키워드(장비명/표시명 부분일치)로 검색합니다(최대 100건). "
                    + "타 인프라 소속 장비는 현재 소속(infraId/infraName)이 함께 반환됩니다."
    )
    @GetMapping("/{infraId}/candidates")
    public ResponseEntity<ApiResponse<List<CandidateDeviceResponse>>> getMappingCandidates(
            @PathVariable Long infraId,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success("매핑 후보 장비", deviceInfraService.getMappingCandidates(infraId, keyword)));
    }

    @Operation(
            summary = "인프라 기준 장비 일괄 매핑",
            description = "선택한 장비들을 이 인프라 소속으로 지정합니다. 타 인프라 소속 장비도 이 인프라로 이동합니다(명시적 수동 지정)."
    )
    @PostMapping("/{infraId}/devices")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> bulkAssign(
            @PathVariable Long infraId,
            @RequestBody BulkMappingRequest request) {
        int affected = deviceInfraService.bulkAssign(infraId, request);
        return ResponseEntity.ok(ApiResponse.success("장비 일괄 매핑", Map.of("affectedCount", affected)));
    }

    @Operation(
            summary = "인프라 기준 장비 일괄 해제",
            description = "이 인프라 소속인 선택 장비들의 매핑을 해제합니다(infra_id=NULL). 해제된 장비는 정합성 리포트(미매핑)에 노출될 수 있습니다."
    )
    @PostMapping("/{infraId}/devices/remove")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> bulkUnassign(
            @PathVariable Long infraId,
            @RequestBody BulkMappingRequest request) {
        int affected = deviceInfraService.bulkUnassign(infraId, request);
        return ResponseEntity.ok(ApiResponse.success("장비 일괄 해제", Map.of("affectedCount", affected)));
    }

    @Operation(
            summary = "장비 소속 자동 산정",
            description = "포인트 ref_device_code 최빈값 → ref_infra_code 매칭으로 infra_id 를 일괄 산정합니다. "
                    + "infra_id 가 비어있는 장비만 채우며(수동 우선), excludeRefCodes 로 공유신호(외기온도류) 인프라 코드를 제외할 수 있습니다."
    )
    @PostMapping("/auto-assign")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> autoAssign(
            @RequestBody(required = false) AutoAssignRequest request) {
        int assigned = deviceInfraService.autoAssign(request);
        return ResponseEntity.ok(ApiResponse.success("자동 산정 완료", Map.of("assignedCount", assigned)));
    }

    @Operation(
            summary = "매핑 정합성 리포트",
            description = "미매핑 장비(XN ref 보유 + infra_id NULL)와 다중 ref 참조 장비(오귀속 검수 후보)를 조회합니다."
    )
    @GetMapping("/consistency")
    public ResponseEntity<ApiResponse<ConsistencyResponse>> getConsistency() {
        return ResponseEntity.ok(ApiResponse.success("정합성 리포트", deviceInfraService.getConsistency()));
    }
}
