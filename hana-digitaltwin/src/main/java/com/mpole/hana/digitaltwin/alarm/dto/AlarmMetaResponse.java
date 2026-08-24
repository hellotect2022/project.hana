package com.mpole.hana.digitaltwin.alarm.dto;

import com.mpole.hana.common.dto.CctvSource;
import com.mpole.hana.common.dto.OutputChannel;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmConditionKind;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmLevel;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmOperator;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmScope;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmEffect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 알람 메타 — 클라이언트(Web/Unity)가 enum 값·의미·순서를 하드코딩하지 않고 동기화.
 *
 * <p>{@link com.mpole.hana.digitaltwin.sop.dto.SopMetaResponse} 와 동일 패턴: 각 enum 을
 * {값: 설명} map(LinkedHashMap → 선언 순서 보존)으로 노출한다. 프론트 드롭다운/검증/정렬은
 * 이 메타를 근거로 구동한다(값 집합 드리프트 방지). 라벨 색 등 순수 표현 토큰은 클라이언트 소유.</p>
 *
 * <p>level 은 선언 순서가 곧 오름차순 심각도(주의→경계→심각), operator 설명은 비교 심볼,
 * outputChannel 은 표시(NOTIFY/SOP/CCTV) + 서버 채널(SMS/BROADCAST/EMAIL) 전체.</p>
 */
public record AlarmMetaResponse(
        Map<String, String> scope,
        Map<String, String> level,
        Map<String, String> operator,
        Map<String, String> conditionKind,
        Map<String, String> effect,
        Map<String, String> outputChannel,
        Map<String, String> cctvSource
) {

    public static AlarmMetaResponse build() {
        Map<String, String> scope = new LinkedHashMap<>();
        for (AlarmScope v : AlarmScope.values()) scope.put(v.name(), v.getDescription());

        Map<String, String> level = new LinkedHashMap<>();
        for (AlarmLevel v : AlarmLevel.values()) level.put(v.name(), v.getDescription());

        Map<String, String> operator = new LinkedHashMap<>();
        for (AlarmOperator v : AlarmOperator.values()) operator.put(v.name(), v.getSymbol());

        Map<String, String> conditionKind = new LinkedHashMap<>();
        for (AlarmConditionKind v : AlarmConditionKind.values()) conditionKind.put(v.name(), v.getDescription());

        Map<String, String> effect = new LinkedHashMap<>();
        for (AlarmEffect v : AlarmEffect.values()) effect.put(v.name(), v.getDescription());

        Map<String, String> outputChannel = new LinkedHashMap<>();
        for (OutputChannel v : OutputChannel.values()) outputChannel.put(v.name(), v.getDescription());

        Map<String, String> cctvSource = new LinkedHashMap<>();
        for (CctvSource v : CctvSource.values()) cctvSource.put(v.name(), v.getDescription());

        // Collections.unmodifiableMap(LinkedHashMap) — Map.copyOf 는 iteration order 미보장이라
        // "선언 순서 = 정렬 순서" 계약(프론트가 레벨/채널 순서를 이 순서로 신뢰)을 지키려 순서 보존 래핑.
        return new AlarmMetaResponse(
                Collections.unmodifiableMap(scope),
                Collections.unmodifiableMap(level),
                Collections.unmodifiableMap(operator),
                Collections.unmodifiableMap(conditionKind),
                Collections.unmodifiableMap(effect),
                Collections.unmodifiableMap(outputChannel),
                Collections.unmodifiableMap(cctvSource)
        );
    }
}
