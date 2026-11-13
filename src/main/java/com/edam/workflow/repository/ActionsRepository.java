package com.edam.workflow.repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.edam.workflow.model.WorkflowAction;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The permanent record of every workflow action taken.
 *
 * <p>Append-only. An action is never updated or deleted, because it is the evidence of who decided what and
 * when, and the approval history of a payment is exactly the thing an auditor will ask for.
 */
public interface ActionsRepository extends JpaRepository<WorkflowAction, UUID> {

	long count();

	List<WorkflowAction> findByWorkflowTaskIdOrderByActionAtAsc(UUID taskId);

	List<WorkflowAction> findByWorkflowTaskIdInOrderByActionAtAsc(List<UUID> taskIds);

	/**
	 * Oldest-first keyset fetch for the timeline: at most {@code fetchLimit} actions whose task
	 * is in {@code taskIds}, strictly after {@code (afterTs, afterId)} and not after
	 * {@code boundedAt}. Ordering is {@code (action_at ASC, id ASC)} for deterministic
	 * tie-breaking.
	 */
	@Query("""
			select a from WorkflowAction a
			where a.workflowTaskId in :taskIds
			  and a.actionAt <= :boundedAt
			  and (   :afterTs is null
			       or a.actionAt > :afterTs
			       or (a.actionAt = :afterTs and (:afterId is null or a.id > :afterId)))
			order by a.actionAt asc, a.id asc
			""")
	List<WorkflowAction> findForTasksAfter(@Param("taskIds") List<UUID> taskIds,
			@Param("boundedAt") Instant boundedAt,
			@Param("afterTs") Instant afterTs,
			@Param("afterId") UUID afterId,
			Pageable pageable);

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
			.sorted(Comparator.comparing(WorkflowAction::getActionAt).thenComparing(WorkflowAction::getId))
			.toList();
	}

}
