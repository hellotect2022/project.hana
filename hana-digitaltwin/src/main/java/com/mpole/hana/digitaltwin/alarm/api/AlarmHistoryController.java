package com.mpole.hana.digitaltwin.alarm.api;

import com.mpole.hana.digitaltwin.alarm.dto.AlarmHistoryPageResponse;
import com.mpole.hana.digitaltwin.alarm.dto.AlarmHistoryResponse;
import com.mpole.hana.digitaltwin.alarm.service.AlarmHistoryService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * 알람 발생 이력 조회 API (읽기 전용).
 * 이력 쓰기는 event-server(R2DBC)가 담당하며, 여기서는 client 조회만 제공한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/alarm/history")
@RequiredArgsConstructor
@Tag(name = "AlarmHistory", description = "알람 발생 이력 조회 API (읽기 전용)")
public class AlarmHistoryController {

    private final AlarmHistoryService alarmHistoryService;

    @Operation(
            summary = "알람 이력 목록 조회",
            description = "알람 발생 이력을 발생시각(occurredAt) 최신순으로 페이징 조회합니다. "
                    + "level/deviceId/from~to(occurredAt) 로 선택 필터링할 수 있습니다.",
            parameters = {
                    @Parameter(name = "level", description = "심각도 등급(CAUTION/WARNING/CRITICAL)", example = "CRITICAL"),
                    @Parameter(name = "deviceId", description = "장비 ID", example = "1001"),
                    @Parameter(name = "from", description = "발생시각 시작(ISO-8601, 예: 2026-07-01T00:00:00+09:00)"),
                    @Parameter(name = "to", description = "발생시각 종료(ISO-8601)"),
                    @Parameter(name = "page", description = "페이지 번호(0-base)", example = "0"),
                    @Parameter(name = "size", description = "페이지 크기", example = "20")
            }
    )
    @GetMapping
    public ResponseEntity<ApiResponse<AlarmHistoryPageResponse>> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        AlarmHistoryPageResponse result = alarmHistoryService.getHistory(level, deviceId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success("알람 이력 목록 조회", result));
    }

    @Operation(
            summary = "알람 이력 상세 조회",
            description = "알람 ID로 단일 알람 발생 이력을 조회합니다.",
            parameters = {@Parameter(name = "id", description = "알람 이력 ID", example = "1", required = true)}
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AlarmHistoryResponse>> get(@PathVariable Long id) {
        AlarmHistoryResponse history = alarmHistoryService.get(id);
        return ResponseEntity.ok(ApiResponse.success("알람 이력 상세 조회", history));
    }
}
