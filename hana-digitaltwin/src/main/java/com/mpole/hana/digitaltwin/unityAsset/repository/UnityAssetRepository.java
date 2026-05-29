package com.mpole.hana.digitaltwin.unityAsset.repository;

import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnityAssetRepository extends JpaRepository<UnityAsset, Long> {
    Optional<UnityAsset> findByAssetName(String assetName);

    boolean existsByAssetName(String assetName);

    boolean existsByAssetNameAndAssetIdNot(String assetName, Long assetId);
}
