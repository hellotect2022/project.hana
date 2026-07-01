package com.mpole.hana.digitaltwin.systemMenu.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter;
import com.mpole.hana.digitaltwin.iam.role.service.RoleService;
import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemPermissionRequest;
import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemResponse;
import com.mpole.hana.digitaltwin.systemMenu.service.DeviceSystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device-system/roles")
@RequiredArgsConstructor
@Tag(name = "Device System Permission", description = "디지털트윈 시스템 권한 API")
public class DeviceSystemPermissionController {

    private final DeviceSystemService deviceSystemService;

    //region 🔄 토큰 기반 BMS 시스템 ID 목록 조회 명세서
    @Operation(
            summary = "토큰으로 BMS 시스템 ID 목록 조회",
            description = "현재 로그인한 사용자의 토큰(Role) 정보를 기반으로 접근 가능한 BMS 시스템 ID 목록을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    //endregion
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<DeviceSystemResponse>>> test(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {

        String roleName = principal.authorities().toArray()[0].toString();
        List<String> roleNames = principal.authorities().stream()
                .map(GrantedAuthority::getAuthority) // 또는 구성을 확인 후 적절한 메서드 호출 (보통 getAuthority() 사용)
                .toList();
        return ResponseEntity.ok(
                ApiResponse.success("토큰으로 BMS 시스템 id 목록 조회",
                        deviceSystemService.getDeviceSystemPermissionsByRoleName(roleNames)
                )
        );
    }

    //region 🔄 권한별 BMS 시스템 ID 목록 조회 명세서
    @Operation(
            summary = "권한별 BMS 시스템 ID 목록 조회",
            description = "특정 권한 ID(roleId)를 기준으로 해당 권한이 접근 가능한 BMS 시스템 ID 목록을 조회합니다."
    )
    //endregion
    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<List<Long>>> getDeviceSystemPermissions(
            @PathVariable Long roleId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "권한별 BMS 시스템 id 목록 조회",
                        deviceSystemService.getDeviceSystemPermissions(roleId)
                )
        );
    }


    @PutMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<Void>> updateDeviceSystemPermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody DeviceSystemPermissionRequest request
    ) {
        deviceSystemService.updateDeviceSystemPermissions(roleId, request);
        return ResponseEntity.ok(ApiResponse.success("권한별 BMS 시스템 id 설정 변경"));
    }
}
