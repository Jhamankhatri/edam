package com.edam.configplane.committee;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Committee level persistence, tenant-scoped. */
public interface CommitteeLevelRepository extends JpaRepository<CommitteeLevel, Long> {

	List<CommitteeLevel> findByInstitutionIdOrderByAmountFromAsc(Long institutionId);

	List<CommitteeLevel> findByInstitutionIdAndActiveTrueOrderByAmountFromAsc(Long institutionId);

	Optional<CommitteeLevel> findByIdAndInstitutionId(Long id, Long institutionId);

	boolean existsByInstitutionIdAndNameIgnoreCaseAndActiveTrue(Long institutionId, String name);

}
