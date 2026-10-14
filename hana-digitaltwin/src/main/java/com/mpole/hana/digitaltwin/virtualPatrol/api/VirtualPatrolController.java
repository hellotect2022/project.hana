package com.mpole.hana.digitaltwin.virtualPatrol.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.virtualPatrol.dto.VirtualPatrolRequest;
import com.mpole.hana.digitaltwin.virtualPatrol.dto.VirtualPatrolResponse;
import com.mpole.hana.digitaltwin.virtualPatrol.service.VirtualPatrolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 가상순찰 경로 CRUD (Unity PC 클라이언트) */
@RestController
@RequestMapping("/api/virtual-patrol")
@RequiredArgsConstructor
@Tag(name = "VirtualPatrol", description = "가상순찰 경로 CRUD API (Unity PC 클라이언트)")
public class VirtualPatrolController {

    private final VirtualPatrolService service;

    @Operation(
            summary = "가상순찰 경로 생성",
            description = "경로를 생성한다. routeId 는 클라이언트(Unity)가 생성한 UUID 를 body 로 전달한다(자동생성 아님). "
                    + "이미 존재하는 routeId 면 409. waypoints 는 지점 리스트(위치 x,y,z + 회전 쿼터니언 qx,qy,qz,qw)."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<VirtualPatrolResponse>> create(
            @Valid @RequestBody VirtualPatrolRequest request) {
        return ResponseEntity.ok(ApiResponse.success("가상순찰 경로 생성", service.create(request)));
    }

    @Operation(
            summary = "가상순찰 경로 목록 조회",
            description = "전체 경로를 북마크 슬롯 우선(오름차순, 미북마크는 뒤) → 생성순(createdAt)으로 반환한다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<VirtualPatrolResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("가상순찰 경로 목록", service.getAll()));
    }

    @Operation(
            summary = "가상순찰 경로 단건 조회",
            description = "routeId(UUID)로 단일 경로를 조회한다.",
            parameters = {@Parameter(name = "routeId", description = "경로 UUID(클라이언트 생성 PK)", required = true,
                    example = "3f2a7c10-9b1e-4a2c-8f3d-6b0a1e2c4d55")}
    )
    @GetMapping("/{routeId}")
    public ResponseEntity<ApiResponse<VirtualPatrolResponse>> getOne(@PathVariable String routeId) {
        return ResponseEntity.ok(ApiResponse.success("가상순찰 경로 단건", service.getOne(routeId)));
    }

    @Operation(
            summary = "가상순찰 경로 수정(전체 교체)",
            description = "routeId(UUID) 경로를 body 값으로 전체 교체한다. bookmarkSlot=null 이면 즐겨찾기 해제, "
                    + "지정 슬롯을 이미 다른 경로가 점유 중이면 그 경로의 슬롯을 해제 후 부여한다.",
            parameters = {@Parameter(name = "routeId", description = "경로 UUID", required = true,
                    example = "3f2a7c10-9b1e-4a2c-8f3d-6b0a1e2c4d55")}
    )
    @PutMapping("/{routeId}")
    public ResponseEntity<ApiResponse<VirtualPatrolResponse>> update(
            @PathVariable String routeId,
            @Valid @RequestBody VirtualPatrolRequest request) {
        return ResponseEntity.ok(ApiResponse.success("가상순찰 경로 수정", service.update(routeId, request)));
    }

    @Operation(
            summary = "가상순찰 경로 삭제",
            description = "routeId(UUID) 경로를 삭제한다. 없으면 404.",
            parameters = {@Parameter(name = "routeId", description = "경로 UUID", required = true,
                    example = "3f2a7c10-9b1e-4a2c-8f3d-6b0a1e2c4d55")}
    )
    @DeleteMapping("/{routeId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String routeId) {
        service.delete(routeId);
        return ResponseEntity.ok(ApiResponse.success("가상순찰 경로 삭제"));
    }
}
