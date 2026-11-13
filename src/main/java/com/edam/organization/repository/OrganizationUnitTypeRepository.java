package com.edam.organization.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.organization.model.OrganizationUnitType;

import org.springframework.data.jpa.repository.JpaRepository;

/** The unit-type vocabulary (Module A). */
public interface OrganizationUnitTypeRepository extends JpaRepository<OrganizationUnitType, UUID> {

	Optional<OrganizationUnitType> findByCode(String code);

	List<OrganizationUnitType> findAllByOrderByNameAsc();

}
