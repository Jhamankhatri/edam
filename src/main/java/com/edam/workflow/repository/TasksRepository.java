package com.edam.workflow.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.workflow.model.WorkflowTask;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Individual approval tasks: one per approver per step.
 *
 * <p>Tasks are the only place approval authority is recorded. A caller may act only if an open task names
 * them, directly or through a role they hold; holding a permission is not sufficient.
 *
 * <h2>Why the queries are derived and filtered in Java</h2>
 * Two of these need "the caller's tasks", which means an {@code OR} over a user id and a set of role ids.
 * That is expressible in JPQL, but a typed repository method keeps the predicate in one place and lets the
 * status comparisons be ordinary Java rather than string literals inside a query. The collections involved
 * are the tasks of a single request or a single approver, so they are small by construction; a query that
 * scanned every task in the institution would be the wrong shape here regardless.
 */
public interface TasksRepository extends JpaRepository<WorkflowTask, UUID> {

	List<WorkflowTask> findByWorkflowInstanceIdAndStatus(UUID instanceId, WorkflowTask.TaskStatus status);

	List<WorkflowTask> findByWorkflowInstanceIdOrderByAssignedAtAsc(UUID instanceId);

	long countByAssignedUserIdAndStatus(UUID userId, WorkflowTask.TaskStatus status);

	/** Open tasks addressed to the caller directly, newest first. */
	Page<WorkflowTask> findByAssignedUserIdAndStatus(UUID userId, WorkflowTask.TaskStatus status, Pageable pageable);

	/**
	 * The caller's approval inbox: open tasks addressed to them directly or to any role they hold.
	 *
	 * <p>Doing the {@code OR} here rather than in the database is deliberate. A role-addressed task is visible
	 * to every current holder of that role, and "current" is a decision the identity layer owns; letting the
	 * caller's role set be resolved once and passed in keeps that rule in one place.
	 */
	default Page<WorkflowTask> findInbox(UUID userId, Collection<UUID> roleIds, Pageable pageable) {
		// The set is materialised once so the predicate is a constant-time membership test per row.
		java.util.Set<UUID> roles = java.util.Set.copyOf(roleIds);
		return findAll((root, query, cb) -> {
			var open = cb.equal(root.get("status"), WorkflowTask.TaskStatus.PENDING);
			var mine = cb.equal(root.get("assignedUserId"), userId);
			var byRole = root.get("assignedRoleId").in(roles);
			query.orderBy(cb.desc(root.get("assignedAt")));
			return cb.and(open, cb.or(mine, byRole));
		}, pageable);
	}

	/** How many approvals a step of an instance has already recorded. Quorum is decided on this number. */
	default long countApproved(UUID instanceId, UUID stepId) {
		return findByWorkflowInstanceIdAndStatus(instanceId, WorkflowTask.TaskStatus.APPROVED)
			.stream()
			.filter(task -> task.getWorkflowStepId().equals(stepId))
			.count();
	}

	/** The still-open tasks of one step of one instance. */
	default List<WorkflowTask> findOpenForStep(UUID instanceId, UUID stepId) {
		return findByWorkflowInstanceIdAndStatus(instanceId, WorkflowTask.TaskStatus.PENDING)
			.stream()
			.filter(task -> task.getWorkflowStepId().equals(stepId))
			.toList();
	}

	/** An open task by id, scoped to its instance so a task id from one request cannot act on another. */
	default Optional<WorkflowTask> findOpenTask(UUID taskId, UUID instanceId) {
		return findById(taskId)
			.filter(task -> task.getWorkflowInstanceId().equals(instanceId)
					&& task.getStatus() == WorkflowTask.TaskStatus.PENDING);
	}

	/** Open tasks of an instance that name the caller, directly or through a role they hold. */
	default List<WorkflowTask> findOpenForCaller(UUID instanceId, UUID userId, Collection<UUID> roleIds) {
		java.util.Set<UUID> roles = java.util.Set.copyOf(roleIds);
		return findByWorkflowInstanceIdAndStatus(instanceId, WorkflowTask.TaskStatus.PENDING)
			.stream()
			.filter(task -> userId.equals(task.getAssignedUserId())
					|| (task.getAssignedRoleId() != null && roles.contains(task.getAssignedRoleId())))
			.toList();
	}

}
