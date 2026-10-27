package com.mpole.hana.digitaltwin.dashboard.situation.api;

import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationDailyUsageDetailResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationDailyUsageKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationEventKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationFireKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationBoardResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationIndoorEnvKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationLightingKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationParkingKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationPowerKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationRenewKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationTopKpiItemResponse;
import com.mpole.hana.digitaltwin.dashboard.situation.service.DashboardSituationService;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsEnergyTrendResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsEventTrendResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsEventTypeResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsFailureTopResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsInspectionResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.service.StatisticsEnergyTrendService;
import com.mpole.hana.digitaltwin.dashboard.statistics.service.StatisticsEventTrendService;
import com.mpole.hana.digitaltwin.dashboard.statistics.service.StatisticsEventTypeService;
import com.mpole.hana.digitaltwin.dashboard.statistics.service.StatisticsFailureTopService;
import com.mpole.hana.digitaltwin.dashboard.statistics.service.StatisticsInspectionService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard/situation")
@RequiredArgsConstructor
public class DashboardSituationController {

    private final DashboardSituationService dashboardSituationService;
    private final StatisticsEnergyTrendService statisticsEnergyTrendService;
    private final StatisticsEventTrendService statisticsEventTrendService;
    private final StatisticsEventTypeService statisticsEventTypeService;
    private final StatisticsFailureTopService statisticsFailureTopService;
    private final StatisticsInspectionService statisticsInspectionService;

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

    @GetMapping("/event-kpi")
    public ResponseEntity<ApiResponse<SituationEventKpiResponse>> getEventKpi() {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 금일 이벤트 발생",
                dashboardSituationService.getEventKpi()
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

    /** 통합현황 — 금일 전력·가스 KPI 상세 (누적 포인트별, {@code type=power|gas}). */
    @GetMapping("/daily-usage-detail")
    public ResponseEntity<ApiResponse<SituationDailyUsageDetailResponse>> getDailyUsageDetail(
            @RequestParam String type) {
        return ResponseEntity.ok(ApiResponse.success(
                "통합현황 금일 전력·가스 사용량 상세",
                dashboardSituationService.getDailyUsageDetail(type)
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

    /** 통계·리포트 에너지 추이 (구버전 백엔드 호환 — /statistics 미배포 시). */
    @GetMapping("/statistics-energy-trend")
    public ResponseEntity<ApiResponse<StatisticsEnergyTrendResponse>> getStatisticsEnergyTrend(
            @RequestParam(defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success(
                "에너지 사용량 추이",
                statisticsEnergyTrendService.getEnergyTrend(period)
        ));
    }

    /** 통계·리포트 이벤트 추이 (구버전 백엔드 호환). */
    @GetMapping("/statistics-event-trend")
    public ResponseEntity<ApiResponse<StatisticsEventTrendResponse>> getStatisticsEventTrend(
            @RequestParam(defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success(
                "이벤트 발생 추이",
                statisticsEventTrendService.getEventTrend(period)
        ));
    }

    @GetMapping("/statistics-event-types")
    public ResponseEntity<ApiResponse<StatisticsEventTypeResponse>> getStatisticsEventTypes(
            @RequestParam(defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success(
                "이벤트 유형별 분석",
                statisticsEventTypeService.getEventTypes(period)
        ));
    }

    @GetMapping("/statistics-failure-top")
    public ResponseEntity<ApiResponse<StatisticsFailureTopResponse>> getStatisticsFailureTop(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "all") String system) {
        return ResponseEntity.ok(ApiResponse.success(
                "설비 장애 TOP 10",
                statisticsFailureTopService.getFailureTop(period, system)
        ));
    }

    @GetMapping("/statistics-inspection-status")
    public ResponseEntity<ApiResponse<StatisticsInspectionResponse>> getStatisticsInspectionStatus(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "all") String system) {
        return ResponseEntity.ok(ApiResponse.success(
                "설비 점검 현황",
                statisticsInspectionService.getInspectionStatus(period, system)
        ));
    }
}
