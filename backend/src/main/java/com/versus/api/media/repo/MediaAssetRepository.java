package com.versus.api.media.repo;

import com.versus.api.media.domain.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    List<MediaAsset> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
