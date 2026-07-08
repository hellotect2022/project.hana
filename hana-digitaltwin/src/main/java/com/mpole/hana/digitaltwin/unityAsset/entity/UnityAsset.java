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

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 32)
    @Builder.Default
    private AssetType assetType = AssetType.EQUIPMENT;

    @Builder.Default
    private Boolean active = true;
}
