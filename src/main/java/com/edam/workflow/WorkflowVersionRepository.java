package com.edam.workflow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Workflow version persistence, tenant-scoped. */
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, Long> {

	List<WorkflowVersion> findByInstitutionIdAndDefinitionIdOrderByVersionNumberDesc(Long institutionId,
			Long definitionId);

	Optional<WorkflowVersion> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<WorkflowVersion> findFirstByInstitutionIdAndDefinitionIdAndStatusOrderByVersionNumberDesc(
			Long institutionId, Long definitionId, WorkflowVersionStatus status);

	@Query("select coalesce(max(v.versionNumber), 0) from WorkflowVersion v where v.definition.id = :definitionId")
	int maxVersionNumber(@Param("definitionId") Long definitionId);

	@Query("select (count(v) > 0) from WorkflowVersion v where v.institutionId = :institutionId "
			+ "and v.definition.id = :definitionId and v.status = 'PUBLISHED'")
	boolean existsPublished(@Param("institutionId") Long institutionId, @Param("definitionId") Long definitionId);

}
