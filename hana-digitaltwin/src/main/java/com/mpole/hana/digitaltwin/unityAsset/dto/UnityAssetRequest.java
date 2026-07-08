package com.mpole.hana.digitaltwin.unityAsset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnityAssetRequest(
        @NotBlank @Size(max = 200) String assetName,
        String description,
        String assetType,
        Boolean active
) {
}
