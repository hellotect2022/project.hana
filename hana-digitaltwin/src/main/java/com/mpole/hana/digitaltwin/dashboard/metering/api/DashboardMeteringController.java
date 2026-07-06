package com.mpole.hana.digitaltwin.dashboard.metering.api;

import com.mpole.hana.digitaltwin.dashboard.metering.dto.MeteringSituationPanelResponse;
import com.mpole.hana.digitaltwin.dashboard.metering.service.DashboardMeteringService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/metering")
@RequiredArgsConstructor
public class DashboardMeteringController {

    private final DashboardMeteringService dashboardMeteringService;

    @GetMapping("/situation-utilities")
    public ResponseEntity<ApiResponse<MeteringSituationPanelResponse>> getSituationUtilities() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 원격검침",
                dashboardMeteringService.getSituationUtilities()
        ));
    }
}
