package com.mpole.hana.digitaltwin.event.api;

import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventRuleRequest;
import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventRuleResponse;
import com.mpole.hana.digitaltwin.event.service.EventRuleService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/event/rules")
@RequiredArgsConstructor
public class EventRuleController {

    private final EventRuleService eventRuleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventRuleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 목록", eventRuleService.findAll()));
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<ApiResponse<EventRuleResponse>> get(@PathVariable Long ruleId) {
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 조회", eventRuleService.findById(ruleId)));
    }

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<ApiResponse<List<EventRuleResponse>>> byDevice(@PathVariable Long deviceId) {
        return ResponseEntity.ok(ApiResponse.success("장비별 규칙", eventRuleService.findByDevice(deviceId)));
    }

    @GetMapping("/severity/{severity}")
    public ResponseEntity<ApiResponse<List<EventRuleResponse>>> bySeverity(@PathVariable String severity) {
        return ResponseEntity.ok(ApiResponse.success("심각도별 규칙", eventRuleService.findBySeverity(severity)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventRuleResponse>> create(@Valid @RequestBody EventRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 생성", eventRuleService.create(request)));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<ApiResponse<EventRuleResponse>> update(
            @PathVariable Long ruleId,
            @Valid @RequestBody EventRuleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 수정", eventRuleService.update(ruleId, request)));
    }

    @PatchMapping("/{ruleId}/toggle")
    public ResponseEntity<ApiResponse<EventRuleResponse>> toggle(@PathVariable Long ruleId) {
        return ResponseEntity.ok(ApiResponse.success("활성 토글", eventRuleService.toggleActive(ruleId)));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long ruleId) {
        eventRuleService.delete(ruleId);
        return ResponseEntity.ok(ApiResponse.success("이벤트 규칙 삭제"));
    }
}
