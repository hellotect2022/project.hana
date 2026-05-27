package com.mpole.hana.digitaltwin.menu.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.menu.dto.MenuPermissionRequest;
import com.mpole.hana.digitaltwin.menu.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu/roles")
@RequiredArgsConstructor
public class MenuPermissionController {
    private final MenuService menuService;

    @GetMapping("/{roleId}/menu-permissions")
    public ResponseEntity<ApiResponse<List<Long>>> getMenuPermissions(
            @PathVariable Long roleId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "권한별 메뉴 id 목록 조회",
                        menuService.getMenuPermissions(roleId)
                )
        );
    }

    @PutMapping("/{roleId}/menu-permissions")
    public ResponseEntity<ApiResponse<Void>> updateMenuPermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody MenuPermissionRequest request
    ) {
        menuService.updateMenuPermissions(roleId, request);
        return ResponseEntity.ok(ApiResponse.success("권한별 메뉴 id 설정 변경"));
    }
}
