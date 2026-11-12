package com.edam.iam;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Permission catalogue access. Platform-level: not institution-scoped by design, because the
 * vocabulary of capabilities is the same for every tenant.
 */
public interface PermissionDefinitionRepository extends JpaRepository<PermissionDefinition, Long> {

	List<PermissionDefinition> findAllByOrderByModuleAscActionAsc();

	Optional<PermissionDefinition> findByCode(String code);

	List<PermissionDefinition> findByCodeIn(java.util.Collection<String> codes);

}
