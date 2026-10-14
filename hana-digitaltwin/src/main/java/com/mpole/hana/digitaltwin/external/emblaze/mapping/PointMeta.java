package com.mpole.hana.digitaltwin.external.emblaze.mapping;

/**
 * {@code tbl_device_point} 한 행의 표시·식별 정보 — 현재값에 같이 실어 보내는 값들.
 *
 * <h3>왜 필요한가</h3>
 * <p>{@code tbl_device_current_value.points} 는 JSONB 라 <b>거기 적힌 것이 전부</b>다.
 * 조회 API({@code RealtimePointResponse.from})가 그 JSON 을 그대로 읽어 내보내지,
 * {@code tbl_device_point} 와 다시 조인하지 않는다.</p>
 *
 * <p>그래서 쓸 때 안 채우면 <b>영원히 비어 있다</b>. 실제로 emblaze 가 쓴 행은
 * {@code pointDisplayName}·{@code unit}·{@code pointkey} 가 전부 null 이어서
 * 관리자 "실시간 값 확인" 화면에 {@code -} 로 나왔다. gateway 가 쓴 행은
 * {@code View01BundleService} 가 채워 넣어 멀쩡한데, 같은 테이블에 두 품질이 섞여 있었다.</p>
 *
 * <p><b>매번 조회하지 않는다.</b> {@link AmqpPointMappingCache} 가 기동 시 한 번 읽어
 * {@code (deviceId, tagName)} 으로 색인해 둔다.</p>
 *
 * @param pointId          진짜 point_id. 예전엔 {@code 0} 을 박았다 — 이벤트 평가의 활성 키가
 *                         {@code ruleId|deviceId|pointId} 라, 0 이면 한 장비의 포인트들이
 *                         한 슬롯을 공유해 서로를 막는다
 * @param pointKey         point_key ({@code XnPointData} 의 필드명은 소문자 {@code pointkey} 다)
 * @param pointDisplayName 화면 표시명
 * @param unit             단위. 없는 태그는 빈 문자열
 * @param objectType       AI / DI 등 {@code point_type}
 * @param deviceCode       {@code ref_device_code} — emblaze uuid
 * @param pointCode        {@code ref_point_code} — 벤더 필드명. 파생 포인트({@code occ})는 비어 있다
 */
public record PointMeta(
        Long pointId,
        String pointKey,
        String pointDisplayName,
        String unit,
        String objectType,
        String deviceCode,
        String pointCode
) {
}
