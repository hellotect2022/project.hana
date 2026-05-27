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

}
