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
	 * whose task belongs to {@code instanceId} (sub-selected through {@code workflow_tasks} —
	 * no in-memory task-id materialization), with {@code action_at} not after {@code boundedAt}
	 * and strictly after the cursor key {@code (afterTs, afterMsb, afterLsb)} (signed-long
	 * halves matching {@link java.util.UUID#compareTo}). Native query with explicit casts keeps
	 * Postgres from inferring {@code unknown} for nullable parameters.
	 */
	@Query(value = """
			select a.* from workflow_actions a
			where a.workflow_task_id in (
				select t.id from workflow_tasks t where t.workflow_instance_id = :instanceId
			)
			  and a.action_at <= :boundedAt
			  and (   cast(:afterTs as timestamptz) is null
			       or a.action_at > cast(:afterTs as timestamptz)
			       or (a.action_at = cast(:afterTs as timestamptz)
			           and (:afterMsb is null
			                or (   (('x'||substr(replace(a.id::text,'-',''),1,16))::bit(64))::bigint > :afterMsb
			                    or ((('x'||substr(replace(a.id::text,'-',''),1,16))::bit(64))::bigint = :afterMsb
			                        and (('x'||substr(replace(a.id::text,'-',''),17,16))::bit(64))::bigint > :afterLsb)))))
			order by a.action_at asc,
			         (('x'||substr(replace(a.id::text,'-',''),1,16))::bit(64))::bigint asc,
			         (('x'||substr(replace(a.id::text,'-',''),17,16))::bit(64))::bigint asc
			limit :fetchLimit
			""", nativeQuery = true)
	List<WorkflowAction> findForInstanceAfter(@Param("instanceId") UUID instanceId,
			@Param("boundedAt") Instant boundedAt,
			@Param("afterTs") Instant afterTs,
			@Param("afterMsb") Long afterMsb,
			@Param("afterLsb") Long afterLsb,
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
