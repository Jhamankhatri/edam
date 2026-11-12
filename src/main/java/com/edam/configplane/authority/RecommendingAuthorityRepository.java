package com.edam.configplane.authority;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Recommending-authority persistence, tenant-scoped. */
public interface RecommendingAuthorityRepository extends JpaRepository<RecommendingAuthority, Long> {

	List<RecommendingAuthority> findByInstitutionIdOrderByTitleAsc(Long institutionId);

	List<RecommendingAuthority> findByInstitutionIdAndActiveTrueOrderByTitleAsc(Long institutionId);

	List<RecommendingAuthority> findByInstitutionIdAndDepartmentCodeAndActiveTrue(Long institutionId,
			String departmentCode);

	Optional<RecommendingAuthority> findByIdAndInstitutionId(Long id, Long institutionId);

	boolean existsByInstitutionIdAndTitleIgnoreCaseAndActiveTrue(Long institutionId, String title);

}
