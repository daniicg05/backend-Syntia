package com.syntia.ai.repository;

import com.syntia.ai.model.VisualAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VisualAssetRepository extends JpaRepository<VisualAsset, Long> {
    Optional<VisualAsset> findByAssetKey(String assetKey);
}
