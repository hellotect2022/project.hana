package com.mpole.hana.digitaltwin.systemMenu.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter;
import com.mpole.hana.digitaltwin.iam.role.service.RoleService;
import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemPermissionRequest;
import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemResponse;
import com.mpole.hana.digitaltwin.systemMenu.service.DeviceSystemService;
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
public class DeviceSystemPermissionController {

    private final DeviceSystemService deviceSystemService;

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
