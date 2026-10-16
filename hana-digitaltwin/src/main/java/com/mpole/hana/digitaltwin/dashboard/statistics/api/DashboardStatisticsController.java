package com.mpole.hana.digitaltwin.dashboard.statistics.api;

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

@RestController
@RequestMapping("/api/dashboard/statistics")
@RequiredArgsConstructor
public class DashboardStatisticsController {

    private final StatisticsEnergyTrendService statisticsEnergyTrendService;
    private final StatisticsEventTrendService statisticsEventTrendService;
    private final StatisticsEventTypeService statisticsEventTypeService;
    private final StatisticsFailureTopService statisticsFailureTopService;
    private final StatisticsInspectionService statisticsInspectionService;

    /**
     * 에너지 사용량 추이 — period: day|week|month|quarter|year (통계 화면 탭과 동일).
     */
    @GetMapping("/energy-trend")
    public ResponseEntity<ApiResponse<StatisticsEnergyTrendResponse>> getEnergyTrend(
            @RequestParam(defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success(
                "에너지 사용량 추이",
                statisticsEnergyTrendService.getEnergyTrend(period)
        ));
    }

    /**
     * 이벤트 발생 추이 — period: day|week|month|quarter|year (통계 화면 탭과 동일).
     */
    @GetMapping("/event-trend")
    public ResponseEntity<ApiResponse<StatisticsEventTrendResponse>> getEventTrend(
            @RequestParam(defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success(
                "이벤트 발생 추이",
                statisticsEventTrendService.getEventTrend(period)
        ));
    }

    /**
     * 이벤트 유형별 분석 — period: day|week|month|quarter|year (tbl_event.level 집계).
     */
    @GetMapping("/event-types")
    public ResponseEntity<ApiResponse<StatisticsEventTypeResponse>> getEventTypes(
            @RequestParam(defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success(
                "이벤트 유형별 분석",
                statisticsEventTypeService.getEventTypes(period)
        ));
    }

    /**
     * 설비 장애 TOP — period + system(all|power|mech|building|fire).
     */
    @GetMapping("/failure-top")
    public ResponseEntity<ApiResponse<StatisticsFailureTopResponse>> getFailureTop(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "all") String system) {
        return ResponseEntity.ok(ApiResponse.success(
                "설비 장애 TOP 10",
                statisticsFailureTopService.getFailureTop(period, system)
        ));
    }

    /** 설비 점검 현황 — period + system. */
    @GetMapping("/inspection-status")
    public ResponseEntity<ApiResponse<StatisticsInspectionResponse>> getInspectionStatus(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "all") String system) {
        return ResponseEntity.ok(ApiResponse.success(
                "설비 점검 현황",
                statisticsInspectionService.getInspectionStatus(period, system)
        ));
    }
}
