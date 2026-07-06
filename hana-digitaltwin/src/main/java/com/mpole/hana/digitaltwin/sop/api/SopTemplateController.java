package com.mpole.hana.digitaltwin.sop.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.sop.dto.*;
import com.mpole.hana.digitaltwin.sop.dto.SopStructureDtos.SopTemplateDetailResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopStructureDtos.SopTemplateStructureRequest;
import com.mpole.hana.digitaltwin.sop.service.SopTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sop/templates")
@RequiredArgsConstructor
public class SopTemplateController {

    private final SopTemplateService sopTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SopTemplateSummaryResponse>>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 목록", sopTemplateService.listTemplates(activeOnly)));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponse<SopTemplateDetailResponse>> getDetail(@PathVariable Long templateId) {
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 상세", sopTemplateService.getTemplateDetail(templateId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SopTemplateSummaryResponse>> create(
            @Valid @RequestBody SopTemplateCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 생성", sopTemplateService.createTemplate(request)));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable Long templateId,
            @Valid @RequestBody SopTemplateUpdateRequest request
    ) {
        sopTemplateService.updateTemplate(templateId, request);
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 수정"));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long templateId) {
        sopTemplateService.deleteTemplate(templateId);
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 비활성화"));
    }

    @PutMapping("/{templateId}/structure")
    public ResponseEntity<ApiResponse<SopTemplateDetailResponse>> saveStructure(
            @PathVariable Long templateId,
            @Valid @RequestBody SopTemplateStructureRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("SOP 구조 저장", sopTemplateService.saveStructure(templateId, request)));
    }
}
