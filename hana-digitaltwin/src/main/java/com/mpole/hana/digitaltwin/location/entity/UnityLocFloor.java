package com.mpole.hana.digitaltwin.location.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_unity_loc_floor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UnityLocFloor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "floor_id")
    private Long floorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private UnityLocBuilding building;

    @Column(name = "floor_name", nullable = false, length = 200)
    private String floorName;

    @Column(name = "floor_num")
    private Integer floorNum;

    @OneToMany(mappedBy = "floor", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UnityLocZone> zones = new ArrayList<>();
}
