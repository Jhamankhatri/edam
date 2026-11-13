package com.edam.organization.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.organization.model.OrganizationUnit;

import org.springframework.data.jpa.repository.JpaRepository;

/** Organization hierarchy (Module A). */
public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, UUID> {

	boolean existsByCodeIgnoreCase(String code);

	Optional<OrganizationUnit> findByCodeIgnoreCase(String code);

	List<OrganizationUnit> findByParentUnitIdOrderByNameAsc(UUID parentUnitId);

	List<OrganizationUnit> findByUnitTypeIdAndActiveTrue(UUID unitTypeId);

	List<OrganizationUnit> findByActiveTrueOrderByNameAsc();

}
