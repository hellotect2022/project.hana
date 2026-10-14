package com.mpole.hana.digitaltwin.command.dto;

import com.mpole.hana.digitaltwin.command.entity.CommandType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 제어 메타 — 클라이언트가 제어 유형 값·라벨·순서를 하드코딩하지 않고 동기화한다.
 *
 * <p>{@code EventMetaResponse}/{@code SopMetaResponse} 와 동일 패턴: enum 을 {값: 설명} map
 * (LinkedHashMap → 선언 순서 보존)으로 노출한다. 유형이 늘어도 화면 수정이 필요 없다.</p>
 */
public record CommandMetaResponse(
        Map<String, String> commandType
) {

    public static CommandMetaResponse build() {
        Map<String, String> commandType = new LinkedHashMap<>();
        for (CommandType v : CommandType.values()) {
            commandType.put(v.name(), v.getDescription());
        }
        // Map.copyOf 는 iteration order 미보장이라 "선언 순서 = 표시 순서" 계약을 지키려 순서 보존 래핑.
        return new CommandMetaResponse(Collections.unmodifiableMap(commandType));
    }
}
