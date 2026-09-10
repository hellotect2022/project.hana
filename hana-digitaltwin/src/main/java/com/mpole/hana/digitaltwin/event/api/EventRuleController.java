package com.mpole.hana.digitaltwin.event.api;

import com.mpole.hana.digitaltwin.event.dto.EventRuleRequest;
import com.mpole.hana.digitaltwin.event.dto.EventRuleResponse;
import com.mpole.hana.digitaltwin.event.service.EventRuleService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 이벤트 규칙 관리 API — 무엇을 감시하고, 걸리면 어떤 채널을 켤지의 설정.
 * 실제 발생 기록은 {@link EventController}, 채널별 발행은 이벤트 액션이 담당한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/event/rules")
@RequiredArgsConstructor
@Tag(name = "EventRule", description = "이벤트 규칙 관리 API")
public class EventRuleController {

    private final EventRuleService eventRuleService;

    @Operation(summary = "이벤트 규칙 등록", description = "카테고리/장비 범위의 임계치(THRESHOLD) 또는 토글(TOGGLE) 규칙과 심각도 레벨을 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<EventRuleResponse>> create(@RequestBody EventRuleRequest request) {
        EventRuleResponse created = eventRuleService.create(request);
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 등록", created));
    }

    @Operation(summary = "이벤트 규칙 목록 조회", description = "등록된 이벤트 규칙 전체를 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventRuleResponse>>> list() {
        List<EventRuleResponse> rules = eventRuleService.list();
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 목록 조회", rules));
    }

    @Operation(
            summary = "이벤트 규칙 상세 조회",
            description = "규칙 ID로 단일 이벤트 규칙과 레벨 상세를 조회합니다.",
            parameters = {@Parameter(name = "id", description = "이벤트 규칙 ID", example = "1", required = true)}
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventRuleResponse>> get(@PathVariable Long id) {
        EventRuleResponse rule = eventRuleService.get(id);
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 상세 조회", rule));
    }

    @Operation(
            summary = "이벤트 규칙 수정",
            description = "규칙 ID의 이벤트 규칙을 수정합니다. 레벨 목록은 전체 교체됩니다.",
            parameters = {@Parameter(name = "id", description = "이벤트 규칙 ID", example = "1", required = true)}
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventRuleResponse>> update(
            @PathVariable Long id,
            @RequestBody EventRuleRequest request) {
        EventRuleResponse updated = eventRuleService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 수정", updated));
    }

    @Operation(
            summary = "이벤트 규칙 삭제",
            description = "규칙 ID의 이벤트 규칙과 하위 레벨을 삭제합니다.",
            parameters = {@Parameter(name = "id", description = "이벤트 규칙 ID", example = "1", required = true)}
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        eventRuleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 삭제"));
    }
}
