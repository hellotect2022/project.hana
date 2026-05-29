package com.mpole.hana.digitaltwin.unityAsset.dto;

import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record UnityAssetResponse(
        Long assetId,
        String assetName,
        String description,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static UnityAssetResponse from(UnityAsset entity) {
        return UnityAssetResponse.builder()
                .assetId(entity.getAssetId())
                .assetName(entity.getAssetName())
                .description(entity.getDescription())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
