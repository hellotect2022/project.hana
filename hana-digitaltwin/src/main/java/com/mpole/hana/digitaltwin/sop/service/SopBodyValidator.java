package com.mpole.hana.digitaltwin.sop.service;

import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.ButtonGroupComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.ButtonOption;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.FrameComponent;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.GroupNode;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.NextRule;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.NextSpec;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.SopNode;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody.StepComponent;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * body 저장 시 구조 검증 (v2). 위반 시 IllegalArgumentException(→ 400).
 *  - root steps 는 전부 STEP(컨테이너 컴포넌트), STEP.no 유일
 *  - group/FRAME 안에 STEP 불가(중첩은 허용)
 *  - STEP.next: default/goto = 존재 STEP 또는 "CLOSE", rules.when = 그 STEP 버튼 id 에 존재
 */
@Component
public class SopBodyValidator {

    private static final String CLOSE = "CLOSE";

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
            validateNext(step, stepNos);
        }
    }

    /** group/FRAME 컨테이너 안에 STEP 금지(중첩 자체는 허용). */
    private void validateNoStep(SopNode node) {
        List<SopNode> children = switch (node) {
            case GroupNode g -> g.group() != null ? g.group().components() : null;
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

    /** STEP.next 검증: default/goto 대상 유효, rules.when 이 그 STEP 버튼 id 에 존재. */
    private void validateNext(StepComponent step, Set<Integer> stepNos) {
        NextSpec next = step.next();
        if (next == null) {
            return;
        }
        checkTarget(next.defaultTarget(), stepNos);
        if (next.rules() != null) {
            Set<String> buttonIds = collectButtonIds(step.components());
            for (NextRule rule : next.rules()) {
                checkTarget(rule.target(), stepNos);
                if (rule.when() == null || !buttonIds.contains(rule.when())) {
                    throw new IllegalArgumentException(
                            "STEP " + step.no() + " next.rules.when 이 버튼 id 에 없습니다: " + rule.when());
                }
            }
        }
    }

    private void checkTarget(String target, Set<Integer> stepNos) {
        if (target == null || CLOSE.equals(target)) {
            return;
        }
        int n;
        try {
            n = Integer.parseInt(target.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("next 대상은 STEP 번호 또는 \"CLOSE\" 여야 합니다: " + target);
        }
        if (!stepNos.contains(n)) {
            throw new IllegalArgumentException("next 대상 STEP 이 존재하지 않습니다: " + target);
        }
    }

    /** STEP 안(그룹/프레임 재귀 포함) 모든 BUTTON_GROUP 옵션 id 수집. */
    private Set<String> collectButtonIds(List<SopNode> nodes) {
        Set<String> ids = new HashSet<>();
        collectButtonIds(nodes, ids);
        return ids;
    }

    private void collectButtonIds(List<SopNode> nodes, Set<String> ids) {
        if (nodes == null) {
            return;
        }
        for (SopNode node : nodes) {
            switch (node) {
                case ButtonGroupComponent bg -> {
                    if (bg.options() != null) {
                        for (ButtonOption o : bg.options()) {
                            if (o.id() != null) {
                                ids.add(o.id());
                            }
                        }
                    }
                }
                case GroupNode g -> collectButtonIds(g.group() != null ? g.group().components() : null, ids);
                case FrameComponent f -> collectButtonIds(f.components(), ids);
                default -> {
                }
            }
        }
    }
}
