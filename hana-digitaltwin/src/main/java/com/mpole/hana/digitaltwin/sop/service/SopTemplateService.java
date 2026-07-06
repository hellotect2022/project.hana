package com.mpole.hana.digitaltwin.sop.service;

import com.mpole.hana.digitaltwin.sop.dto.*;
import com.mpole.hana.digitaltwin.sop.dto.SopStructureDtos.*;
import com.mpole.hana.digitaltwin.sop.entity.*;
import com.mpole.hana.digitaltwin.sop.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SopTemplateService {

    private final SopTemplateRepository templateRepository;
    private final SopStepRepository stepRepository;
    private final SopTransitionRepository transitionRepository;
    private final SopSnapshotService snapshotService;

    @Transactional(readOnly = true)
    public List<SopTemplateSummaryResponse> listTemplates(boolean activeOnly) {
        List<SopTemplate> templates = activeOnly
                ? templateRepository.findByActiveTrueOrderByTemplateNameAsc()
                : templateRepository.findAllByOrderByTemplateNameAsc();
        return templates.stream().map(SopTemplateSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SopTemplateSummaryResponse getTemplateSummary(Long templateId) {
        return SopTemplateSummaryResponse.from(getTemplateEntity(templateId));
    }

    @Transactional(readOnly = true)
    public SopTemplateDetailResponse getTemplateDetail(Long templateId) {
        SopTemplate template = getTemplateEntity(templateId);
        List<SopStep> steps = stepRepository.findByTemplateIdWithItems(templateId);
        List<SopTransition> transitions = transitionRepository.findByTemplate_TemplateId(templateId);
        return toTemplateDetail(template, steps, transitions);
    }

    @Transactional(readOnly = true)
    public SopTemplate getTemplateForRuntime(Long templateId) {
        SopTemplate template = getTemplateEntity(templateId);
        long stepCount = stepRepository.findByTemplateIdWithItems(templateId).size();
        if (stepCount == 0) {
            throw new IllegalStateException("스텝이 없는 SOP 템플릿입니다. templateId=" + templateId);
        }
        return template;
    }

    @Transactional
    public SopTemplateSummaryResponse createTemplate(SopTemplateCreateRequest request) {
        if (templateRepository.existsByTemplateCode(request.templateCode())) {
            throw new IllegalStateException("이미 존재하는 템플릿 코드입니다: " + request.templateCode());
        }
        int timeout = request.timeoutMinutes() != null ? request.timeoutMinutes() : 10;
        String name = request.templateName().trim();
        SopTemplate template = SopTemplate.builder()
                .templateCode(request.templateCode().trim())
                .templateName(name)
                .description(request.description())
                .active(true)
                .title(name)
                .timeoutMinutes(timeout)
                .revision(1)
                .build();
        templateRepository.save(template);
        return SopTemplateSummaryResponse.from(template);
    }

    @Transactional
    public void updateTemplate(Long templateId, SopTemplateUpdateRequest request) {
        SopTemplate template = getTemplateEntity(templateId);
        template.setTemplateName(request.templateName().trim());
        template.setDescription(request.description());
        if (request.active() != null) {
            template.setActive(request.active());
        }
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        SopTemplate template = getTemplateEntity(templateId);
        template.setActive(false);
    }

    @Transactional
    public SopTemplateDetailResponse saveStructure(Long templateId, SopTemplateStructureRequest request) {
        SopTemplate template = getTemplateEntity(templateId);
        if (request.title() != null && !request.title().isBlank()) {
            template.setTitle(request.title().trim());
        }
        if (request.timeoutMinutes() != null) {
            template.setTimeoutMinutes(request.timeoutMinutes());
        }
        template.setRevision(template.getRevision() + 1);

        transitionRepository.deleteByTemplate_TemplateId(templateId);
        stepRepository.deleteByTemplate_TemplateId(templateId);
        stepRepository.flush();

        Map<Integer, SopStep> stepByNo = new HashMap<>();
        for (SopStepDto stepDto : request.steps()) {
            SopStep step = SopStep.builder()
                    .template(template)
                    .stepNo(stepDto.stepNo())
                    .title(stepDto.title())
                    .subtitle(stepDto.subtitle())
                    .requireAllItemsComplete(Boolean.TRUE.equals(stepDto.requireAllItemsComplete()))
                    .showCompleteButton(stepDto.showCompleteButton() == null || stepDto.showCompleteButton())
                    .build();
            if (stepDto.items() != null) {
                for (SopStepItemDto itemDto : stepDto.items()) {
                    SopStepItem item = SopStepItem.builder()
                            .step(step)
                            .sortOrder(itemDto.sortOrder())
                            .itemType(itemDto.itemType())
                            .label(itemDto.label())
                            .description(itemDto.description())
                            .config(itemDto.config())
                            .required(Boolean.TRUE.equals(itemDto.required()))
                            .build();
                    step.getItems().add(item);
                    if (itemDto.buttons() != null) {
                        for (SopButtonDto btnDto : itemDto.buttons()) {
                            item.getButtons().add(SopButton.builder()
                                    .item(item)
                                    .buttonKey(btnDto.buttonKey())
                                    .label(btnDto.label())
                                    .style(btnDto.style() != null ? btnDto.style() : "default")
                                    .actionType(btnDto.actionType())
                                    .actionConfig(btnDto.actionConfig())
                                    .sortOrder(btnDto.sortOrder())
                                    .build());
                        }
                    }
                }
            }
            stepRepository.save(step);
            stepByNo.put(step.getStepNo(), step);
        }

        if (request.transitions() != null) {
            for (SopTransitionDto tr : request.transitions()) {
                SopStep from = stepByNo.get(tr.fromStepNo());
                if (from == null) {
                    throw new IllegalStateException("fromStepNo 없음: " + tr.fromStepNo());
                }
                SopStep to = tr.toStepNo() != null ? stepByNo.get(tr.toStepNo()) : null;
                transitionRepository.save(SopTransition.builder()
                        .template(template)
                        .fromStep(from)
                        .triggerKey(tr.triggerKey())
                        .toStep(to)
                        .resultStatus(tr.resultStatus())
                        .build());
            }
        }

        List<SopStep> savedSteps = stepRepository.findByTemplateIdWithItems(templateId);
        List<SopTransition> savedTransitions = transitionRepository.findByTemplate_TemplateId(templateId);
        return toTemplateDetail(template, savedSteps, savedTransitions);
    }

    private SopTemplateDetailResponse toTemplateDetail(
            SopTemplate template,
            List<SopStep> steps,
            List<SopTransition> transitions
    ) {
        List<SopStepDto> stepDtos = steps.stream().map(snapshotService::toStepDto).toList();
        List<SopTransitionDto> transitionDtos = transitions.stream()
                .map(t -> new SopTransitionDto(
                        t.getFromStep().getStepNo(),
                        t.getTriggerKey(),
                        t.getToStep() != null ? t.getToStep().getStepNo() : null,
                        t.getResultStatus()
                ))
                .toList();
        return new SopTemplateDetailResponse(
                template.getTemplateId(),
                template.getTemplateCode(),
                template.getTemplateName(),
                template.getRevision(),
                template.getTitle(),
                template.getTimeoutMinutes(),
                stepDtos,
                transitionDtos
        );
    }

    private SopTemplate getTemplateEntity(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("SOP 템플릿을 찾을 수 없습니다."));
    }
}
