package com.mpole.hana.digitaltwin.event.api;

import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventInstanceCreateRequest;
import com.mpole.hana.digitaltwin.event.dto.EventRuleDtos.EventInstanceSummary;
import com.mpole.hana.digitaltwin.event.service.EventInstanceService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopRuntimeDtos.EventDisplayResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventInstanceController {

    private final EventInstanceService eventInstanceService;

    @GetMapping("/instances")
    public ResponseEntity<ApiResponse<List<EventInstanceSummary>>> listOpen(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("활성 이벤트 목록", eventInstanceService.listOpen(limit)));
    }

    @PostMapping("/instances")
    public ResponseEntity<ApiResponse<EventInstanceSummary>> create(
            @Valid @RequestBody EventInstanceCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("이벤트 생성", eventInstanceService.create(request)));
    }

    @GetMapping("/instances/{eventId}/display")
    public ResponseEntity<ApiResponse<EventDisplayResponse>> display(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.success("이벤트 표시 스펙", eventInstanceService.getDisplay(eventId)));
    }
}
