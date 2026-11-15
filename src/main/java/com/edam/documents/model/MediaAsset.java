package com.edam.documents.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A reference to a binary asset stored in an external provider (Cloudinary). No bytes live
 * in this row — only metadata and the identifiers needed to retrieve or delete the object.
 *
 * <p>Matches the V6 table as widened by V21. Lifecycle (ACTIVE/ARCHIVED/DELETED) is tracked
 * separately from the malware-scan outcome (PENDING_SCAN/CLEAN/INFECTED/SCAN_FAILED): a
 * deleted row can still carry the last scan verdict for the audit log.
 */
@Entity
@Table(name = "media_assets")
public class MediaAsset {

	public enum Lifecycle { ACTIVE, ARCHIVED, DELETED, QUARANTINED }
	public enum ScanStatus { PENDING_SCAN, CLEAN, INFECTED, SCAN_FAILED }
	public enum DocumentCategory {
		QUOTATION, INVOICE, SUPPORTING_DOCUMENT, APPROVAL_DOCUMENT,
		TAX_DOCUMENT, GRN_DOCUMENT, CAPEX_DOCUMENT, OTHER
	}

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "original_file_name", nullable = false, length = 255)
	private String originalFileName;

	@Column(name = "stored_file_name", length = 255)
	private String storedFileName;

	@Column(name = "storage_provider", nullable = false, length = 32)
	private String storageProvider;

	@Column(name = "storage_reference", nullable = false, length = 500)
	private String storageReference;

	@Column(name = "public_id", length = 500)
	private String publicId;

	@Column(name = "asset_id", length = 100)
	private String assetId;

	@Column(name = "secure_url")
	private String secureUrl;

	@Column(name = "resource_type", length = 32)
	private String resourceType;

	@Column(name = "file_format", length = 32)
	private String fileFormat;

	@Column(name = "mime_type", length = 150)
	private String mimeType;

	@Column(name = "file_size", nullable = false)
	private long fileSize;

	@Column(name = "checksum", length = 64)
	private String checksum;

	@Column(name = "document_category", length = 60)
	private String documentCategory = DocumentCategory.OTHER.name();

	public void setDocumentCategory(String v) {
		// accept both legacy (SUPPORTING) and new (SUPPORTING_DOCUMENT) category codes
		this.documentCategory = v;
	}

	@Column(name = "description")
	private String description;

	@Column(name = "lifecycle_status", nullable = false, length = 32)
	private String lifecycleStatus = Lifecycle.ACTIVE.name();

	@Column(name = "scan_status", nullable = false, length = 32)
	private String scanStatus = ScanStatus.PENDING_SCAN.name();

	@Column(name = "scanned_at")
	private Instant scannedAt;

	@Column(name = "uploaded_by")
	private UUID uploadedBy;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt = Instant.now();

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@Version
	@Column(name = "version", nullable = false)
	private long version = 0L;

	public UUID getId() { return this.id; }
	public String getOriginalFileName() { return this.originalFileName; }
	public void setOriginalFileName(String v) { this.originalFileName = v; }
	public String getStoredFileName() { return this.storedFileName; }
	public void setStoredFileName(String v) { this.storedFileName = v; }
	public String getStorageProvider() { return this.storageProvider; }
	public void setStorageProvider(String v) { this.storageProvider = v; }
	public String getStorageReference() { return this.storageReference; }
	public void setStorageReference(String v) { this.storageReference = v; }
	public String getPublicId() { return this.publicId; }
	public void setPublicId(String v) { this.publicId = v; }
	public String getAssetId() { return this.assetId; }
	public void setAssetId(String v) { this.assetId = v; }
	public String getSecureUrl() { return this.secureUrl; }
	public void setSecureUrl(String v) { this.secureUrl = v; }
	public String getResourceType() { return this.resourceType; }
	public void setResourceType(String v) { this.resourceType = v; }
	public String getFileFormat() { return this.fileFormat; }
	public void setFileFormat(String v) { this.fileFormat = v; }
	public String getMimeType() { return this.mimeType; }
	public void setMimeType(String v) { this.mimeType = v; }
	public long getFileSize() { return this.fileSize; }
	public void setFileSize(long v) { this.fileSize = v; }
	public String getChecksum() { return this.checksum; }
	public void setChecksum(String v) { this.checksum = v; }
	public String getDocumentCategory() { return this.documentCategory; }
	public void setDocumentCategory(String v) { this.documentCategory = v; }
	public String getDescription() { return this.description; }
	public void setDescription(String v) { this.description = v; }
	public String getLifecycleStatus() { return this.lifecycleStatus; }
	public void setLifecycleStatus(String v) { this.lifecycleStatus = v; }
	public String getScanStatus() { return this.scanStatus; }
	public void setScanStatus(String v) { this.scanStatus = v; }
	public Instant getScannedAt() { return this.scannedAt; }
	public void setScannedAt(Instant v) { this.scannedAt = v; }
	public UUID getUploadedBy() { return this.uploadedBy; }
	public void setUploadedBy(UUID v) { this.uploadedBy = v; }
	public Instant getUploadedAt() { return this.uploadedAt; }
	public Instant getDeletedAt() { return this.deletedAt; }
	public Instant getCreatedAt() { return this.createdAt; }
	public Instant getUpdatedAt() { return this.updatedAt; }
	public long getVersion() { return this.version; }

	public void markUploaded(UUID userId) {
		Instant now = Instant.now();
		this.uploadedBy = userId;
		this.uploadedAt = now;
	}

	public void markDeleted() {
		this.deletedAt = Instant.now();
		this.lifecycleStatus = Lifecycle.DELETED.name();
	}

	@PrePersist
	void onInsert() {
		Instant now = Instant.now();
		if (this.createdAt == null) this.createdAt = now;
		if (this.updatedAt == null) this.updatedAt = now;
		if (this.uploadedAt == null) this.uploadedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}
}
