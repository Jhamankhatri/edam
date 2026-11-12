package com.edam.common.tenant;

import java.util.Optional;

import com.edam.common.security.InstitutionContext;

import org.springframework.stereotype.Component;

/**
 * The single chokepoint for tenant checks on loaded entities.
 *
 * <h2>Why this class exists</h2>
 * The composite indexes make tenant-scoped queries natural, but a repository finder by id alone
 * is one forgotten argument away. Every service that loads an institution-owned entity by id goes
 * through this guard, which performs the ownership check and throws the same
 * {@code NOT_FOUND} a missing row produces — so a cross-tenant probe cannot distinguish
 * "not yours" from "does not exist" (BRD §23.2, AC-02).
 *
 * <p>This is deliberately <em>not</em> a Hibernate {@code @Filter}: filters are disabled by
 * default, bypassed by native queries, and silently switchable off. An explicit call is
 * reviewable, testable and always on.
 */
@Component
public class TenantGuard {

	private final InstitutionContext institutionContext;

	public TenantGuard(InstitutionContext institutionContext) {
		this.institutionContext = institutionContext;
	}

	/** The caller's institution id; throws if no tenant is resolved. */
	public Long requireInstitutionId() {
		return this.institutionContext.requireInstitutionId();
	}

	/**
	 * Assert that an entity belongs to the caller's institution.
	 *
	 * @return the entity, for fluent use
	 * @throws com.edam.common.exception.ResourceNotFoundException if it does not
	 */
	public <T extends InstitutionOwnedEntity> T check(T entity) {
		entity.requireInstitution(requireInstitutionId());
		return entity;
	}

	/** Assert ownership of a possibly-absent entity, preserving the 404 for the absent case. */
	public <T extends InstitutionOwnedEntity> T check(Optional<T> entity, String resourceType, Object id) {
		return check(entity.orElseThrow(
				() -> new com.edam.common.exception.ResourceNotFoundException(resourceType, id)));
	}

}
