package com.edam.workflow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Workflow definition persistence, tenant-scoped. */
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

	List<WorkflowDefinition> findByInstitutionIdOrderByCodeAsc(Long institutionId);

	Optional<WorkflowDefinition> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<WorkflowDefinition> findByInstitutionIdAndCode(Long institutionId, String code);

	boolean existsByInstitutionIdAndCode(Long institutionId, String code);

}
