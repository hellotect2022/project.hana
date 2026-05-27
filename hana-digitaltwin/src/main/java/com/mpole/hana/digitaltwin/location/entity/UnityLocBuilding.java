package com.mpole.hana.digitaltwin.location.entity;

import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Id;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_unity_loc_building")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UnityLocBuilding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "building_id")
    private Long buildingId;

    @Column(name = "building_name", nullable = false, length = 200)
    private String buildingName;

    @OneToMany(mappedBy = "building", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UnityLocFloor> floors = new ArrayList<>();
}
