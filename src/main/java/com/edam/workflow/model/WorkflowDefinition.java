package com.edam.workflow.model;

import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A named approval process (Module I).
 *
 * <h2>Definition vs version</h2>
 * The definition is the stable identity ("OPEX approval", "CAPEX approval"); the versions are its
 * immutable revisions. Nothing in the engine reads a definition to decide behaviour — it reads a
 * <em>version</em>. That separation is what lets a bank publish a change without altering a request that
 * is already in flight.
 *
 * <h2>Why a dedicated workflow instead of deriving the chain from the delegation matrix</h2>
 * The delegation matrix decides <em>which offices</em> must approve a classification. The workflow decides
 * the state machine: which states exist, what a revert returns to, whether parallel branches are allowed,
 * and which transitions are legal. Both are needed: a bank may route two different amount bands to the same
 * offices but through different state machines (for example, one requiring a CFO counter-signature only
 * above a threshold).
 */
@Entity
@Table(name = "workflow_definitions")
public class WorkflowDefinition extends AuditableEntity {

	@Column(name = "workflow_code", nullable = false, length = 64)
	private String code;

	@Column(name = "workflow_name", nullable = false, length = 200)
	private String name;

	@Column(name = "description")
	private String description;

	/** Null means the definition is not bound to one request type and may be selected explicitly. */
	@Column(name = "request_type_id")
	private UUID requestTypeId;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	public String getCode() {
		return this.code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public UUID getRequestTypeId() {
		return this.requestTypeId;
	}

	public void setRequestTypeId(UUID requestTypeId) {
		this.requestTypeId = requestTypeId;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
