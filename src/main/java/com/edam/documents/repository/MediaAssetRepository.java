package com.edam.documents.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.documents.model.MediaAsset;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
	Optional<MediaAsset> findByPublicId(String publicId);
	List<MediaAsset> findByIdInOrderByUploadedAtDesc(List<UUID> ids);
}
