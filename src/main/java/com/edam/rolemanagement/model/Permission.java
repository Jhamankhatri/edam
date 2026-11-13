package com.edam.rolemanagement.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One entry of the product permission catalogue (Module C).
 *
 * <h2>Read-only by design</h2>
 * Permissions are installed by versioned migration and mirrored in
 * {@link com.edam.common.security.Permissions}. There is no create or delete path in the application:
 * a permission a bank invents in the database cannot be enforced by code that does not know it exists,
 * so allowing runtime creation would produce a row that looks authoritative and does nothing.
 *
 * <p>This entity therefore has no setters and does not extend {@code AuditableEntity} — it has no
 * {@code updated_at} because it is never updated.
 */
@Entity
@Table(name = "permissions")
public class Permission {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "code", nullable = false, updatable = false, length = 150)
	private String code;

	@Column(name = "module", nullable = false, updatable = false, length = 100)
	private String module;

	@Column(name = "description", updatable = false)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Permission() {
		// JPA
	}

	public UUID getId() {
		return this.id;
	}

	public String getCode() {
		return this.code;
	}

	public String getModule() {
		return this.module;
	}

	public String getDescription() {
		return this.description;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
