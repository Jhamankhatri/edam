package com.edam.documents.repository;

import java.util.List;
import java.util.UUID;

import com.edam.documents.model.DocumentScanEvent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentScanEventRepository extends JpaRepository<DocumentScanEvent, UUID> {
	List<DocumentScanEvent> findByMediaAssetIdOrderByScanStartedAtDesc(UUID mediaAssetId);
}
