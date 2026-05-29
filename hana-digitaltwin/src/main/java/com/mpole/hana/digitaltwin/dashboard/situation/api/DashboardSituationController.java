package com.mpole.hana.digitaltwin.dashboard.situation.api;

import com.mpole.hana.digitaltwin.dashboard.situation.dto.SituationBoardResponse;
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
}
