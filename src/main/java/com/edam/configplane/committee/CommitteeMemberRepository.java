package com.edam.configplane.committee;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Committee member pool persistence, tenant-scoped. */
public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, Long> {

	List<CommitteeMember> findByInstitutionIdOrderByDisplayNameAsc(Long institutionId);

	List<CommitteeMember> findByInstitutionIdAndActiveTrueOrderByDisplayNameAsc(Long institutionId);

	Optional<CommitteeMember> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<CommitteeMember> findByInstitutionIdAndEmployeeId(Long institutionId, String employeeId);

	boolean existsByInstitutionIdAndEmployeeIdAndActiveTrue(Long institutionId, String employeeId);

	boolean existsByInstitutionIdAndDesignationAndActiveTrue(Long institutionId, CommitteeDesignation designation);

}
