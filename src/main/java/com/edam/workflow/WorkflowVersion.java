package com.edam.workflow;

import java.time.Instant;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One immutable version of a workflow's step model (BRD §8.3, §8.6).
 *
 * <h2>Stored as a validated document, not executed as code</h2>
 * {@code definitionJson} is a declarative step list — codes, labels, approver roles, quorum rules
 * and sequence. It is parsed into {@link WorkflowSteps} and validated against a closed set of
 * approver roles on publish. Nothing in it is ever evaluated as an expression, a script or SQL: the
 * user's constraint forbids executing admin-configurable logic, and a workflow that could name an
 * arbitrary permission or class would be exactly that.
 *
 * <p>{@code checksum} is the SHA-256 of the definition document. It makes "the published version has
 * not been altered since approval" verifiable, and it lets a re-publish of identical content be
 * recognised instead of creating a duplicate version.
 *
 * <h2>Immutability</h2>
 * Published rows are not edited: the entity exposes no mutators for the document, status changes go
 * through {@link #publish(String, Instant)} / {@link #retire()}, and the service refuses to touch a
 * non-draft row's content.
 */
@Entity
@Table(name = "workflow_version")
public class WorkflowVersion extends InstitutionOwnedEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "definition_id", nullable = false)
	private WorkflowDefinition definition;

	@Min(1)
	@Column(name = "version_number", nullable = false, updatable = false)
	private int versionNumber;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private WorkflowVersionStatus status = WorkflowVersionStatus.DRAFT;

	@NotBlank
	@Column(name = "definition_json", nullable = false, length = 20000)
	private String definitionJson;

	@NotBlank
	@Column(name = "checksum", nullable = false, length = 64)
	private String checksum;

	@Size(max = 1000)
	@Column(name = "change_note", length = 1000)
	private String changeNote;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Size(max = 64)
	@Column(name = "published_by", length = 64)
	private String publishedBy;

	protected WorkflowVersion() {
		// JPA
	}

	public WorkflowVersion(WorkflowDefinition definition, int versionNumber, String definitionJson, String checksum,
			String changeNote) {
		this.definition = definition;
		this.versionNumber = versionNumber;
		this.definitionJson = definitionJson;
		this.checksum = checksum;
		this.changeNote = changeNote;
	}

	/** Replace the document of a draft. Refused once published. */
	public void updateDraft(String definitionJson, String checksum, String changeNote) {
		if (this.status != WorkflowVersionStatus.DRAFT) {
			throw new IllegalStateException("A published workflow version is immutable; create a new version");
		}
		this.definitionJson = definitionJson;
		this.checksum = checksum;
		this.changeNote = changeNote;
	}

	public void publish(String publishedBy, Instant when) {
		if (this.status != WorkflowVersionStatus.DRAFT) {
			throw new IllegalStateException("Only a draft can be published");
		}
		this.status = WorkflowVersionStatus.PUBLISHED;
		this.publishedBy = publishedBy;
		this.publishedAt = when;
	}

	public void retire() {
		this.status = WorkflowVersionStatus.RETIRED;
	}

	public boolean isDraft() {
		return this.status == WorkflowVersionStatus.DRAFT;
	}

	public boolean isPublished() {
		return this.status == WorkflowVersionStatus.PUBLISHED;
	}

	public WorkflowDefinition getDefinition() {
		return this.definition;
	}

	public int getVersionNumber() {
		return this.versionNumber;
	}

	public WorkflowVersionStatus getStatus() {
		return this.status;
	}

	public String getDefinitionJson() {
		return this.definitionJson;
	}

	public String getChecksum() {
		return this.checksum;
	}

	public String getChangeNote() {
		return this.changeNote;
	}

	public Instant getPublishedAt() {
		return this.publishedAt;
	}

	public String getPublishedBy() {
		return this.publishedBy;
	}

}
