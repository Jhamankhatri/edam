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
 * Running and completed workflow instances, one per expense request.
 *
 * <p>The unique constraint on the expense request is what makes double submission through a retried or
 * concurrent request impossible, rather than merely unlikely.
 */
public interface InstancesRepository extends JpaRepository<WorkflowInstance, UUID> {


	Optional<WorkflowInstance> findByExpenseRequestId(UUID expenseRequestId);

	boolean existsByExpenseRequestId(UUID expenseRequestId);

	List<WorkflowInstance> findByWorkflowVersionId(UUID versionId);

	long countByWorkflowVersionIdAndCompletedAtIsNull(UUID versionId);


}
