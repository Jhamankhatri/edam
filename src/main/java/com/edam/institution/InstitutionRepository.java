package com.edam.institution;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for institutions.
 *
 * <p>Package-private visibility would be ideal but Spring Data proxies require public
 * interfaces; module boundaries are therefore enforced by policy (documented in
 * PACKAGE_STRUCTURE.md) and by keeping this type inside the institution module. No other
 * module may inject it: services exchange institution data through the module's public
 * service.
 */
public interface InstitutionRepository extends JpaRepository<Institution, Long> {

	Optional<Institution> findByCode(String code);

	boolean existsByCode(String code);

}
