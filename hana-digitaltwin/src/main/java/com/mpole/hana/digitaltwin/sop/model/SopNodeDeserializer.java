package com.mpole.hana.digitaltwin.sop.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.digitaltwin.sop.enums.ComponentType;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.ButtonGroupComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.CompleteActionComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.FrameComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.GroupComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.LabelComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.NoteComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.PhoneContactComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.SopNode;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.StepComponent;

import java.io.IOException;

/**
 * 컴포넌트 트리 노드 다형 역직렬화: componentType 으로 분기(STEP·GROUP·FRAME 컨테이너 / leaf 5종).
 * 알 수 없으면 예외 → 저장 검증에서 400.
 */
public class SopNodeDeserializer extends JsonDeserializer<SopNode> {

    @Override
    public SopNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        JsonNode typeNode = node.get("componentType");
        if (typeNode == null || typeNode.isNull()) {
            throw new IllegalArgumentException("SOP 노드에 componentType 이 없습니다: " + node);
        }

        ComponentType type;
        try {
            type = ComponentType.valueOf(typeNode.asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 componentType: " + typeNode.asText());
        }

        Class<? extends SopNode> target = switch (type) {
            case STEP -> StepComponent.class;
            case LABEL -> LabelComponent.class;
            case COMPLETE_ACTION -> CompleteActionComponent.class;
            case BUTTON_GROUP -> ButtonGroupComponent.class;
            case PHONE_CONTACT -> PhoneContactComponent.class;
            case NOTE -> NoteComponent.class;
            case GROUP -> GroupComponent.class;
            case FRAME -> FrameComponent.class;
        };
        return mapper.treeToValue(node, target);
    }
}
