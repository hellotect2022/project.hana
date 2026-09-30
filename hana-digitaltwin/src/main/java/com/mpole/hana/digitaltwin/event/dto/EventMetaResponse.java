package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.constant.ActionStatus;
import com.mpole.hana.common.constant.NotificationChannel;
import com.mpole.hana.common.constant.SuppressMode;
import com.mpole.hana.common.dto.CctvSource;
import com.mpole.hana.digitaltwin.event.entity.RuleConditionKind;
import com.mpole.hana.digitaltwin.event.entity.EventLevel;
import com.mpole.hana.digitaltwin.event.entity.RuleOperator;
import com.mpole.hana.digitaltwin.event.entity.RuleScope;
import com.mpole.hana.digitaltwin.event.entity.EventEffect;
import com.mpole.hana.digitaltwin.event.entity.EventStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 이벤트 메타 — 클라이언트(Web/Unity)가 enum 값·의미·순서를 하드코딩하지 않고 동기화.
 *
 * <p>{@link com.mpole.hana.digitaltwin.sop.dto.SopMetaResponse} 와 동일 패턴: 각 enum 을
 * {값: 설명} map(LinkedHashMap → 선언 순서 보존)으로 노출한다. 프론트 드롭다운/검증/정렬은
 * 이 메타를 근거로 구동한다(값 집합 드리프트 방지). 라벨 색 등 순수 표현 토큰은 클라이언트 소유.</p>
 *
 * <p>level 은 선언 순서가 곧 오름차순 심각도(주의→경계→심각), operator 설명은 비교 심볼.</p>
 *
 * <p>출력 채널은 {@code notificationChannel}({@link NotificationChannel}) 하나로 일원화했다.
 * 구 {@code outputChannel}(OutputChannel enum)은 같은 것을 두 벌로 정의하던 잔재라 제거했다 —
 * 키가 {@code OutputSpec} 필드명과 1:1 이라 클라이언트가 매핑만 하면 된다.</p>
 */
public record EventMetaResponse(
        Map<String, String> scope,
        Map<String, String> level,
        Map<String, String> operator,
        Map<String, String> conditionKind,
        Map<String, String> effect,
        Map<String, String> cctvSource,
        Map<String, ChannelMeta> notificationChannel,
        Map<String, String> actionStatus,
        Map<String, String> eventStatus,
        Map<String, String> suppressMode
) {

    public static EventMetaResponse build() {
        Map<String, String> scope = new LinkedHashMap<>();
        for (RuleScope v : RuleScope.values()) scope.put(v.name(), v.getDescription());

        Map<String, String> level = new LinkedHashMap<>();
        for (EventLevel v : EventLevel.values()) level.put(v.name(), v.getDescription());

        Map<String, String> operator = new LinkedHashMap<>();
        for (RuleOperator v : RuleOperator.values()) operator.put(v.name(), v.getSymbol());

        Map<String, String> conditionKind = new LinkedHashMap<>();
        for (RuleConditionKind v : RuleConditionKind.values()) conditionKind.put(v.name(), v.getDescription());

        Map<String, String> effect = new LinkedHashMap<>();
        for (EventEffect v : EventEffect.values()) effect.put(v.name(), v.getDescription());

        Map<String, String> cctvSource = new LinkedHashMap<>();
        for (CctvSource v : CctvSource.values()) cctvSource.put(v.name(), v.getDescription());

        // 이벤트 1건에서 발행되는 채널(tbl_event_action.channel)과 그 진행 상태.
        // 다른 enum 과 달리 값이 셋이다 — 화면이 제목·부연·준비중 배지를 다 이 메타로 그린다.
        Map<String, ChannelMeta> notificationChannel = new LinkedHashMap<>();
        for (NotificationChannel v : NotificationChannel.values())
            notificationChannel.put(v.name(), new ChannelMeta(v.getLabel(), v.getDescription(), v.isReady()));

        Map<String, String> actionStatus = new LinkedHashMap<>();
        for (ActionStatus v : ActionStatus.values()) actionStatus.put(v.name(), v.getDescription());

        // active/acked 두 축의 조합 파생값 — 화면 배지 라벨을 이 메타로 구동한다.
        Map<String, String> eventStatus = new LinkedHashMap<>();
        for (EventStatus v : EventStatus.values()) eventStatus.put(v.name(), v.getDescription());

        Map<String, String> suppressMode = new LinkedHashMap<>();
        for (SuppressMode v : SuppressMode.values()) suppressMode.put(v.name(), v.getDescription());

        // Collections.unmodifiableMap(LinkedHashMap) — Map.copyOf 는 iteration order 미보장이라
        // "선언 순서 = 정렬 순서" 계약(프론트가 레벨/채널 순서를 이 순서로 신뢰)을 지키려 순서 보존 래핑.
        return new EventMetaResponse(
                Collections.unmodifiableMap(scope),
                Collections.unmodifiableMap(level),
                Collections.unmodifiableMap(operator),
                Collections.unmodifiableMap(conditionKind),
                Collections.unmodifiableMap(effect),
                Collections.unmodifiableMap(cctvSource),
                Collections.unmodifiableMap(notificationChannel),
                Collections.unmodifiableMap(actionStatus),
                Collections.unmodifiableMap(eventStatus),
                Collections.unmodifiableMap(suppressMode)
        );
    }

    /**
     * 출력 채널 하나의 화면용 정보.
     *
     * @param label       카드 제목
     * @param description 카드 부연
     * @param ready       서버 쪽 실행기가 있는가. false 면 화면이 "준비중"으로 잠근다
     */
    public record ChannelMeta(String label, String description, boolean ready) {
    }
}
