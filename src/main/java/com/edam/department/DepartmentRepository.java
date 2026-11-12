package com.edam.department;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Department persistence, tenant-scoped. */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

	List<Department> findByInstitutionIdOrderByCodeAsc(Long institutionId);

	List<Department> findByInstitutionIdAndActiveTrueOrderByCodeAsc(Long institutionId);

	Optional<Department> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<Department> findByInstitutionIdAndCode(Long institutionId, String code);

	boolean existsByInstitutionIdAndCode(Long institutionId, String code);

}
