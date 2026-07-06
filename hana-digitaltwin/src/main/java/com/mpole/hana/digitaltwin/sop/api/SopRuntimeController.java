package com.mpole.hana.digitaltwin.sop.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopRuntimeDtos.*;
import com.mpole.hana.digitaltwin.sop.service.SopRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sop/instances")
@RequiredArgsConstructor
public class SopRuntimeController {

    private final SopRuntimeService sopRuntimeService;

    @GetMapping("/{instanceId}")
    public ResponseEntity<ApiResponse<SopRuntimePayload>> get(@PathVariable Long instanceId) {
        return ResponseEntity.ok(
                ApiResponse.success("SOP 인스턴스 조회", sopRuntimeService.buildRuntimePayload(instanceId))
        );
    }

    @PostMapping("/{instanceId}/items/{itemId}/complete")
    public ResponseEntity<ApiResponse<Void>> completeItem(
            @PathVariable Long instanceId,
            @PathVariable Long itemId,
            @RequestParam(required = false) Long actorUserId
    ) {
        sopRuntimeService.completeItem(instanceId, itemId, actorUserId);
        return ResponseEntity.ok(ApiResponse.success("항목 완료 처리"));
    }

    @PostMapping("/{instanceId}/steps/{stepNo}/complete")
    public ResponseEntity<ApiResponse<ButtonClickResponse>> completeStep(
            @PathVariable Long instanceId,
            @PathVariable int stepNo
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("스텝 완료", sopRuntimeService.completeStep(instanceId, stepNo))
        );
    }

    @PostMapping("/{instanceId}/buttons/{buttonKey}/click")
    public ResponseEntity<ApiResponse<ButtonClickResponse>> clickButton(
            @PathVariable Long instanceId,
            @PathVariable String buttonKey,
            @RequestBody(required = false) ButtonClickRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("버튼 처리", sopRuntimeService.clickButton(instanceId, buttonKey, request))
        );
    }
}
