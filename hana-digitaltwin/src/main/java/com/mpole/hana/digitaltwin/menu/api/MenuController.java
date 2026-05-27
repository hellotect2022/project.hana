package com.mpole.hana.digitaltwin.menu.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.menu.dto.MenuCreateRequest;
import com.mpole.hana.digitaltwin.menu.dto.MenuResponse;
import com.mpole.hana.digitaltwin.menu.dto.MenuSortOrderRequest;
import com.mpole.hana.digitaltwin.menu.dto.MenuUpdateRequest;
import com.mpole.hana.digitaltwin.menu.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getMenus() {
        return ResponseEntity.ok(
                ApiResponse.success("메뉴 목록 조회", menuService.getMenus())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createMenu(
            @Valid @RequestBody MenuCreateRequest request
    ) {
        menuService.createMenu(request);
        return ResponseEntity.ok(ApiResponse.success("메뉴 생성"));
    }

    @PutMapping("/{menuId}")
    public ResponseEntity<ApiResponse<Void>> updateMenu(
            @PathVariable Long menuId,
            @Valid @RequestBody MenuUpdateRequest request
    ) {
        menuService.updateMenu(menuId, request);
        return ResponseEntity.ok(ApiResponse.success("메뉴 정보 수정"));
    }

    @PutMapping("/sort-orders")
    public ResponseEntity<ApiResponse<Void>> updateMenuSortOrders(
            @Valid @RequestBody List<MenuSortOrderRequest> requests
    ) {
        menuService.updateMenuSortOrders(requests);
        return ResponseEntity.ok(ApiResponse.success("메뉴 순서 변경 저장"));
    }

    @DeleteMapping("/{menuId}")
    public ResponseEntity<ApiResponse<Void>> deleteMenu(
            @PathVariable Long menuId
    ) {
        menuService.deleteMenu(menuId);
        return ResponseEntity.ok(ApiResponse.success("메뉴 삭제"));
    }
}
