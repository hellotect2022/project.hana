package com.mpole.hana.digitaltwin.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 콜라이더(구역) 등록·수정 요청.
 *
 * @param parentId   상위 구역 id. <b>null 이면 층 직속 최상위 구역</b>. 신규 필드
 * @param zoneCode   외부 연동용 식별자(HQS stationId/wcId/wcBoothId). 값이 있으면 유일해야 한다. 신규 필드
 * @param attributes 구역 종류별 부가 속성. 예: {@code {"type":"TOILET","gender":"M"}}. 신규 필드
 * @param zoneMeshName 3D 콜라이더 메시명. <b>필수이며 전체에서 유일</b>하다.
 *                     자식 구역(부스 등)도 반드시 값을 가진다 — 클라이언트가 null 을 만나지 않도록
 *                     계약을 단순하게 유지하기 위해서다. 실제 Unity 메시가 아직 없더라도
 *                     식별자 규칙에 맞는 이름을 부여한다(예: {@code collider_4f_men_wc_01_b01}).
 *                     매칭되는 메시가 없으면 3D 에서 그려지지 않을 뿐 동작에는 지장이 없다.
 */
public record UnityZoneRequest(
        @NotNull(message = "층(floorId)은 필수입니다.") Long floorId,
        @NotBlank(message = "이름(name)은 필수입니다.") @Size(max = 200) String zoneName,

        /** 필수 · 전체 유일. 자식 구역도 값을 가져야 한다(클라이언트 null 방어 부담 제거). */
        @NotBlank(message = "mesh_name은 필수입니다.") @Size(max = 200) String zoneMeshName,

        /** "2D" | "3D" — 미지정 시 2D */
        @Size(max = 8) String meshType,

        /** 상위 구역 id. null 이면 최상위 */
        Long parentId,

        /** 외부 연동 식별자. 값이 있으면 유일 */
        @Size(max = 200) String zoneCode,

        /** 구역 종류별 부가 속성 */
        Map<String, Object> attributes
) {
}
