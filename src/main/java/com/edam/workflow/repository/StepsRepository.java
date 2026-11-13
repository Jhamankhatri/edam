package com.edam.workflow.repository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.edam.workflow.model.WorkflowStep;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Ordered approval steps of one workflow revision, with their approvers.
 *
 * <p>Steps are the unit of quorum. A step's approvers are always needed together with it, because starting a
 * step requires knowing who may act.
 *
 * <h2>Why the approvers are forced here rather than fetched lazily</h2>
 * Starting a workflow opens a task per approver per step. With lazy loading that is one query per approver at
 * exactly the moment a request is submitted — the worst possible moment for an N+1. The entity maps the
 * collection eagerly for that reason; these finders load the steps and let the mapping do its job.
 */
public interface StepsRepository extends JpaRepository<WorkflowStep, UUID> {

	List<WorkflowStep> findByWorkflowVersionIdOrderByStepOrderAsc(UUID versionId);

	/** Every step of a revision, in order, for starting an instance. */
	default List<WorkflowStep> findWithApprovers(UUID versionId) {
		return findByWorkflowVersionIdOrderByStepOrderAsc(versionId);
	}

	/** Steps after {@code afterOrder}, in order, for advancing to the next step. */
	default List<WorkflowStep> findAfterOrder(UUID versionId, short afterOrder) {
		return findByWorkflowVersionIdOrderByStepOrderAsc(versionId).stream()
			.filter(step -> step.getStepOrder() > afterOrder)
			.sorted(Comparator.comparingInt(WorkflowStep::getStepOrder))
			.toList();
	}

}
