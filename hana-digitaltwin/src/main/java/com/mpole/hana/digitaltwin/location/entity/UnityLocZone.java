package com.mpole.hana.digitaltwin.location.entity;

import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Id;

@Entity
@Table(name = "tbl_unity_loc_zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UnityLocZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long zoneId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private UnityLocFloor floor;

    @Column(name = "zone_name", nullable = false, length = 200)
    private String zoneName;

    @Column(name = "zone_mesh_name", length = 200)
    private String zoneMeshName;

    /** 메쉬 표현 구분: "2D" | "3D". WA-ZONE 추가는 2D, 조명/화재 구역 추가는 3D */
    @Column(name = "mesh_type", length = 8)
    @Builder.Default
    private String meshType = "2D";

    /** 구역(존) 정보 수정 — 층/이름/메쉬명/메쉬타입 갱신 */
    public void updateInfo(UnityLocFloor floor, String zoneName, String zoneMeshName, String meshType) {
        this.floor = floor;
        this.zoneName = zoneName;
        this.zoneMeshName = zoneMeshName;
        if (meshType != null) this.meshType = meshType;
    }
}
