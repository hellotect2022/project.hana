package com.mpole.hana.digitaltwin.dashboard.situation.api;

import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationDailyUsageKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationFireKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationBoardResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationIndoorEnvKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationLightingKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationParkingKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationPowerKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationRenewKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationTopKpiItemResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.service.DashboardSituationService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard/situation")
@RequiredArgsConstructor
public class DashboardSituationController {

    private final DashboardSituationService dashboardSituationService;

    @GetMapping("/top-kpis")
    public ResponseEntity<ApiResponse<List<SituationTopKpiItemResponse>>> getTopKpis() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 상단 KPI",
                dashboardSituationService.getTopKpis()
        ));
    }

    @GetMapping("/board")
    public ResponseEntity<ApiResponse<SituationBoardResponse>> getBoard() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 운영 현황판",
                dashboardSituationService.getBoard()
        ));
    }

    @GetMapping("/fire-kpi")
    public ResponseEntity<ApiResponse<SituationFireKpiResponse>> getFireKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 소방방재",
                dashboardSituationService.getFireKpi()
        ));
    }

    @GetMapping("/lighting-kpi")
    public ResponseEntity<ApiResponse<SituationLightingKpiResponse>> getLightingKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 조명제어",
                dashboardSituationService.getLightingKpi()
        ));
    }

    @GetMapping("/parking-kpi")
    public ResponseEntity<ApiResponse<SituationParkingKpiResponse>> getParkingKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 주차관리",
                dashboardSituationService.getParkingKpi()
        ));
    }

    @GetMapping("/daily-usage-kpi")
    public ResponseEntity<ApiResponse<SituationDailyUsageKpiResponse>> getDailyUsageKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 금일 전력·가스 사용량",
                dashboardSituationService.getDailyUsageKpi()
        ));
    }

    @GetMapping("/renew-kpi")
    public ResponseEntity<ApiResponse<SituationRenewKpiResponse>> getRenewKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 신재생 에너지 생산 KPI",
                dashboardSituationService.getRenewKpi()
        ));
    }

    @GetMapping("/indoor-env-kpi")
    public ResponseEntity<ApiResponse<SituationIndoorEnvKpiResponse>> getIndoorEnvKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 기준층 실내 환경 KPI",
                dashboardSituationService.getIndoorEnvKpi()
        ));
    }

    @GetMapping("/power-kpi")
    public ResponseEntity<ApiResponse<SituationPowerKpiResponse>> getPowerKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 전력감시 KPI",
                dashboardSituationService.getPowerMonitorKpi()
        ));
    }
}
