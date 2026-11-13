package com.edam.workflow.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.workflow.model.WorkflowTask;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Individual approval tasks: one per approver per step.
 */
public interface TasksRepository extends JpaRepository<WorkflowTask, UUID>, JpaSpecificationExecutor<WorkflowTask> {

	List<WorkflowTask> findByWorkflowInstanceIdAndStatus(UUID instanceId, WorkflowTask.TaskStatus status);

	List<WorkflowTask> findByWorkflowInstanceIdOrderByAssignedAtAsc(UUID instanceId);

	/**
	 * Oldest-first keyset fetch for the timeline. Returns at most {@code fetchLimit} tasks for
	 * the instance whose {@code assigned_at} is not after {@code boundedAt} and that are
	 * strictly after the cursor key {@code (afterTs, afterId)}. When {@code afterTs} is null,
	 * returns the earliest tasks. Uses a native query with explicit casts so Postgres can infer
	 * parameter types even when the cursor values are null.
	 */
	@Query(value = """
			select t.* from workflow_tasks t
			where t.workflow_instance_id = :instanceId
			  and t.assigned_at <= :boundedAt
			  and (   cast(:afterTs as timestamptz) is null
			       or t.assigned_at > cast(:afterTs as timestamptz)
			       or (t.assigned_at = cast(:afterTs as timestamptz)
			           and (cast(:afterId as uuid) is null or t.id > cast(:afterId as uuid))))
			order by t.assigned_at asc, t.id asc
			limit :fetchLimit
			""", nativeQuery = true)
	List<WorkflowTask> findForInstanceAfter(@Param("instanceId") UUID instanceId,
			@Param("boundedAt") Instant boundedAt,
			@Param("afterTs") Instant afterTs,
			@Param("afterId") UUID afterId,
			@Param("fetchLimit") int fetchLimit);

	long countByAssignedUserIdAndStatus(UUID userId, WorkflowTask.TaskStatus status);

	/** Open tasks addressed to the caller directly, newest first. */
	Page<WorkflowTask> findByAssignedUserIdAndStatus(UUID userId, WorkflowTask.TaskStatus status, Pageable pageable);

	default Page<WorkflowTask> findInbox(UUID userId, Collection<UUID> roleIds, Pageable pageable) {
		java.util.Set<UUID> roles = java.util.Set.copyOf(roleIds);
		Specification<WorkflowTask> specification = (root, query, cb) -> {
			var open = cb.equal(root.get("status"), WorkflowTask.TaskStatus.PENDING);
			var mine = cb.equal(root.get("assignedUserId"), userId);
			var byRole = root.get("assignedRoleId").in(roles);
			query.orderBy(cb.desc(root.get("assignedAt")));
			return cb.and(open, cb.or(mine, byRole));
		};
		return findAll(specification, pageable);
	}

	default long countApproved(UUID instanceId, UUID stepId) {
		return findByWorkflowInstanceIdAndStatus(instanceId, WorkflowTask.TaskStatus.APPROVED)
			.stream()
			.filter(task -> task.getWorkflowStepId().equals(stepId))
			.count();
	}

	default List<WorkflowTask> findOpenForStep(UUID instanceId, UUID stepId) {
		return findByWorkflowInstanceIdAndStatus(instanceId, WorkflowTask.TaskStatus.PENDING)
			.stream()
			.filter(task -> task.getWorkflowStepId().equals(stepId))
			.toList();
	}

	default Optional<WorkflowTask> findOpenTask(UUID taskId, UUID instanceId) {
		return findById(taskId)
			.filter(task -> task.getWorkflowInstanceId().equals(instanceId)
				&& task.getStatus() == WorkflowTask.TaskStatus.PENDING);
	}

	default List<WorkflowTask> findOpenForCaller(UUID instanceId, UUID userId, Collection<UUID> roleIds) {
		java.util.Set<UUID> roles = java.util.Set.copyOf(roleIds);
		return findByWorkflowInstanceIdAndStatus(instanceId, WorkflowTask.TaskStatus.PENDING)
			.stream()
			.filter(task -> userId.equals(task.getAssignedUserId())
				|| (task.getAssignedRoleId() != null && roles.contains(task.getAssignedRoleId())))
			.toList();
	}

}
