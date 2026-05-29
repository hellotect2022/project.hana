package com.mpole.hana.digitaltwin.dashboard.mechanical.api;

import com.mpole.hana.digitaltwin.dashboard.mechanical.dto.MechanicalPageSnapshotResponse;
import com.mpole.hana.digitaltwin.dashboard.mechanical.service.DashboardMechanicalService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
