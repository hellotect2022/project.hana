package com.mpole.hana.digitaltwin.space.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.space.dto.ZoneDisplayPolicyRequest;
import com.mpole.hana.digitaltwin.space.dto.ZoneDisplayPolicyResponse;
import com.mpole.hana.digitaltwin.space.dto.ZonePolicyUpdateItem;
import com.mpole.hana.digitaltwin.space.dto.ZonePolicyWithZoneRequest;
import com.mpole.hana.digitaltwin.space.dto.ZoneStatusSummaryResponse;
import com.mpole.hana.digitaltwin.space.entity.ZoneSystemType;
import com.mpole.hana.digitaltwin.space.service.ZoneDisplayPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 공간(구역)·조명/화재감지 표현정책 관리 (WA-SPACE-LIGHT) */
@RestController
@RequestMapping("/api/space/zone-policies")
@RequiredArgsConstructor
public class ZoneDisplayPolicyController {

    private final ZoneDisplayPolicyService service;

    /** 표현정책 목록 (systemType=LIGHTING|FIRE_DETECTION) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ZoneDisplayPolicyResponse>>> getPolicies(
            @RequestParam String systemType) {
        var list = service.getPolicies(ZoneSystemType.fromCode(systemType));
        return ResponseEntity.ok(ApiResponse.success("구역 표현정책 목록", list));
    }

    /** 구역 현황 통계 (전체/ON/OFF/Fault) */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ZoneStatusSummaryResponse>> getSummary(
            @RequestParam String systemType) {
        var summary = service.getSummary(ZoneSystemType.fromCode(systemType));
        return ResponseEntity.ok(ApiResponse.success("구역 현황 통계", summary));
    }

    /** 표현정책 등록 (기존 Zone 선택) */
    @PostMapping
    public ResponseEntity<ApiResponse<ZoneDisplayPolicyResponse>> create(
            @Valid @RequestBody ZoneDisplayPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("구역 표현정책 등록", service.create(request)));
    }

    /** 조명/화재 구역 추가 — 새 Zone(3D) + 표현정책 동시 생성 */
    @PostMapping("/with-zone")
    public ResponseEntity<ApiResponse<ZoneDisplayPolicyResponse>> createWithZone(
            @Valid @RequestBody ZonePolicyWithZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success("구역 생성", service.createWithNewZone(request)));
    }

    /** 정책 저장 (변경된 표현정책 일괄 저장) */
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> saveAll(
            @Valid @RequestBody List<ZonePolicyUpdateItem> items) {
        service.saveAll(items);
        return ResponseEntity.ok(ApiResponse.success("표현정책 저장"));
    }

    /** 표현정책 삭제 */
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long ruleId) {
        service.delete(ruleId);
        return ResponseEntity.ok(ApiResponse.success("표현정책 삭제"));
    }
}
