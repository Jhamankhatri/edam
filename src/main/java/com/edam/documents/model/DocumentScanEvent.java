package com.edam.documents.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** One attempt to scan an asset for malware. Append-only; never updated. */
@Entity
@Table(name = "doc_scan_events")
public class DocumentScanEvent {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "media_asset_id", nullable = false)
	private UUID mediaAssetId;

	@Column(name = "scan_status", nullable = false, length = 32)
	private String scanStatus;

	@Column(name = "scanner", nullable = false, length = 60)
	private String scanner;

	@Column(name = "scanner_version", length = 60)
	private String scannerVersion;

	@Column(name = "threat_name", length = 255)
	private String threatName;

	@Column(name = "scan_started_at", nullable = false)
	private Instant scanStartedAt = Instant.now();

	@Column(name = "scan_finished_at")
	private Instant scanFinishedAt;

	@Column(name = "detail")
	private String detail;

	protected DocumentScanEvent() {}

	public DocumentScanEvent(UUID mediaAssetId, String scanStatus, String scanner, String scannerVersion,
			String threatName, String detail) {
		this.mediaAssetId = mediaAssetId;
		this.scanStatus = scanStatus;
		this.scanner = scanner;
		this.scannerVersion = scannerVersion;
		this.threatName = threatName;
		this.detail = detail;
		this.scanStartedAt = Instant.now();
	}

	public UUID getId() { return this.id; }
	public UUID getMediaAssetId() { return this.mediaAssetId; }
	public String getScanStatus() { return this.scanStatus; }
	public String getScanner() { return this.scanner; }
	public String getScannerVersion() { return this.scannerVersion; }
	public String getThreatName() { return this.threatName; }
	public Instant getScanStartedAt() { return this.scanStartedAt; }
	public Instant getScanFinishedAt() { return this.scanFinishedAt; }
	public String getDetail() { return this.detail; }

	public void finish() { this.scanFinishedAt = Instant.now(); }
}

