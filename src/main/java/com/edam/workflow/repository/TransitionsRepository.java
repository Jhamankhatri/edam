package com.edam.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.workflow.model.WorkflowTransition;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The declared transitions of a workflow revision.
 *
 * <p>Read as a whole per revision: the engine asks "what may happen from this state", and a revision has a
 * few dozen transitions at most. Fetching them per action would turn one routing decision into several
 * queries at the moment an approver clicks.
 */
public interface TransitionsRepository extends JpaRepository<WorkflowTransition, UUID> {

	List<WorkflowTransition> findByWorkflowVersionId(UUID versionId);

	/** The declared transition for one action leaving one state, if the revision permits it. */
	default Optional<WorkflowTransition> find(UUID versionId, UUID fromStateId, WorkflowTransition.ActionCode action) {
		return findByWorkflowVersionId(versionId).stream()
			.filter(transition -> transition.matches(fromStateId, action))
			.findFirst();
	}

	/** Every action the revision permits from the given state. Used by the case-file view. */
	default List<WorkflowTransition.ActionCode> actionsFrom(UUID versionId, UUID fromStateId) {
		return findByWorkflowVersionId(versionId).stream()
			.filter(transition -> transition.getFromStateId().equals(fromStateId))
			.map(WorkflowTransition::getActionCode)
			.toList();
	}

}
