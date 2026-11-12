package com.edam.common.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Minimal audit and concurrency superclass for EDAM's mutable entities.
 *
 * <h2>Why a base class here (and nowhere else)</h2>
 * Audit stamps and optimistic locking are cross-cutting persistence concerns that are
 * identical for every table; repeating six fields on every entity invites drift and
 * forgotten columns. This is the <em>only</em> inheritance-based mechanism in the
 * foundation; business behaviour lives in services, not in a generic DAO hierarchy.
 *
 * <h2>Deliberate exclusions</h2>
 * <ul>
 * <li>No soft-delete flag: the BRD requires delete guards and audit history, not a global
 * "deleted" column. Adding one later is a schema decision, not a framework default.</li>
 * <li>No institution column here: tenant isolation is an unresolved decision (OD-20) and
 * a half-implemented discriminator is worse than none. Tenant-owned entities will declare
 * their own tenant key once the isolation model is approved.</li>
 * <li>No domain equality/hashCode: JPA entities are compared by identity.</li>
 * </ul>
 *
 * <p>Timestamps are captured in UTC; presentation-time zone is an institution
 * configuration concern.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@CreatedBy
	@Column(name = "created_by", nullable = false, updatable = false, length = 64)
	private String createdBy;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by", nullable = false, length = 64)
	private String updatedBy;

	/**
	 * Optimistic locking. Required by the BRD's target proposals for approval actions:
	 * two approvers acting on the same task must not both succeed.
	 */
	@Version
	@Column(name = "version", nullable = false)
	private long version;

	public Long getId() {
		return this.id;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public String getCreatedBy() {
		return this.createdBy;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public String getUpdatedBy() {
		return this.updatedBy;
	}

	public long getVersion() {
		return this.version;
	}

}
