package com.mpole.hana.digitaltwin.iam.role.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.iam.role.dto.RoleRequest;
import com.mpole.hana.digitaltwin.iam.role.dto.RoleResponse;
import com.mpole.hana.digitaltwin.iam.role.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoles() {
        return ResponseEntity.ok(
                ApiResponse.success("권한 목록 조회", roleService.getRoles())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody RoleRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("권한 생성", roleService.createRole(request))
        );
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Void>> updateRole(
            @PathVariable Long roleId,
            @Valid @RequestBody RoleRequest request
    ) {
        roleService.updateRole(roleId, request);
        return ResponseEntity.ok(ApiResponse.success("권한 수정"));
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.ok(ApiResponse.success("권한 삭제"));
    }
}
