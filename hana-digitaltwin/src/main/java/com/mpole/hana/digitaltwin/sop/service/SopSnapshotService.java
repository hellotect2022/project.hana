package com.mpole.hana.digitaltwin.sop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.digitaltwin.sop.dto.SopStructureDtos.*;
import com.mpole.hana.digitaltwin.sop.entity.SopButton;
import com.mpole.hana.digitaltwin.sop.entity.SopStep;
import com.mpole.hana.digitaltwin.sop.entity.SopStepItem;
import com.mpole.hana.digitaltwin.sop.entity.SopTemplate;
import com.mpole.hana.digitaltwin.sop.entity.SopTransition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SopSnapshotService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public Map<String, Object> buildSnapshotMap(
            SopTemplate template,
            List<SopStep> steps,
            List<SopTransition> transitions
    ) {
        List<SopStepDto> stepDtos = steps.stream().map(this::toStepDto).toList();
        List<SopTransitionDto> transitionDtos = transitions.stream()
                .map(t -> new SopTransitionDto(
                        t.getFromStep().getStepNo(),
                        t.getTriggerKey(),
                        t.getToStep() != null ? t.getToStep().getStepNo() : null,
                        t.getResultStatus()
                ))
                .toList();

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("title", template.getTitle());
        snapshot.put("timeoutMinutes", template.getTimeoutMinutes());
        snapshot.put("revision", template.getRevision());
        snapshot.put("steps", objectMapper.convertValue(stepDtos, List.class));
        snapshot.put("transitions", objectMapper.convertValue(transitionDtos, List.class));
        return snapshot;
    }

    public ParsedSnapshot parseSnapshot(Map<String, Object> structureSnapshot) {
        if (structureSnapshot == null || structureSnapshot.isEmpty()) {
            throw new IllegalStateException("SOP 인스턴스 스냅샷이 비어 있습니다.");
        }
        String title = structureSnapshot.get("title") != null
                ? String.valueOf(structureSnapshot.get("title"))
                : "디지털 SOP";
        int timeoutMinutes = structureSnapshot.get("timeoutMinutes") instanceof Number n
                ? n.intValue()
                : 10;
        int revision = structureSnapshot.get("revision") instanceof Number r
                ? r.intValue()
                : 1;

        List<SopStepDto> steps = objectMapper.convertValue(
                structureSnapshot.get("steps"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SopStepDto.class)
        );
        List<SopTransitionDto> transitions = structureSnapshot.get("transitions") != null
                ? objectMapper.convertValue(
                structureSnapshot.get("transitions"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SopTransitionDto.class)
        )
                : List.of();

        return new ParsedSnapshot(title, timeoutMinutes, revision, steps, transitions);
    }

    public SopStepDto toStepDto(SopStep step) {
        List<SopStepItemDto> items = step.getItems().stream().map(item -> {
            List<SopButtonDto> buttons = item.getButtons().stream()
                    .map(this::toButtonDto)
                    .toList();
            return new SopStepItemDto(
                    item.getItemId(),
                    item.getSortOrder(),
                    item.getItemType(),
                    item.getLabel(),
                    item.getDescription(),
                    item.getConfig(),
                    item.getRequired(),
                    buttons
            );
        }).toList();
        return new SopStepDto(
                step.getStepId(),
                step.getStepNo(),
                step.getTitle(),
                step.getSubtitle(),
                step.getRequireAllItemsComplete(),
                step.getShowCompleteButton(),
                items
        );
    }

    private SopButtonDto toButtonDto(SopButton b) {
        return new SopButtonDto(
                b.getButtonId(),
                b.getButtonKey(),
                b.getLabel(),
                b.getStyle(),
                b.getActionType(),
                b.getActionConfig(),
                b.getSortOrder()
        );
    }

    public record ParsedSnapshot(
            String title,
            int timeoutMinutes,
            int revision,
            List<SopStepDto> steps,
            List<SopTransitionDto> transitions
    ) {
    }
}
