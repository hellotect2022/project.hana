package com.mpole.hana.digitaltwin.unityAsset.dto;

import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record UnityAssetResponse(
        Long assetId,
        String assetName,
        String description,
        String assetType,
        String assetTypeLabel,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static UnityAssetResponse from(UnityAsset entity) {
        return UnityAssetResponse.builder()
                .assetId(entity.getAssetId())
                .assetName(entity.getAssetName())
                .description(entity.getDescription())
                .assetType(entity.getAssetType() != null ? entity.getAssetType().name() : null)
                .assetTypeLabel(entity.getAssetType() != null ? entity.getAssetType().getLabel() : null)
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
