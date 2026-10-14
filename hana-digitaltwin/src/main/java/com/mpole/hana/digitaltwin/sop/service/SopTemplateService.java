package com.mpole.hana.digitaltwin.sop.service;

import com.mpole.hana.digitaltwin.sop.dto.SopTemplateDtos.CreateRequest;
import com.mpole.hana.digitaltwin.sop.dto.SopTemplateDtos.DetailResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopTemplateDtos.SummaryResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopTemplateDtos.UpdateRequest;
import com.mpole.hana.digitaltwin.sop.entity.SopTemplate;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;
import com.mpole.hana.digitaltwin.sop.repository.SopTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SopTemplateService {

    private static final int BODY_VERSION = 2;

    private final SopTemplateRepository repository;
    private final SopBodyValidator validator;

    @Transactional(readOnly = true)
    public List<SummaryResponse> list(boolean activeOnly) {
        List<SopTemplate> templates = activeOnly
                ? repository.findByActiveTrueOrderByTemplateNameAsc()
                : repository.findAllByOrderByTemplateNameAsc();
        return templates.stream().map(SummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DetailResponse getDetail(Long templateId) {
        return DetailResponse.from(find(templateId));
    }

    @Transactional
    public SummaryResponse create(CreateRequest request) {
        String code = request.templateCode().trim();
        if (repository.existsByTemplateCode(code)) {
            throw new IllegalArgumentException("이미 존재하는 템플릿 코드입니다: " + code);
        }
        SopTemplate template = SopTemplate.builder()
                .templateCode(code)
                .templateName(request.templateName().trim())
                .description(request.description())
                .title(request.title() != null ? request.title() : request.templateName().trim())
                .active(true)
                .revision(1)
                .body(new SopTemplateBody(BODY_VERSION, List.of())) // 빈 문서(steps 없음)로 시작
                .build();
        repository.save(template);
        return SummaryResponse.from(template);
    }

    @Transactional
    public void update(Long templateId, UpdateRequest request) {
        SopTemplate template = find(templateId);
        if (request.templateName() != null) {
            template.setTemplateName(request.templateName().trim());
        }
        template.setDescription(request.description());
        if (request.title() != null) {
            template.setTitle(request.title());
        }
        if (request.active() != null) {
            template.setActive(request.active());
        }
    }

    /** soft delete. */
    @Transactional
    public void delete(Long templateId) {
        find(templateId).setActive(false);
    }

    /** 문서(body) 저장 — 검증 후 저장 + revision 증가. */
    @Transactional
    public DetailResponse saveBody(Long templateId, SopTemplateBody body) {
        validator.validate(body);
        SopTemplate template = find(templateId);
        template.setBody(new SopTemplateBody(BODY_VERSION, body.steps()));
        template.setRevision(template.getRevision() + 1);
        return DetailResponse.from(template);
    }

    private SopTemplate find(Long templateId) {
        return repository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("SOP 템플릿을 찾을 수 없습니다."));
    }
}
