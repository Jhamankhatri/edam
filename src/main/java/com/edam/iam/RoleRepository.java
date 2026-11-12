package com.edam.iam;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Role persistence, tenant-scoped. */
public interface RoleRepository extends JpaRepository<Role, Long> {

	List<Role> findByInstitutionIdOrderByCodeAsc(Long institutionId);

	List<Role> findByInstitutionIdAndActiveTrueOrderByCodeAsc(Long institutionId);

	Optional<Role> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<Role> findByInstitutionIdAndCode(Long institutionId, String code);

	boolean existsByInstitutionIdAndCodeIgnoreCaseAndActiveTrue(Long institutionId, String code);

}
