package com.mpole.hana.digitaltwin.sop.service;

import com.mpole.hana.digitaltwin.sop.dto.SopInstanceDtos.CreateRequest;
import com.mpole.hana.digitaltwin.sop.dto.SopInstanceDtos.InstanceResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopInstanceDtos.SaveResultRequest;
import com.mpole.hana.digitaltwin.sop.entity.SopInstance;
import com.mpole.hana.digitaltwin.sop.entity.SopTemplate;
import com.mpole.hana.digitaltwin.sop.enums.SopInstanceStatus;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.SopNode;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.StepComponent;
import com.mpole.hana.digitaltwin.sop.repository.SopInstanceRepository;
import com.mpole.hana.digitaltwin.sop.repository.SopTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class SopRuntimeService {

    private final SopInstanceRepository instanceRepository;
    private final SopTemplateRepository templateRepository;

    /** 인스턴스 생성 — 생성 시점 body 를 스냅샷으로 고정(불변) + revision lock. */
    @Transactional
    public InstanceResponse createInstance(CreateRequest request) {
        SopTemplate template = templateRepository.findById(request.templateId())
                .orElseThrow(() -> new EntityNotFoundException("SOP 템플릿을 찾을 수 없습니다."));

        SopTemplateBody body = template.getBody();
        SopInstance instance = SopInstance.builder()
                .eventId(request.eventId())
                .templateId(template.getTemplateId())
                .templateCode(template.getTemplateCode())
                .lockedRevision(template.getRevision())
                .structureSnapshot(body)
                .result(new LinkedHashMap<>())
                .status(SopInstanceStatus.IN_PROGRESS)
                .currentStepNo(firstStepNo(body))
                .startedAt(OffsetDateTime.now())
                .build();
        instanceRepository.save(instance);
        return InstanceResponse.from(instance);
    }

    @Transactional(readOnly = true)
    public InstanceResponse getInstance(Long instanceId) {
        return InstanceResponse.from(find(instanceId));
    }

    /** 결과 저장(방식 B) — result 를 통째로 갱신 + 선택적 상태 전이. */
    @Transactional
    public InstanceResponse saveResult(Long instanceId, SaveResultRequest request) {
        SopInstance instance = find(instanceId);
        if (request.result() != null) {
            instance.setResult(request.result());
        }
        if (request.currentStepNo() != null) {
            instance.setCurrentStepNo(request.currentStepNo());
        }
        if (request.resultStatus() != null) {
            instance.setResultStatus(request.resultStatus());
        }
        if (request.status() != null) {
            instance.setStatus(request.status());
            if (isTerminal(request.status()) && instance.getCompletedAt() == null) {
                instance.setCompletedAt(OffsetDateTime.now());
            }
        }
        return InstanceResponse.from(instance);
    }

    private boolean isTerminal(SopInstanceStatus s) {
        return s == SopInstanceStatus.COMPLETED
                || s == SopInstanceStatus.CANCELLED
                || s == SopInstanceStatus.TIMEOUT;
    }

    private SopInstance find(Long instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new EntityNotFoundException("SOP 인스턴스를 찾을 수 없습니다."));
    }

    private Integer firstStepNo(SopTemplateBody body) {
        if (body == null || body.steps() == null) {
            return null;
        }
        for (SopNode n : body.steps()) {
            if (n instanceof StepComponent s) {
                return s.no();
            }
        }
        return null;
    }
}
