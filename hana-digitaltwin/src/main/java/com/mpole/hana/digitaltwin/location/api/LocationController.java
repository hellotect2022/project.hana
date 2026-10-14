//package com.mpole.hana.digitaltwin.location.api;
//
//import com.mpole.hana.digitaltwin.global.response.ApiResponse;
//import com.mpole.hana.digitaltwin.location.dto.UnityLocationResponse;
//import com.mpole.hana.digitaltwin.location.service.LocationService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/device")
//@RequiredArgsConstructor
////@Tag(name = "Location", description = "Unity 위치 트리(건물 › 층 › 구역) 조회")
//@Tag(name = "Location", description = "Unity 위치 트리(건물 › 층 › 구역) 조회")
//public class LocationController {
//
//    private final LocationService locationService;
//
//    @Operation(
//            summary = "Unity 위치 트리 조회",
//            description = """
//                    건물 › 층 › 구역 전체를 한 번에 내려준다. Unity 3D 뷰어가 기동 시 씬 구성·구역 판정에 쓴다.
//                    · 건물: buildingId, buildingName, floorList
//                    · 층: floorId, floorNum(실제 층수, 지하는 음수 — B2F = -2), sortOrder(0 = 최하층 B7F → 위로 증가, 배열은 이 순서로 정렬됨),
//                      floorName, description(층 설명 — 입주사·용도, 예 하나은행 / 주차장. 없으면 null. 2026-10-14 추가), zoneList
//                    · 구역: zoneId, zoneName, zoneMeshName(Unity 메시 이름, 항상 값 있음), parentId(상위 구역, 최상위면 null),
//                      depth(0 = 최상위, 1 = 자식), zoneCode(외부 연동 식별자)
//                    · zoneList 는 평면 배열이다. 중첩 구역은 parentId/depth 로 트리를 복원하고, 레이 판정 시 depth 가 가장 깊은 구역을 고른다.
//                    """
//    )
//    @GetMapping("/location-info")
//    public ResponseEntity<ApiResponse<List<UnityLocationResponse>>> getLocationInfo() {
//        return ResponseEntity.ok(ApiResponse.success(
//                "Unity 위치 트리", locationService.findUnityLocationTree()));
//    }
//}
