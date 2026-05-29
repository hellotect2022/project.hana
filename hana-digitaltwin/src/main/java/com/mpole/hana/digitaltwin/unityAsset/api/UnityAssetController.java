package com.mpole.hana.digitaltwin.unityAsset.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.unityAsset.dto.UnityAssetRequest;
import com.mpole.hana.digitaltwin.unityAsset.dto.UnityAssetResponse;
import com.mpole.hana.digitaltwin.unityAsset.service.UnityAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/unity-assets")
@RequiredArgsConstructor
public class UnityAssetController {

    private final UnityAssetService unityAssetService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UnityAssetResponse>>> getUnityAssets(
            @RequestParam(required = false) Boolean activeOnly) {
        List<UnityAssetResponse> assets = unityAssetService.getAllAssets(activeOnly);
        return ResponseEntity.ok(ApiResponse.success("Unity 3D 에셋 목록 조회", assets));
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<ApiResponse<UnityAssetResponse>> getUnityAsset(@PathVariable Long assetId) {
        UnityAssetResponse asset = unityAssetService.getAsset(assetId);
        return ResponseEntity.ok(ApiResponse.success("Unity 3D 에셋 조회", asset));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UnityAssetResponse>> createUnityAsset(
            @Valid @RequestBody UnityAssetRequest request) {
        UnityAssetResponse created = unityAssetService.createAsset(request);
        return ResponseEntity.ok(ApiResponse.success("Unity 3D 에셋 생성", created));
    }

    @PutMapping("/{assetId}")
    public ResponseEntity<ApiResponse<UnityAssetResponse>> updateUnityAsset(
            @PathVariable Long assetId,
            @Valid @RequestBody UnityAssetRequest request) {
        UnityAssetResponse updated = unityAssetService.updateAsset(assetId, request);
        return ResponseEntity.ok(ApiResponse.success("Unity 3D 에셋 수정", updated));
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<ApiResponse<Void>> deleteUnityAsset(@PathVariable Long assetId) {
        unityAssetService.deleteAsset(assetId);
        return ResponseEntity.ok(ApiResponse.success("Unity 3D 에셋 삭제"));
    }
}
