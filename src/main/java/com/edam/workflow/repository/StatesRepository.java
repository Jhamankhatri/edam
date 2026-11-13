package com.edam.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.workflow.model.WorkflowState;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * States of one workflow revision.
 *
 * <p>A state is identified by its type, which is what the engine transitions on; the label is presentation.
 * Exactly one state per version is initial, enforced by a partial unique index.
 */
public interface StatesRepository extends JpaRepository<WorkflowState, UUID> {

	List<WorkflowState> findByWorkflowVersionId(UUID versionId);

	/** The one initial state of a revision. */
	default Optional<WorkflowState> findInitial(UUID versionId) {
		return findByWorkflowVersionId(versionId).stream().filter(WorkflowState::isInitial).findFirst();
	}

	/** The first state of a revision whose type matches, e.g. the one APPROVED state. */
	default Optional<WorkflowState> findByType(UUID versionId, WorkflowState.StateType stateType) {
		return findByWorkflowVersionId(versionId).stream()
			.filter(state -> state.getStateType() == stateType)
			.findFirst();
	}

}
