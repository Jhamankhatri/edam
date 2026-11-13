package com.edam.workflow.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * An immutable revision of a workflow definition (Module I).
 *
 * <h2>The three states, and what each permits</h2>
 * <ul>
 *   <li><strong>DRAFT</strong> — editable. Any number may exist. No request may be started on a draft,
 *       because a half-configured chain would stall after the first approval.</li>
 *   <li><strong>PUBLISHED</strong> — exactly one per definition, enforced by a partial unique index.
 *       Immutable: editing a published version is how a bank accidentally changes how an in-flight
 *       request behaves. To change behaviour, publish a new version.</li>
 *   <li><strong>RETIRED</strong> — was published, may still be referenced by in-flight instances, and
 *       cannot start new ones.</li>
 * </ul>
 *
 * <h2>Why a published version must never be mutated</h2>
 * A {@code WorkflowInstance} pins its {@code workflowVersionId}. If the version's steps changed after
 * publishing, every in-flight request would silently re-route mid-approval: an approver who already acted
 * would be removed from a chain, or added to one. The engine therefore never loads "the latest version" for
 * a running instance, and the service refuses to modify a non-draft version at all.
 *
 * <h2>Published metadata is not optional</h2>
 * The schema requires {@code publishedBy} and {@code publishedAt} whenever the status is PUBLISHED. A
 * published workflow with no publisher would make "who changed the approval rules" unanswerable.
 */
@Entity
@Table(name = "workflow_versions")
public class WorkflowVersion extends AuditableEntity {

	/** Lifecycle of a workflow revision. */
	public enum Status {

		/** Editable; no request may use it. */
		DRAFT,
		/** The one active revision. Immutable. */
		PUBLISHED,
		/** Superseded. May still be referenced by instances created while it was published. */
		RETIRED

	}

	@Column(name = "workflow_definition_id", nullable = false, updatable = false)
	private UUID workflowDefinitionId;

	@Column(name = "version_number", nullable = false, updatable = false)
	private int versionNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "version_status", nullable = false, length = 20)
	private Status status = Status.DRAFT;

	@Column(name = "effective_from")
	private Instant effectiveFrom;

	@Column(name = "effective_to")
	private Instant effectiveTo;

	@Column(name = "published_by")
	private UUID publishedBy;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "retired_by")
	private UUID retiredBy;

	@Column(name = "retired_at")
	private Instant retiredAt;

	/**
	 * The revision that replaced this one, when it was retired by a publication.
	 *
	 * <p>Null when the definition was retired outright (no workflow should be started for that request type
	 * any more) rather than superseded. The distinction matters to an administrator reading the history:
	 * "replaced by v4" and "switched off" are different decisions.
	 */
	@Column(name = "superseded_by_version_id")
	private UUID supersededByVersionId;

	@OneToMany(mappedBy = "workflowVersion", cascade = CascadeType.ALL, orphanRemoval = true,
			fetch = FetchType.LAZY)
	@OrderBy("stepOrder ASC")
	private List<WorkflowStep> steps = new ArrayList<>();

	@OneToMany(mappedBy = "workflowVersion", cascade = CascadeType.ALL, orphanRemoval = true,
			fetch = FetchType.LAZY)
	private List<WorkflowState> states = new ArrayList<>();

	@OneToMany(mappedBy = "workflowVersion", cascade = CascadeType.ALL, orphanRemoval = true,
			fetch = FetchType.LAZY)
	private List<WorkflowTransition> transitions = new ArrayList<>();

	/** True when the revision may still be edited. */
	public boolean isEditable() {
		return this.status == Status.DRAFT;
	}

	/** True when new instances may start on this revision. */
	public boolean isStartable() {
		return this.status == Status.PUBLISHED;
	}

	public UUID getWorkflowDefinitionId() {
		return this.workflowDefinitionId;
	}

	public void setWorkflowDefinitionId(UUID workflowDefinitionId) {
		this.workflowDefinitionId = workflowDefinitionId;
	}

	public int getVersionNumber() {
		return this.versionNumber;
	}

	public void setVersionNumber(int versionNumber) {
		this.versionNumber = versionNumber;
	}

	public Status getStatus() {
		return this.status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public Instant getEffectiveFrom() {
		return this.effectiveFrom;
	}

	public void setEffectiveFrom(Instant effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public Instant getEffectiveTo() {
		return this.effectiveTo;
	}

	public void setEffectiveTo(Instant effectiveTo) {
		this.effectiveTo = effectiveTo;
	}

	public UUID getPublishedBy() {
		return this.publishedBy;
	}

	public void setPublishedBy(UUID publishedBy) {
		this.publishedBy = publishedBy;
	}

	public Instant getPublishedAt() {
		return this.publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

	public List<WorkflowStep> getSteps() {
		return this.steps;
	}

	public void setSteps(List<WorkflowStep> steps) {
		this.steps = steps;
	}

	public List<WorkflowState> getStates() {
		return this.states;
	}

	public void setStates(List<WorkflowState> states) {
		this.states = states;
	}

	public List<WorkflowTransition> getTransitions() {
		return this.transitions;
	}

	public void setTransitions(List<WorkflowTransition> transitions) {
		this.transitions = transitions;
	}

	public UUID getRetiredBy() {
		return this.retiredBy;
	}

	public Instant getRetiredAt() {
		return this.retiredAt;
	}

	public UUID getSupersededByVersionId() {
		return this.supersededByVersionId;
	}

	/**
	 * Publishes this revision.
	 *
	 * <p>The only transition into {@code PUBLISHED}. It requires the publisher because the schema does, and
	 * requires a definition because a version with no definition could not be found by any request type.
	 *
	 * @param publisher the administrator who published it
	 * @param when the effective instant
	 * @throws IllegalStateException if the revision is not a draft
	 */
	public void publish(UUID publisher, Instant when) {
		if (this.status != Status.DRAFT) {
			throw new IllegalStateException("Only a draft version can be published; this one is " + this.status);
		}
		if (this.workflowDefinitionId == null) {
			throw new IllegalStateException("A version cannot be published without a definition");
		}
		this.status = Status.PUBLISHED;
		this.publishedBy = publisher;
		this.publishedAt = when;
		this.effectiveFrom = (this.effectiveFrom == null) ? when : this.effectiveFrom;
	}

	/**
	 * Retires a published revision, optionally naming its replacement.
	 *
	 * <p>Retirement never touches in-flight instances: each pins this version's id, so its steps keep
	 * governing the requests that started under it. Retirement only stops <em>new</em> starts.
	 *
	 * @param actor the administrator who retired it
	 * @param when the instant
	 * @param replacement the version that supersedes this one, or {@code null} if the route is switched off
	 */
	public void retire(UUID actor, Instant when, UUID replacement) {
		if (this.status == Status.DRAFT) {
			throw new IllegalStateException("A draft version was never in use and cannot be retired");
		}
		if (replacement != null && replacement.equals(getId())) {
			throw new IllegalArgumentException("A version cannot supersede itself");
		}
		this.status = Status.RETIRED;
		this.retiredBy = actor;
		this.retiredAt = when;
		this.supersededByVersionId = replacement;
	}

}
