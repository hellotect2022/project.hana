package com.mpole.hana.digitaltwin.sop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mpole.hana.digitaltwin.sop.enums.ActionKind;
import com.mpole.hana.digitaltwin.sop.enums.ComponentType;
import com.mpole.hana.digitaltwin.sop.enums.CompletionState;
import com.mpole.hana.digitaltwin.sop.enums.StepState;
import com.mpole.hana.digitaltwin.sop.enums.Variant;

import java.util.List;
import java.util.Map;

/**
 * SOP 템플릿 body(JSONB) 타입드 모델 (v2).
 * body = { version, steps[] } — root steps 원소는 전부 STEP(컨테이너 컴포넌트).
 * STEP 은 componentType 을 가진 컴포넌트이면서 자식 components[](leaf 5종 또는 group)를 품는다.
 * 조건부 노출(showWhen) 없음 — 모든 컴포넌트 항상 표시.
 * 모든 레코드 @JsonInclude(NON_NULL): null 필드는 직렬화에서 생략.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SopTemplateBody(
        Integer version,
        @JsonDeserialize(contentUsing = SopNodeDeserializer.class)
        List<SopNode> steps
) {

    /** 컴포넌트 트리 노드: STEP(컨테이너) / leaf 5종 / group. */
    public interface SopNode {
    }

    // ── STEP: 컨테이너 컴포넌트 (헤더 + 자식 components) ──
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepComponent(
            ComponentType componentType, Integer no, String title,
            StepState state, CompleteSpec complete,
            List<NextRule> rules,
            @JsonDeserialize(contentUsing = SopNodeDeserializer.class)
            List<SopNode> components
    ) implements SopNode {
    }

    // ── STEP 전이 규칙 (STEP 최상위 rules[]. 래퍼/ default 없음) ──
    /**
     * when = 충족돼야 할 컴포넌트 id 목록(AND: 체크된 COMPLETE_ACTION id · 선택된 BUTTON_GROUP 옵션 id).
     * next = 이동 대상 STEP 번호(문자열/숫자 허용). <b>생략 시 SOP 종료</b>. 규칙 미매칭 시 순번상 다음 STEP.
     * (Unity C# 예약어 회피로 옛 goto → next 로 명명.)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NextRule(
            List<String> when,
            String next
    ) {
    }

    // ── FRAME: 테두리 박스 컨테이너 컴포넌트(시각). 자식 components(leaf) 를 테두리로 감쌈 ──
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FrameComponent(
            ComponentType componentType, String id,
            @JsonDeserialize(contentUsing = SopNodeDeserializer.class)
            List<SopNode> components
    ) implements SopNode {
    }

    // ── leaf 콘텐츠 5종 (componentType으로 판별) ──

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabelComponent(
            ComponentType componentType, String id, String text
    ) implements SopNode {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompleteActionComponent(
            ComponentType componentType, String id,
            String text, CompletionState state, SopAction action
    ) implements SopNode {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ButtonGroupComponent(
            ComponentType componentType, String id, List<ButtonOption> options
    ) implements SopNode {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhoneContactComponent(
            ComponentType componentType, String id,
            String orgName, String phone, String prefix, SopAction action
    ) implements SopNode {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NoteComponent(
            ComponentType componentType, String id, String text
    ) implements SopNode {
    }

    // ── GROUP: 논리 그룹 컨테이너 컴포넌트 (componentType:"GROUP", 테두리 없음). FRAME 과 동일 구조 ──
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupComponent(
            ComponentType componentType, String id, String label,
            @JsonDeserialize(contentUsing = SopNodeDeserializer.class)
            List<SopNode> components
    ) implements SopNode {
    }

    // ── 서브 객체 ──

    /** 부수효과 동작(선택). 흐름 제어는 STEP.next 담당 → target 없음. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SopAction(ActionKind kind, Map<String, Object> payload) {
    }

    /** id: 로그/결과 저장에서 어느 버튼을 눌렀는지 안정적으로 가리키는 식별자(선택, 권장). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ButtonOption(String id, String label, Variant variant, SopAction action) {
    }

    /** STEP 헤더 완료 버튼 정의(상태는 두지 않음 — STEP.state로 판정). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompleteSpec(ActionKind action) {
    }
}
