package com.mpole.hana.digitaltwin.monitor.api;

import com.mpole.hana.common.monitor.dto.MonitorSnapshot;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.monitor.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * event / gateway / digitaltwin 3개 서비스의 생존 상태 + 리소스 사용량을
 * 단일 API로 통합 조회하는 관제용 aggregator 컨트롤러.
 *
 * <p>운영/관제용 민감 API이므로 기존 JWT 인증 필터를 그대로 통과해야 한다.
 * SecurityConfig는 수정하지 않는다.
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
@Tag(name = "Monitor Aggregator", description = "서비스 생존/리소스 통합 모니터링 API")
public class MonitorController {

    private final MonitorService monitorService;

    //region 🔄 서비스 통합 모니터링 스냅샷 조회 명세서
    @Operation(
            summary = "서비스 통합 모니터링 스냅샷 조회",
            description = "event / gateway / digitaltwin 3개 서비스의 Spring Boot Actuator를 폴링해 "
                    + "생존 상태(LiveStatus), JVM/프로세스 리소스 사용량, 서비스별 PID/시작시각을 반환하고, "
                    + "aggregator가 도는 호스트(digitaltwin) 기준 상단 카드용 host 요약"
                    + "(CPU/메모리/디스크 사용률 0.0~1.0 분수, DB/네트워크 상태)을 단일 스냅샷으로 반환합니다. "
                    + "host.memoryUsage는 호스트 물리메모리가 아니라 JVM 힙 사용률(heapUsed/heapMax)입니다. "
                    + "리소스 metric 수집 실패 필드는 -1.0(double) 또는 0, 상태 미확인은 UNKNOWN으로 표기됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    //endregion
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<MonitorSnapshot>> getStatus() {
        MonitorSnapshot snapshot = monitorService.getSnapshot();
        return ResponseEntity.ok(ApiResponse.success("서비스 통합 모니터링 스냅샷 조회", snapshot));
    }
}
