package com.mpole.hana.digitaltwin.sop.service;

import com.mpole.hana.digitaltwin.sop.dto.SopRuntimeDtos.*;
import com.mpole.hana.digitaltwin.sop.dto.SopStructureDtos.SopButtonDto;
import com.mpole.hana.digitaltwin.sop.dto.SopStructureDtos.SopStepDto;
import com.mpole.hana.digitaltwin.sop.dto.SopStructureDtos.SopStepItemDto;
import com.mpole.hana.digitaltwin.sop.dto.SopStructureDtos.SopTransitionDto;
import com.mpole.hana.digitaltwin.sop.entity.*;
import com.mpole.hana.digitaltwin.sop.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SopRuntimeService {

    private final SopInstanceRepository instanceRepository;
    private final SopInstanceItemStateRepository itemStateRepository;
    private final SopActionLogRepository actionLogRepository;
    private final SopStepRepository stepRepository;
    private final SopTransitionRepository transitionRepository;
    private final SopSnapshotService snapshotService;

    @Transactional
    public SopInstance createInstance(Long eventId, SopTemplate template) {
        if (instanceRepository.findByEventId(eventId).isPresent()) {
            throw new IllegalStateException("이미 SOP 인스턴스가 존재합니다. eventId=" + eventId);
        }
        Long templateId = template.getTemplateId();
        List<SopStep> steps = stepRepository.findByTemplateIdWithItems(templateId);
        if (steps.isEmpty()) {
            throw new IllegalStateException("스텝이 없는 SOP 템플릿입니다.");
        }
        List<SopTransition> transitions = transitionRepository.findByTemplate_TemplateId(templateId);
        Map<String, Object> snapshot = snapshotService.buildSnapshotMap(template, steps, transitions);

        OffsetDateTime now = OffsetDateTime.now();
        SopInstance instance = SopInstance.builder()
                .eventId(eventId)
                .template(template)
                .lockedRevision(template.getRevision())
                .structureSnapshot(snapshot)
                .currentStepNo(1)
                .status(SopInstanceStatus.IN_PROGRESS)
                .startedAt(now)
                .timeoutAt(now.plusMinutes(template.getTimeoutMinutes()))
                .build();
        instanceRepository.save(instance);

        for (SopStep step : steps) {
            for (SopStepItem item : step.getItems()) {
                itemStateRepository.save(SopInstanceItemState.builder()
                        .instanceId(instance.getInstanceId())
                        .itemId(item.getItemId())
                        .completed(false)
                        .build());
            }
        }
        return instance;
    }

    @Transactional(readOnly = true)
    public SopRuntimePayload buildRuntimePayload(Long instanceId) {
        SopInstance instance = getInstance(instanceId);
        SopSnapshotService.ParsedSnapshot snapshot = snapshotService.parseSnapshot(instance.getStructureSnapshot());
        Map<Long, SopInstanceItemState> stateMap = itemStateRepository.findByInstanceId(instanceId)
                .stream()
                .collect(Collectors.toMap(SopInstanceItemState::getItemId, s -> s));

        List<SopRuntimeStepDto> stepDtos = snapshot.steps().stream().map(step -> {
            String stepStatus = step.stepNo().equals(instance.getCurrentStepNo())
                    ? "ACTIVE"
                    : (step.stepNo() < instance.getCurrentStepNo() ? "DONE" : "PENDING");
            List<SopRuntimeItemDto> items = (step.items() != null ? step.items() : List.<SopStepItemDto>of())
                    .stream()
                    .map(item -> {
                        SopInstanceItemState st = stateMap.get(item.itemId());
                        List<SopRuntimeButtonDto> buttons = (item.buttons() != null ? item.buttons() : List.<SopButtonDto>of())
                                .stream()
                                .map(b -> new SopRuntimeButtonDto(
                                        b.buttonId(),
                                        b.buttonKey(),
                                        b.label(),
                                        b.style(),
                                        b.actionType()
                                ))
                                .toList();
                        return new SopRuntimeItemDto(
                                item.itemId(),
                                item.sortOrder(),
                                item.itemType(),
                                item.label(),
                                item.description(),
                                item.config(),
                                item.required(),
                                st != null && Boolean.TRUE.equals(st.getCompleted()),
                                buttons
                        );
                    })
                    .toList();
            return new SopRuntimeStepDto(
                    step.stepId(),
                    step.stepNo(),
                    step.title(),
                    step.subtitle(),
                    step.showCompleteButton(),
                    stepStatus,
                    items
            );
        }).toList();

        return new SopRuntimePayload(
                instance.getInstanceId(),
                snapshot.title(),
                instance.getStatus(),
                instance.getResultStatus(),
                instance.getCurrentStepNo(),
                instance.getTimeoutAt(),
                stepDtos
        );
    }

    @Transactional
    public void completeItem(Long instanceId, Long itemId, Long actorUserId) {
        SopInstanceItemState state = itemStateRepository.findByInstanceIdAndItemId(instanceId, itemId)
                .orElseThrow(() -> new EntityNotFoundException("항목 상태를 찾을 수 없습니다."));
        state.setCompleted(true);
        state.setCompletedAt(OffsetDateTime.now());
        state.setCompletedBy(actorUserId);
    }

    @Transactional
    public ButtonClickResponse clickButton(Long instanceId, String buttonKey, ButtonClickRequest request) {
        SopInstance instance = getInstance(instanceId);
        if (instance.getStatus() == SopInstanceStatus.COMPLETED
                || instance.getStatus() == SopInstanceStatus.CANCELLED
                || instance.getStatus() == SopInstanceStatus.TIMEOUT) {
            throw new IllegalStateException("종료된 SOP입니다.");
        }

        SopButtonDto button = findButton(instance, buttonKey);
        logAction(instance, button, request);

        return switch (button.actionType()) {
            case CLOSE_SOP -> closeInstance(instance, resolveResultStatus(button, "COMPLETED"));
            case TRANSITION -> transition(instance, button);
            case EXECUTE_API -> {
                transition(instance, button);
                yield new ButtonClickResponse(
                        instance.getStatus(),
                        instance.getResultStatus(),
                        instance.getCurrentStepNo(),
                        false
                );
            }
            case OPEN_MODAL, NOOP -> new ButtonClickResponse(
                    instance.getStatus(),
                    instance.getResultStatus(),
                    instance.getCurrentStepNo(),
                    false
            );
        };
    }

    @Transactional
    public ButtonClickResponse completeStep(Long instanceId, int stepNo) {
        SopInstance instance = getInstance(instanceId);
        if (!Objects.equals(instance.getCurrentStepNo(), stepNo)) {
            throw new IllegalStateException("현재 스텝이 아닙니다.");
        }
        SopSnapshotService.ParsedSnapshot snapshot = snapshotService.parseSnapshot(instance.getStructureSnapshot());
        Optional<SopTransitionDto> completeTr = snapshot.transitions().stream()
                .filter(t -> t.fromStepNo().equals(stepNo) && "COMPLETE".equals(t.triggerKey()))
                .findFirst();
        if (completeTr.isPresent() && completeTr.get().toStepNo() != null) {
            instance.setCurrentStepNo(completeTr.get().toStepNo());
        } else {
            int maxStep = snapshot.steps().stream().mapToInt(SopStepDto::stepNo).max().orElse(stepNo);
            if (stepNo >= maxStep) {
                return closeInstance(instance, "COMPLETED");
            }
            instance.setCurrentStepNo(stepNo + 1);
        }
        return new ButtonClickResponse(instance.getStatus(), instance.getResultStatus(), instance.getCurrentStepNo(), false);
    }

    private ButtonClickResponse transition(SopInstance instance, SopButtonDto button) {
        Map<String, Object> config = button.actionConfig() != null ? button.actionConfig() : Map.of();
        if (config.containsKey("toStepNo")) {
            instance.setCurrentStepNo(((Number) config.get("toStepNo")).intValue());
            return new ButtonClickResponse(instance.getStatus(), null, instance.getCurrentStepNo(), false);
        }

        SopSnapshotService.ParsedSnapshot snapshot = snapshotService.parseSnapshot(instance.getStructureSnapshot());
        SopTransitionDto tr = snapshot.transitions().stream()
                .filter(t -> t.fromStepNo().equals(instance.getCurrentStepNo())
                        && t.triggerKey().equals(button.buttonKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("전이 정의를 찾을 수 없습니다: " + button.buttonKey()));

        if (tr.toStepNo() == null) {
            return closeInstance(instance, tr.resultStatus() != null ? tr.resultStatus() : "COMPLETED");
        }
        instance.setCurrentStepNo(tr.toStepNo());
        return new ButtonClickResponse(instance.getStatus(), null, instance.getCurrentStepNo(), false);
    }

    private ButtonClickResponse closeInstance(SopInstance instance, String resultStatus) {
        instance.setStatus(SopInstanceStatus.COMPLETED);
        instance.setResultStatus(resultStatus);
        instance.setCompletedAt(OffsetDateTime.now());
        return new ButtonClickResponse(instance.getStatus(), resultStatus, null, true);
    }

    private String resolveResultStatus(SopButtonDto button, String defaultStatus) {
        if (button.actionConfig() != null && button.actionConfig().get("resultStatus") != null) {
            return String.valueOf(button.actionConfig().get("resultStatus"));
        }
        return defaultStatus;
    }

    private SopButtonDto findButton(SopInstance instance, String buttonKey) {
        SopSnapshotService.ParsedSnapshot snapshot = snapshotService.parseSnapshot(instance.getStructureSnapshot());
        for (SopStepDto step : snapshot.steps()) {
            if (step.items() == null) {
                continue;
            }
            for (SopStepItemDto item : step.items()) {
                if (item.buttons() == null) {
                    continue;
                }
                for (SopButtonDto button : item.buttons()) {
                    if (button.buttonKey().equals(buttonKey)) {
                        return button;
                    }
                }
            }
        }
        throw new EntityNotFoundException("버튼을 찾을 수 없습니다: " + buttonKey);
    }

    private void logAction(SopInstance instance, SopButtonDto button, ButtonClickRequest request) {
        Long itemId = findItemIdForButton(instance, button.buttonKey());
        actionLogRepository.save(SopActionLog.builder()
                .instanceId(instance.getInstanceId())
                .itemId(itemId)
                .buttonId(button.buttonId())
                .actionType(button.actionType().name())
                .requestPayload(request != null ? request.clientMeta() : null)
                .actorUserId(request != null ? request.actorUserId() : null)
                .result("SUCCESS")
                .build());
    }

    private Long findItemIdForButton(SopInstance instance, String buttonKey) {
        SopSnapshotService.ParsedSnapshot snapshot = snapshotService.parseSnapshot(instance.getStructureSnapshot());
        for (SopStepDto step : snapshot.steps()) {
            if (step.items() == null) {
                continue;
            }
            for (SopStepItemDto item : step.items()) {
                if (item.buttons() == null) {
                    continue;
                }
                for (SopButtonDto button : item.buttons()) {
                    if (button.buttonKey().equals(buttonKey)) {
                        return item.itemId();
                    }
                }
            }
        }
        return null;
    }

    private SopInstance getInstance(Long instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new EntityNotFoundException("SOP 인스턴스를 찾을 수 없습니다."));
    }
}
