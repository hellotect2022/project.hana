package com.mpole.hana.digitaltwin.sop.service;

import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.ButtonGroupComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.ButtonOption;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.CompleteActionComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.FrameComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.GroupComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.NextRule;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.SopNode;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.StepComponent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * body 저장 시 구조 검증 (v2). 위반 시 IllegalArgumentException(→ 400).
 *  - root steps 는 전부 STEP(컨테이너 컴포넌트), STEP.no 유일
 *  - group/FRAME 안에 STEP 불가(중첩은 허용)
 *  - STEP.rules(전이): when = 그 STEP 참조가능 id(COMPLETE_ACTION·BUTTON_GROUP 옵션) 목록(AND, 비어있으면 안 됨),
 *    next = 존재하는 STEP 번호(생략 시 SOP 종료). 참조가능 id 는 스텝 내 유일.
 */
@Component
public class SopBodyValidator {

    public void validate(SopTemplateBody body) {
        if (body == null || body.steps() == null || body.steps().isEmpty()) {
            throw new IllegalArgumentException("steps 가 비어 있습니다.");
        }

        Set<Integer> stepNos = new HashSet<>();
        for (SopNode node : body.steps()) {
            if (!(node instanceof StepComponent step)) {
                throw new IllegalArgumentException("최상위 steps 는 STEP 컴포넌트만 올 수 있습니다.");
            }
            if (step.no() == null) {
                throw new IllegalArgumentException("STEP.no 가 필요합니다.");
            }
            if (!stepNos.add(step.no())) {
                throw new IllegalArgumentException("STEP.no 가 중복됩니다: " + step.no());
            }
        }

        for (SopNode node : body.steps()) {
            StepComponent step = (StepComponent) node;
            if (step.components() != null) {
                for (SopNode child : step.components()) {
                    validateNoStep(child);
                }
            }
            Set<String> refIds = collectReferenceableIds(step);   // 참조가능 id + 스텝 내 유일성 검증
            validateRules(step, stepNos, refIds);
        }
    }

    /** group/FRAME 컨테이너 안에 STEP 금지(중첩 자체는 허용). */
    private void validateNoStep(SopNode node) {
        List<SopNode> children = switch (node) {
            case GroupComponent g -> g.components();
            case FrameComponent f -> f.components();
            default -> null;
        };
        if (children == null) {
            return;
        }
        for (SopNode child : children) {
            if (child instanceof StepComponent) {
                throw new IllegalArgumentException("group/FRAME 안에는 STEP 이 올 수 없습니다.");
            }
            validateNoStep(child);
        }
    }

    /**
     * STEP.rules 검증: 모든 STEP 에 rules 필수(전이 명시). when 의 각 id 가 참조가능 id 에 존재,
     * next 대상(있으면) 유효 STEP(없으면 SOP 종료 규칙).
     */
    private void validateRules(StepComponent step, Set<Integer> stepNos, Set<String> refIds) {
        if (step.rules() == null || step.rules().isEmpty()) {
            throw new IllegalArgumentException(
                    "STEP " + step.no() + " 에 rules 가 필요합니다(모든 STEP 은 전이 규칙을 명시해야 함; 종료는 next 없는 규칙).");
        }
        for (NextRule rule : step.rules()) {
            if (rule.when() == null || rule.when().isEmpty()) {
                throw new IllegalArgumentException("STEP " + step.no() + " rules.when 이 비어 있습니다.");
            }
            for (String id : rule.when()) {
                if (id == null || !refIds.contains(id)) {
                    throw new IllegalArgumentException(
                            "STEP " + step.no() + " rules.when 의 id 가 그 STEP 참조가능 id 에 없습니다: " + id);
                }
            }
            if (rule.next() != null) {   // next 생략 = SOP 종료(허용)
                checkStepTarget(rule.next(), stepNos, step.no());
            }
        }
    }

    private void checkStepTarget(String target, Set<Integer> stepNos, Integer fromNo) {
        int n;
        try {
            n = Integer.parseInt(target.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("STEP " + fromNo + " rules.next 는 STEP 번호여야 합니다: " + target);
        }
        if (!stepNos.contains(n)) {
            throw new IllegalArgumentException("STEP " + fromNo + " rules.next 대상 STEP 이 존재하지 않습니다: " + target);
        }
    }

    /** STEP 내 참조가능 id(COMPLETE_ACTION·BUTTON_GROUP 옵션) 수집 + 유일성 검증(그룹/프레임 재귀). */
    private Set<String> collectReferenceableIds(StepComponent step) {
        List<String> ids = new ArrayList<>();
        collectReferenceableIds(step.components(), ids);
        Set<String> set = new HashSet<>();
        for (String id : ids) {
            if (!set.add(id)) {
                throw new IllegalArgumentException("STEP " + step.no() + " 안에 중복된 컴포넌트 id 가 있습니다: " + id);
            }
        }
        return set;
    }

    private void collectReferenceableIds(List<SopNode> nodes, List<String> out) {
        if (nodes == null) {
            return;
        }
        for (SopNode node : nodes) {
            switch (node) {
                case ButtonGroupComponent bg -> {
                    if (bg.options() != null) {
                        for (ButtonOption o : bg.options()) {
                            if (o.id() != null) {
                                out.add(o.id());
                            }
                        }
                    }
                }
                case CompleteActionComponent ca -> {
                    if (ca.id() != null) {
                        out.add(ca.id());
                    }
                }
                case GroupComponent g -> collectReferenceableIds(g.components(), out);
                case FrameComponent f -> collectReferenceableIds(f.components(), out);
                default -> {
                }
            }
        }
    }
}
