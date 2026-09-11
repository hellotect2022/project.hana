package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;

/**
 * Unity 씬 트리({@code GET /api/device/location-info})의 구역 항목.
 *
 * <p><b>계층이 생겨도 {@code zoneList} 는 평면을 유지한다.</b> 자식 구역을 중첩 배열로
 * 바꾸면 이 응답을 쓰는 3D 뷰어가 깨진다. 대신 {@code parentId}/{@code depth} 를 실어
 * 클라이언트가 필요할 때 트리를 복원하게 한다.</p>
 *
 * <p>{@code depth} 가 중요한 이유 — 뷰어는 장비 위치에서 아래로 레이를 쏴 구역을 판정하는데,
 * 구역이 중첩되면 바깥(화장실) 콜라이더가 먼저 맞는다. 맞은 것들 중 <b>depth 가 가장 깊은</b>
 * 구역을 골라야 부스가 잡힌다.</p>
 *
 * <p>{@code zoneMeshName} 은 <b>항상 값이 있다</b>(등록 시 필수). 자식 구역도 마찬가지라
 * 클라이언트가 null 을 방어할 필요가 없다. 다만 실제 Unity 메시가 없는 이름일 수 있고,
 * 그 경우 3D 에서 매칭되지 않아 그려지지 않을 뿐이다.</p>
 *
 * @param parentId 상위 구역 id. null 이면 최상위. 신규 필드
 * @param depth    0=최상위, 1=자식. 신규 필드
 * @param zoneCode 외부 연동 식별자. 신규 필드
 */
public record UnityLocZoneResponse(
        Long zoneId,
        String zoneName,
        String zoneMeshName,
        Long parentId,
        Integer depth,
        String zoneCode
) {
    public static UnityLocZoneResponse from(UnityLocZone zone) {
        var parent = zone.getParent();
        return new UnityLocZoneResponse(
                zone.getZoneId(),
                zone.getZoneName(),
                zone.getZoneMeshName(),
                parent != null ? parent.getZoneId() : null,
                zone.getDepth(),
                zone.getZoneCode()
        );
    }
}
