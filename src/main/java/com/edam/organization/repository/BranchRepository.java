package com.edam.organization.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.organization.model.Branch;

import org.springframework.data.jpa.repository.JpaRepository;

/** Branches (Module A). */
public interface BranchRepository extends JpaRepository<Branch, UUID> {

	Optional<Branch> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByOrganizationUnitId(UUID organizationUnitId);

	List<Branch> findByActiveTrueOrderByNameAsc();

}
