package com.mpole.hana.digitaltwin.unityAsset.service;

import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.unityAsset.dto.UnityAssetRequest;
import com.mpole.hana.digitaltwin.unityAsset.dto.UnityAssetResponse;
import com.mpole.hana.digitaltwin.unityAsset.entity.AssetType;
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import com.mpole.hana.digitaltwin.unityAsset.repository.UnityAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnityAssetService {
    private final UnityAssetRepository unityAssetRepository;
    private final DeviceRepository deviceRepository;

    @Transactional(readOnly = true)
    public List<UnityAssetResponse> getAllAssets(Boolean activeOnly) {
        return unityAssetRepository.findAll().stream()
                .filter(a -> activeOnly == null || !Boolean.TRUE.equals(activeOnly) || Boolean.TRUE.equals(a.getActive()))
                .sorted(Comparator.comparing(a -> a.getAssetName() != null ? a.getAssetName() : ""))
                .map(UnityAssetResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UnityAssetResponse> getActiveAssets() {
        return getAllAssets(true);
    }

    @Transactional(readOnly = true)
    public UnityAssetResponse getAsset(Long assetId) {
        return UnityAssetResponse.from(getAssetOrThrow(assetId));
    }

    @Transactional(readOnly = true)
    public UnityAsset getAssetOrThrow(Long assetId) {
        return unityAssetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("3D 에셋을 찾을 수 없습니다. id=" + assetId));
    }

    @Transactional
    public UnityAssetResponse createAsset(UnityAssetRequest request) {
        String name = request.assetName().trim();
        if (unityAssetRepository.existsByAssetName(name)) {
            throw new IllegalStateException("이미 사용 중인 asset_name 입니다: " + name);
        }
        UnityAsset asset = UnityAsset.builder()
                .assetName(name)
                .description(request.description())
                .assetType(AssetType.fromCode(request.assetType()))
                .active(request.active() == null || request.active())
                .build();
        return UnityAssetResponse.from(unityAssetRepository.save(asset));
    }

    @Transactional
    public UnityAssetResponse updateAsset(Long assetId, UnityAssetRequest request) {
        UnityAsset asset = getAssetOrThrow(assetId);
        String name = request.assetName().trim();
        if (unityAssetRepository.existsByAssetNameAndAssetIdNot(name, assetId)) {
            throw new IllegalStateException("이미 사용 중인 asset_name 입니다: " + name);
        }
        asset.setAssetName(name);
        if (request.description() != null) {
            asset.setDescription(request.description());
        }
        asset.setAssetType(AssetType.fromCode(request.assetType()));
        if (request.active() != null) {
            asset.setActive(request.active());
        }
        return UnityAssetResponse.from(asset);
    }

    @Transactional
    public void deleteAsset(Long assetId) {
        getAssetOrThrow(assetId);
        long linkedDevices = deviceRepository.countByUnityAsset_AssetId(assetId);
        if (linkedDevices > 0) {
            throw new IllegalStateException(
                    "이 에셋을 사용 중인 장비가 " + linkedDevices + "대 있어 삭제할 수 없습니다.");
        }
        unityAssetRepository.deleteById(assetId);
    }
}
