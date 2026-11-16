package com.edam.communication.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.edam.communication.model.RecipientGroup;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.rolemanagement.repository.UserRoleRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;
import com.edam.workflow.model.WorkflowTask;
import com.edam.workflow.repository.InstancesRepository;
import com.edam.workflow.repository.TasksRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the set of active user ids that should receive a given event.
 *
 * <p>Resolution is driven entirely by the configured {@link RecipientGroup} enum values. Only
 * groups listed in {@link RecipientGroup} are supported; anything else is rejected at event-config
 * update time, so there is no arbitrary-SQL/script surface here.
 *
 * <p>Role-assigned tasks ({@code assignedRoleId} populated, {@code assignedUserId} null) are
 * expanded through {@link UserRoleRepository#findActiveHolderIds} using the same expiry predicate
 * the authorization layer uses, so a deactivated or expired grant never receives a notification.
 */
@Service
public class NotificationRecipientResolver {

	private static final Logger log = LoggerFactory.getLogger(NotificationRecipientResolver.class);

	private final UserRepository users;
	private final UserRoleRepository userRoles;
	private final TasksRepository tasks;
	private final InstancesRepository instances;
	private final ExpenseRequestRepository requests;

	public NotificationRecipientResolver(UserRepository users, UserRoleRepository userRoles,
			TasksRepository tasks, InstancesRepository instances, ExpenseRequestRepository requests) {
		this.users = users;
		this.userRoles = userRoles;
		this.tasks = tasks;
		this.instances = instances;
		this.requests = requests;
	}

	/**
	 * Resolve all active recipient user ids for the given (event, refRequest) configured groups.
	 * Duplicates are removed; inactive users are filtered out.
	 */
	@Transactional(readOnly = true)
	public Set<UUID> resolve(Set<RecipientGroup> groups, UUID refRequestId) {
		return resolve(groups, refRequestId, null);
	}

	/**
	 * Resolve recipients for an event.
	 * @param overrideForwardedRecipient when non-null, used for the FORWARDED_RECIPIENT group instead
	 *        of a lookup (so the just-forwarded user is notified on the same transaction even before
	 *        they appear in the assigned-task list).
	 */
	@Transactional(readOnly = true)
	public Set<UUID> resolve(Set<RecipientGroup> groups, UUID refRequestId, UUID overrideForwardedRecipient) {
		Set<UUID> ids = new LinkedHashSet<>();
		if (groups == null) return ids;

		ExpenseRequest request = refRequestId == null ? null
				: requests.findById(refRequestId).orElse(null);

		for (RecipientGroup g : groups) {
			switch (g) {
				case REQUESTER -> {
					if (request != null && request.getRequestedBy() != null) ids.add(request.getRequestedBy());
				}
				case ASSIGNED_APPROVER -> {
					if (request != null) ids.addAll(currentAssignedApprovers(request.getId()));
				}
				case WORKFLOW_PARTICIPANTS -> {
					if (request != null) ids.addAll(workflowParticipants(request.getId()));
				}
				case FORWARDED_RECIPIENT -> {
					if (overrideForwardedRecipient != null) ids.add(overrideForwardedRecipient);
				}
				case DEPARTMENT_HEAD, DESIGNATED_ADMINISTRATORS -> {
					// Reserved groups for later phases; deliberately not resolved here so that
					// an admin cannot accidentally select a group that silently reaches no one.
					log.info("notification.group_unresolved group={} request={}", g, refRequestId);
				}
			}
		}

		// Filter to only active users (defense against stale IDs).
		if (ids.isEmpty()) return ids;
		List<User> active = users.findAllById(ids);
		return active.stream()
				.filter(User::isActive)
				.map(User::getId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** All active users currently holding a PENDING approval task for the given request. */
	private Set<UUID> currentAssignedApprovers(UUID requestId) {
		Set<UUID> ids = new LinkedHashSet<>();
		var instance = instances.findByExpenseRequestId(requestId).orElse(null);
		if (instance == null) return ids;
		List<WorkflowTask> pending = tasks.findByWorkflowInstanceIdAndStatus(instance.getId(),
				com.edam.workflow.model.WorkflowTask.TaskStatus.PENDING);
		for (WorkflowTask t : pending) {
			if (t.getAssignedUserId() != null) {
				ids.add(t.getAssignedUserId());
			} else if (t.getAssignedRoleId() != null) {
				ids.addAll(userRoles.findActiveHolderIds(t.getAssignedRoleId()));
			}
		}
		return ids;
	}

	/** Requester + all users who acted (APPROVED) or are currently assigned. */
	private Set<UUID> workflowParticipants(UUID requestId) {
		Set<UUID> ids = new LinkedHashSet<>();
		var instance = instances.findByExpenseRequestId(requestId).orElse(null);
		if (instance == null) return ids;
		// Requester
		var req = requests.findById(requestId).orElse(null);
		if (req != null && req.getRequestedBy() != null) ids.add(req.getRequestedBy());
		// All tasks (pending + decided): assigned users and actors (ActedBy stored on tasks?)
		List<WorkflowTask> all = tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId());
		for (WorkflowTask t : all) {
			if (t.getAssignedUserId() != null) ids.add(t.getAssignedUserId());
			else if (t.getAssignedRoleId() != null) ids.addAll(userRoles.findActiveHolderIds(t.getAssignedRoleId()));
		}
		return ids;
	}
}
