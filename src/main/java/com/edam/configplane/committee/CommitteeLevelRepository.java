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

	/** True when the member belongs to any level, active or not. */
	@org.springframework.data.jpa.repository.Query("select (count(l) > 0) from CommitteeLevel l join l.members m "
			+ "where l.institutionId = :institutionId and m.id = :memberId")
	boolean existsByInstitutionIdAndMembersId(@org.springframework.data.repository.query.Param("institutionId") Long institutionId,
			@org.springframework.data.repository.query.Param("memberId") Long memberId);

	/** True when the member belongs to an ACTIVE level. */
	@org.springframework.data.jpa.repository.Query("select (count(l) > 0) from CommitteeLevel l join l.members m "
			+ "where l.institutionId = :institutionId and l.active = true and m.id = :memberId")
	boolean existsByInstitutionIdAndMembersIdAndActiveTrue(
			@org.springframework.data.repository.query.Param("institutionId") Long institutionId,
			@org.springframework.data.repository.query.Param("memberId") Long memberId);

}
