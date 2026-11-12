package com.edam.common.tenant;

import com.edam.common.audit.AuditableEntity;
import com.edam.common.exception.ResourceNotFoundException;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Base class for every <strong>institution-owned</strong> entity.
 *
 * <h2>Why a discriminator column (provisional decision P-1)</h2>
 * BRD OD-23 leaves the tenant isolation model (shared schema / schema-per-tenant /
 * database-per-tenant) unapproved. EDAM therefore implements the only strategy that is both
 * buildable today and migratable later: a shared schema with a mandatory
 * {@code institution_id} discriminator.
 *
 * <p>The discriminator is <em>not</em> a security control on its own. Isolation is enforced in
 * three layers, and every layer is tested:
 * <ol>
 *   <li><strong>Population</strong> — the service layer stamps the institution from
 *       {@link com.edam.common.security.InstitutionContext}, never from request input.</li>
 *   <li><strong>Query</strong> — repositories must constrain by institution. {@code TenantGuard}
 *       provides a single chokepoint so a forgotten {@code where} clause is a test failure
 *       rather than a data breach.</li>
 *   <li><strong>Database</strong> — foreign keys to {@code institution} and composite indexes
 *       lead with the discriminator, so a cross-tenant join is not expressible without an
 *       explicit, reviewable mistake.</li>
 * </ol>
 *
 * <p>A client-supplied institution id is <em>never</em> trusted as authorization (BRD §8): it may
 * appear in a path to *select* a tenant the caller is a member of, and it is compared against
 * {@link com.edam.common.security.EdamPrincipal#belongsTo(String)} before use.
 *
 * <p>Note the deliberate absence of a {@code @Filter}: Hibernate filters are disabled by default
 * and are silently bypassed by native queries, which makes them a poor primary control. Explicit
 * constraints plus tests are verifiable; a filter that can be switched off is not.
 */
@MappedSuperclass
public abstract class InstitutionOwnedEntity extends AuditableEntity {

	@Column(name = "institution_id", nullable = false, updatable = false)
	private Long institutionId;

	public Long getInstitutionId() {
		return this.institutionId;
	}

	/**
	 * Stamp the owning institution. Called by the service layer on creation only; the column is
	 * {@code updatable = false} so an entity can never be moved between tenants.
	 */
	public void assignInstitution(Long institutionId) {
		if (institutionId == null) {
			throw new IllegalArgumentException("institutionId is required for an institution-owned entity");
		}
		if (this.institutionId != null && !this.institutionId.equals(institutionId)) {
			throw new IllegalStateException("An institution-owned entity cannot be reassigned to another institution");
		}
		this.institutionId = institutionId;
	}

	/**
	 * Assert that this entity belongs to the caller's institution. Throws the same exception a
	 * missing record would, so a cross-tenant probe cannot distinguish "not yours" from
	 * "does not exist" (BRD §23.2: access-denied responses must not reveal existence).
	 */
	public void requireInstitution(Long expectedInstitutionId) {
		if (expectedInstitutionId == null || !expectedInstitutionId.equals(this.institutionId)) {
			throw new ResourceNotFoundException(getClass().getSimpleName(),
					getId() == null ? "unknown" : getId());
		}
	}

}
