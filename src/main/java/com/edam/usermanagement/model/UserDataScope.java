package com.edam.usermanagement.model;

import java.time.Instant;
import java.util.UUID;

import com.edam.common.security.DataScope;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A user's organizational data scope row (Module C).
 *
 * <h2>Why this is the most security-sensitive small table in EDAM</h2>
 * It answers "over which part of the organization?" for every permission the user holds. A missing row
 * denies everything, which is the correct failure mode: better an administrator who cannot work until
 * their scope is configured than an administrator who can reconfigure the whole bank by default.
 *
 * <h2>No foreign key on scopeReferenceId</h2>
 * The column addresses a department, a branch or an organization unit depending on
 * {@link #scopeType}, and PostgreSQL has no polymorphic foreign key. The alternative — three nullable
 * columns with a CHECK — was rejected because it makes every query branch on which column is populated.
 * Integrity is enforced in the service that creates the scope and verified by
 * {@code DataScopeIntegrityTest}, which resolves every row against its target table.
 *
 * <h2>Why the type is duplicated from DataScope.Type</h2>
 * Deliberately not the same enum. {@link DataScope.Type} is the in-memory authorization model;
 * this is the persistence representation. Coupling them would mean a future rename of a persisted value
 * silently changing the security semantics of existing rows. The mapping in
 * {@link #toDataScope()} is the single translation point, and it fails loudly on an unknown value.
 */
@Entity
@Table(name = "user_data_scopes")
public class UserDataScope {

	@jakarta.persistence.Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "scope_type", nullable = false, updatable = false, length = 50)
	private DataScope.Type scopeType;

	@Column(name = "scope_reference_id", updatable = false)
	private UUID scopeReferenceId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "created_by", updatable = false, length = 100)
	private String createdBy;

	protected UserDataScope() {
		// JPA
	}

	public UserDataScope(UUID userId, DataScope scope, String createdBy) {
		if (userId == null) {
			throw new IllegalArgumentException("A data scope requires a user");
		}
		// DataScope's constructor already guarantees the reference is present exactly when required.
		this.userId = userId;
		this.scopeType = scope.type();
		this.scopeReferenceId = scope.referenceId();
		this.createdAt = Instant.now();
		this.createdBy = createdBy;
	}

	/** The authorization view of this row. */
	public DataScope toDataScope() {
		try {
			return new DataScope(this.scopeType, this.scopeReferenceId);
		}
		catch (IllegalArgumentException ex) {
			// A row that cannot form a valid scope must not silently grant nothing OR everything;
			// failing here makes the corrupt row visible instead of quietly dropping authority.
			throw new IllegalStateException(
					"Corrupt user_data_scopes row id=" + this.id + " type=" + this.scopeType, ex);
		}
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public DataScope.Type getScopeType() {
		return this.scopeType;
	}

	public UUID getScopeReferenceId() {
		return this.scopeReferenceId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public String getCreatedBy() {
		return this.createdBy;
	}

}
