package com.edam.workflow.repository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.edam.workflow.model.WorkflowAction;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The permanent record of every workflow action taken.
 *
 * <p>Append-only. An action is never updated or deleted, because it is the evidence of who decided what and
 * when, and the approval history of a payment is exactly the thing an auditor will ask for.
 */
public interface ActionsRepository extends JpaRepository<WorkflowAction, UUID> {

	List<WorkflowAction> findByWorkflowTaskIdOrderByActionAtAsc(UUID taskId);

	List<WorkflowAction> findByWorkflowTaskIdInOrderByActionAtAsc(List<UUID> taskIds);

	/**
	 * The full action history of an instance, oldest first.
	 *
	 * <p>Callers pass the instance's task ids, which they already hold. Filtering on the ids keeps this a
	 * scoped read: deriving the instance from the action would require either a join the entity does not map
	 * or a scan of every action ever recorded. An empty instance simply has no actions.
	 */
	default List<WorkflowAction> findForInstance(List<UUID> taskIds) {
		if (taskIds.isEmpty()) {
			return List.of();
		}
		return findByWorkflowTaskIdInOrderByActionAtAsc(taskIds).stream()
			.sorted(Comparator.comparing(WorkflowAction::getActionAt))
			.toList();
	}

}
