package com.mpole.hana.digitaltwin.dashboard.power.api;

import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerPageSnapshotResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerSectionResponse;
import com.mpole.hana.digitaltwin.dashboard.power.service.DashboardPowerService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/power")
@RequiredArgsConstructor
public class DashboardPowerController {

    private final DashboardPowerService dashboardPowerService;

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PowerPageSnapshotResponse>> getPage() {
        return ResponseEntity.ok(ApiResponse.success(
                "전력감시 대시보드",
                dashboardPowerService.getPageSnapshot()
        ));
    }

    @GetMapping("/kpi")
    public ResponseEntity<ApiResponse<PowerKpiResponse>> getKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "전력감시 KPI",
                dashboardPowerService.getKpi()
        ));
    }

    @GetMapping("/section")
    public ResponseEntity<ApiResponse<PowerSectionResponse>> getSection(
            @RequestParam(name = "tab", defaultValue = "ehv") String tab) {
        return ResponseEntity.ok(ApiResponse.success(
                "전력 구간 현황",
                dashboardPowerService.getSection(tab)
        ));
    }
}
