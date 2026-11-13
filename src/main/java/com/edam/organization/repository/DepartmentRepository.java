package com.edam.organization.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.organization.model.Department;

import org.springframework.data.jpa.repository.JpaRepository;

/** Departments (Module A). */
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

	Optional<Department> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByOrganizationUnitId(UUID organizationUnitId);

	List<Department> findByActiveTrueOrderByNameAsc();

	List<Department> findByHeadUserId(UUID headUserId);

}
