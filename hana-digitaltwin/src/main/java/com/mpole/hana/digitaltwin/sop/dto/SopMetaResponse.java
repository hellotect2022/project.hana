package com.mpole.hana.digitaltwin.sop.dto;

import com.mpole.hana.digitaltwin.sop.enums.ActionKind;
import com.mpole.hana.digitaltwin.sop.enums.ComponentType;
import com.mpole.hana.digitaltwin.sop.enums.CompletionState;
import com.mpole.hana.digitaltwin.sop.enums.StepState;
import com.mpole.hana.digitaltwin.sop.enums.Variant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SOP 메타 — 클라이언트(Web/Unity)가 enum 값·의미·색을 하드코딩하지 않고 동기화.
 * 각 enum을 {값: 설명} map으로 노출. variant는 {값: hex}(색 팔레트 겸용).
 */
public record SopMetaResponse(
        Map<String, String> componentType,
        Map<String, String> actionKind,
        Map<String, String> variant,
        Map<String, String> stepState,
        Map<String, String> completionState
) {

    public static SopMetaResponse build() {
        Map<String, String> componentType = new LinkedHashMap<>();
        for (ComponentType v : ComponentType.values()) componentType.put(v.name(), v.getDescription());

        Map<String, String> actionKind = new LinkedHashMap<>();
        for (ActionKind v : ActionKind.values()) actionKind.put(v.name(), v.getDescription());

        Map<String, String> variant = new LinkedHashMap<>();
        for (Variant v : Variant.values()) variant.put(v.name(), v.getHex());   // 값 = hex

        Map<String, String> stepState = new LinkedHashMap<>();
        for (StepState v : StepState.values()) stepState.put(v.name(), v.getDescription());

        Map<String, String> completionState = new LinkedHashMap<>();
        for (CompletionState v : CompletionState.values()) completionState.put(v.name(), v.getDescription());

        // Collections.unmodifiableMap(LinkedHashMap) — Map.copyOf 는 iteration order 미보장.
        // 클라이언트가 선언 순서를 신뢰할 수 있도록 순서 보존 래핑.
        return new SopMetaResponse(
                Collections.unmodifiableMap(componentType),
                Collections.unmodifiableMap(actionKind),
                Collections.unmodifiableMap(variant),
                Collections.unmodifiableMap(stepState),
                Collections.unmodifiableMap(completionState)
        );
    }
}
