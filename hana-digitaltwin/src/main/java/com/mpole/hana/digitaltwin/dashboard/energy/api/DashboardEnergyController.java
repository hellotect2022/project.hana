package com.mpole.hana.digitaltwin.dashboard.energy.api;

import com.mpole.hana.digitaltwin.dashboard.energy.dto.EnergyGasKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.energy.service.DashboardEnergyService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/energy")
@RequiredArgsConstructor
public class DashboardEnergyController {

    private final DashboardEnergyService dashboardEnergyService;

    @GetMapping("/gas-kpi")
    public ResponseEntity<ApiResponse<EnergyGasKpiResponse>> getGasKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "에너지 가스 KPI",
                dashboardEnergyService.getGasKpi()
        ));
    }
}
