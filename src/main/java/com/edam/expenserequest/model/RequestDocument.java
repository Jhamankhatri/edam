package com.edam.expenserequest.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A document attached to a request (Module G, bridging to Module H).
 *
 * <h2>No file content here, by design</h2>
 * The row references {@code media_assets}; the bytes live in object storage. Storing files in the database
 * would bloat backups, defeat streaming and make the storage provider choice irreversible. Phase 4
 * implements the upload and download paths; this entity records which asset belongs to which request, under
 * which document type, and whether it was mandatory at the time.
 *
 * <h2>Why {@code isMandatory} and {@code requiredByRule} are stored, not derived</h2>
 * "Was this document required?" is a question about the moment of submission. Configuration changes later —
 * a bank decides GRNs are no longer required — and recomputing the answer from today's rules would rewrite
 * history, making an approver's decision look unexplained. Both values are therefore snapshots.
 */
@Entity
@Table(name = "request_documents")
public class RequestDocument {

	/**
	 * The document categories the schema permits.
	 *
	 * <p>Declared here as constants rather than an enum because the column is {@code VARCHAR} with a CHECK,
	 * and because a future migration may add a category without a code change; the service validates
	 * against this list so an unknown value is rejected with a readable message instead of a constraint
	 * violation.
	 */
	public static final java.util.Set<String> TYPES = java.util.Set.of(
			"SUPPORTING", "SUPPORTING_DOCUMENT",
			"QUOTATION", "INVOICE", "APPROVAL_DOCUMENT",
			"TAX", "TAX_DOCUMENT", "GRN", "GRN_DOCUMENT", "CAPEX_DOCUMENT",
			"WARRANTY", "BUDGET", "OTHER");

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "expense_request_id", nullable = false)
	private UUID expenseRequestId;

	@Column(name = "media_asset_id", nullable = false)
	private UUID mediaAssetId;

	@Column(name = "document_type", nullable = false, length = 60)
	private String documentType;

	@Column(name = "is_mandatory", nullable = false)
	private boolean mandatory;

	@Column(name = "required_by_rule", length = 150)
	private String requiredByRule;

	@Column(name = "uploaded_by")
	private UUID uploadedBy;

	@Column(name = "description")
	private String description;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	protected RequestDocument() {
	}

	public RequestDocument(UUID expenseRequestId, UUID mediaAssetId, String documentType, boolean mandatory,
			String requiredByRule, UUID uploadedBy, Instant uploadedAt) {
		this.expenseRequestId = expenseRequestId;
		this.mediaAssetId = mediaAssetId;
		this.documentType = documentType;
		this.mandatory = mandatory;
		this.requiredByRule = requiredByRule;
		this.uploadedBy = uploadedBy;
		this.uploadedAt = uploadedAt;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getExpenseRequestId() {
		return this.expenseRequestId;
	}

	public UUID getMediaAssetId() {
		return this.mediaAssetId;
	}

	public String getDocumentType() {
		return this.documentType;
	}

	public boolean isMandatory() {
		return this.mandatory;
	}

	public String getRequiredByRule() {
		return this.requiredByRule;
	}

	public UUID getUploadedBy() {
		return this.uploadedBy;
	}

	public Instant getUploadedAt() {
		return this.uploadedAt;
	}

	public String getDescription() { return this.description; }
	public void setDescription(String v) { this.description = v; }

}
