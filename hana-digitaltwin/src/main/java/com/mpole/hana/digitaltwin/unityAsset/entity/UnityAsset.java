package com.mpole.hana.digitaltwin.unityAsset.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tbl_unity_3d_asset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnityAsset extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "asset_name", nullable = false, unique = true, length = 200)
    private String assetName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    private Boolean active = true;
}
