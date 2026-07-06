package com.mpole.hana.digitaltwin.dashboard.mechanical.api;

import com.mpole.hana.digitaltwin.dashboard.mechanical.dto.MechanicalEquipmentKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.mechanical.dto.MechanicalPageBoardResponse;
import com.mpole.hana.digitaltwin.dashboard.mechanical.dto.MechanicalPageSnapshotResponse;
import com.mpole.hana.digitaltwin.dashboard.mechanical.dto.MechanicalSituationPanelResponse;
import com.mpole.hana.digitaltwin.dashboard.mechanical.service.DashboardMechanicalService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/mechanical")
@RequiredArgsConstructor
public class DashboardMechanicalController {

    private final DashboardMechanicalService dashboardMechanicalService;

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<MechanicalPageSnapshotResponse>> getPage() {
        return ResponseEntity.ok(ApiResponse.success(
                "기계설비 대시보드",
                dashboardMechanicalService.getPageSnapshot()
        ));
    }

    @GetMapping("/situation-equipment")
    public ResponseEntity<ApiResponse<MechanicalSituationPanelResponse>> getSituationEquipment() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 기계설비",
                dashboardMechanicalService.getSituationEquipment()
        ));
    }

    @GetMapping("/equipment-kpi")
    public ResponseEntity<ApiResponse<MechanicalEquipmentKpiResponse>> getEquipmentKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 전체 설비 가동 현황",
                dashboardMechanicalService.getEquipmentKpi()
        ));
    }

    @GetMapping("/page-board/{tabId}")
    public ResponseEntity<ApiResponse<MechanicalPageBoardResponse>> getPageBoard(@PathVariable String tabId) {
        return ResponseEntity.ok(ApiResponse.success(
                "건축설비 탭별 현황판",
                dashboardMechanicalService.getPageBoard(tabId)
        ));
    }
}
