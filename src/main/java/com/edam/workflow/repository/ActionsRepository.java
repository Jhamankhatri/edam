package com.edam.workflow.repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.edam.workflow.model.WorkflowAction;

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
	 * Oldest-first keyset fetch for the timeline. Returns at most {@code fetchLimit} actions
	 * whose task is in {@code taskIds}, with {@code action_at} not after {@code boundedAt} and
	 * strictly after the cursor key {@code (afterTs, afterId)}. Native query with explicit
	 * casts keeps Postgres from inferring {@code unknown} for nullable parameters.
	 */
	@Query(value = """
			select a.* from workflow_actions a
			where a.workflow_task_id = any (:taskIds)
			  and a.action_at <= :boundedAt
			  and (   cast(:afterTs as timestamptz) is null
			       or a.action_at > cast(:afterTs as timestamptz)
			       or (a.action_at = cast(:afterTs as timestamptz)
			           and (cast(:afterId as uuid) is null or a.id > cast(:afterId as uuid))))
			order by a.action_at asc, a.id asc
			limit :fetchLimit
			""", nativeQuery = true)
	List<WorkflowAction> findForTasksAfter(@Param("taskIds") UUID[] taskIds,
			@Param("boundedAt") Instant boundedAt,
			@Param("afterTs") Instant afterTs,
			@Param("afterId") UUID afterId,
			@Param("fetchLimit") int fetchLimit);

	/**
	 * The full action history of an instance, oldest first.
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
