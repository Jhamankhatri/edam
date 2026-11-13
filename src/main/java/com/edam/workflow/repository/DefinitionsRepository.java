package com.edam.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.workflow.model.WorkflowAction;
import com.edam.workflow.model.WorkflowDefinition;
import com.edam.workflow.model.WorkflowInstance;
import com.edam.workflow.model.WorkflowState;
import com.edam.workflow.model.WorkflowStep;
import com.edam.workflow.model.WorkflowTask;
import com.edam.workflow.model.WorkflowVersion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Workflow definitions: the named, request-type-scoped entry point to a workflow.
 *
 * <p>A definition is stable across revisions. The revision that governs a request is pinned on the
 * instance at submission, so editing a definition never alters an in-flight request.
 */
public interface DefinitionsRepository extends JpaRepository<WorkflowDefinition, UUID> {


	Optional<WorkflowDefinition> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	List<WorkflowDefinition> findByActiveTrueOrderByNameAsc();

	List<WorkflowDefinition> findAllByOrderByNameAsc();

	Optional<WorkflowDefinition> findFirstByRequestTypeIdAndActiveTrue(UUID requestTypeId);


}
