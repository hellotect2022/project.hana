package com.mpole.hana.digitaltwin.sop.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopTemplateDtos.CreateRequest;
import com.mpole.hana.digitaltwin.sop.dto.SopTemplateDtos.DetailResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopTemplateDtos.SummaryResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopTemplateDtos.UpdateRequest;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;
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

    private final SopTemplateService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SummaryResponse>>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 목록", service.list(activeOnly)));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponse<DetailResponse>> get(@PathVariable Long templateId) {
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 상세", service.getDetail(templateId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SummaryResponse>> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 생성", service.create(request)));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable Long templateId, @Valid @RequestBody UpdateRequest request) {
        service.update(templateId, request);
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 수정"));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long templateId) {
        service.delete(templateId);
        return ResponseEntity.ok(ApiResponse.success("SOP 템플릿 비활성화"));
    }

    /** v2 컴포넌트 문서 저장(검증 + revision 증가). */
    @PutMapping("/{templateId}/body")
    public ResponseEntity<ApiResponse<DetailResponse>> saveBody(
            @PathVariable Long templateId, @RequestBody SopTemplateBody body) {
        return ResponseEntity.ok(ApiResponse.success("SOP 문서 저장", service.saveBody(templateId, body)));
    }
}
