package com.mpole.hana.digitaltwin.iam.role.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.iam.role.dto.RoleUserResponse;
import com.mpole.hana.digitaltwin.iam.role.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleUserController {

    private final RoleService roleService;

    @GetMapping("/{roleId}/users")
    public ResponseEntity<ApiResponse<List<RoleUserResponse>>> getUsersInRole(
            @PathVariable Long roleId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("권한별 소속 사용자 목록", roleService.getUsersInRole(roleId))
        );
    }

    @GetMapping("/{roleId}/users/exclude")
    public ResponseEntity<ApiResponse<List<RoleUserResponse>>> getUsersExceptRole(
            @PathVariable Long roleId,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("권한 제외 사용자 목록", roleService.getUsersExceptRole(roleId, search))
        );
    }

    @PostMapping("/{roleId}/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> addUserToRole(
            @PathVariable Long roleId,
            @PathVariable Long userId
    ) {
        roleService.addUserToRole(roleId, userId);
        return ResponseEntity.ok(ApiResponse.success("권한에 사용자 추가"));
    }

    @DeleteMapping("/{roleId}/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeUserFromRole(
            @PathVariable Long roleId,
            @PathVariable Long userId
    ) {
        roleService.removeUserFromRole(roleId, userId);
        return ResponseEntity.ok(ApiResponse.success("권한에서 사용자 제거"));
    }
}
