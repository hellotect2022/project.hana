package com.mpole.hana.digitaltwin.sop.dto;

import com.mpole.hana.digitaltwin.sop.entity.SopButtonActionType;
import com.mpole.hana.digitaltwin.sop.entity.SopItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class SopStructureDtos {

    private SopStructureDtos() {
    }

    public record SopButtonDto(
            Long buttonId,
            @NotBlank String buttonKey,
            @NotBlank String label,
            String style,
            @NotNull SopButtonActionType actionType,
            Map<String, Object> actionConfig,
            @NotNull Integer sortOrder
    ) {
    }

    public record SopStepItemDto(
            Long itemId,
            @NotNull Integer sortOrder,
            @NotNull SopItemType itemType,
            String label,
            String description,
            Map<String, Object> config,
            Boolean required,
            @Valid List<SopButtonDto> buttons
    ) {
    }

    public record SopStepDto(
            Long stepId,
            @NotNull Integer stepNo,
            @NotBlank String title,
            String subtitle,
            Boolean requireAllItemsComplete,
            Boolean showCompleteButton,
            @Valid List<SopStepItemDto> items
    ) {
    }

    public record SopTransitionDto(
            Integer fromStepNo,
            @NotBlank String triggerKey,
            Integer toStepNo,
            String resultStatus
    ) {
    }

    public record SopTemplateStructureRequest(
            String title,
            Integer timeoutMinutes,
            @Valid @NotNull List<SopStepDto> steps,
            @Valid List<SopTransitionDto> transitions
    ) {
    }

    public record SopTemplateDetailResponse(
            Long templateId,
            String templateCode,
            String templateName,
            Integer revision,
            String title,
            Integer timeoutMinutes,
            List<SopStepDto> steps,
            List<SopTransitionDto> transitions
    ) {
    }
}
