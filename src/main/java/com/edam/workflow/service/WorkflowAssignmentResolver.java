package com.edam.workflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.edam.common.exception.ResourceNotFoundException;
import com.edam.organization.model.Department;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.rolemanagement.repository.RoleRepository;
import com.edam.rolemanagement.repository.UserRoleRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;
import com.edam.workflow.model.WorkflowStepApprover;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an approver designation into concrete user ids (Module I).
 *
 * <h2>Resolved at submission, against live configuration</h2>
 * "Department head" is looked up when the request is submitted, so a change of head between two requests
 * is honoured. Resolving when the workflow was published would have frozen the office holders and routed a
 * later request to whoever held the post months ago.
 *
 * <h2>What happens when an office is vacant</h2>
 * Nothing is invented. A designation that resolves to nobody contributes no approver, and the engine refuses
 * to start the step with "no approver could be resolved". The alternative — falling back to a role, or to
 * the requester's manager, or to the CFO — would route a financial decision to someone the bank never
 * designated, and would do so silently.
 *
 * <h2>Why inactive users are excluded</h2>
 * A deactivated employee cannot sign in, so a task assigned to them would sit unactioned until someone
 * noticed. Excluding them at resolution converts a stalled request into an immediate, explainable
 * configuration error.
 *
 * <h2>Deduplication</h2>
 * Two designations can resolve to the same person (a head who also holds the approving role). The result is a
 * set, so that person receives one task, not two — otherwise they could satisfy a quorum of two by
 * themselves.
 */
@Service
public class WorkflowAssignmentResolver {

	private static final Logger log = LoggerFactory.getLogger(WorkflowAssignmentResolver.class);

	private final UserRepository users;

	private final RoleRepository roles;

	private final DepartmentRepository departments;

	private final UserRoleRepository userRoles;

	private final com.edam.approvallimits.service.RecommendingAuthorityService recommendingAuthorities;

	private final com.edam.committee.service.CommitteeResolutionService committees;

	public WorkflowAssignmentResolver(UserRepository users, RoleRepository roles, DepartmentRepository departments,
			UserRoleRepository userRoles,
			com.edam.approvallimits.service.RecommendingAuthorityService recommendingAuthorities,
			com.edam.committee.service.CommitteeResolutionService committees) {
		this.users = users;
		this.roles = roles;
		this.departments = departments;
		this.userRoles = userRoles;
		this.recommendingAuthorities = recommendingAuthorities;
		this.committees = committees;
	}

	/**
	 * Resolves every designation of a step into distinct, active user ids, in a deterministic order.
	 *
	 * @param approvers the step's designations
	 * @param context the requesting department and user
	 */
	@Transactional(readOnly = true)
	public List<UUID> resolve(List<WorkflowStepApprover> approvers, WorkflowEngine.AssignmentContext context) {
		Set<UUID> resolved = new LinkedHashSet<>();
		for (WorkflowStepApprover approver : approvers) {
			switch (approver.getApproverType()) {
				case USER -> addIfActive(resolved, approver.getUserId());
				case ROLE -> addRoleHolders(resolved, approver.getRoleId());
				case DEPARTMENT_HEAD -> addDepartmentHead(resolved, context.requestingDepartmentId());
				case MANAGER -> addManager(resolved, context.requestingUserId());
				case CEO, CFO, HEAD_OF_ADMIN_GSD -> addOfficeHolder(resolved, approver.getApproverType());
				// Recommending authorities are resolved from the department's configured designation
				// (Module D). An unresolved authority contributes nobody, so the engine refuses to start the
				// step with "no approver could be resolved" rather than routing to a substitute.
				case RECOMMENDING_AUTHORITY -> addRecommendingAuthority(resolved, approver.getApproverType(),
						context);
				// A committee step names one amount band, and the people who act are whoever is currently
				// seated on it. Seats are effective-dated, so the answer depends on the instant; the same
				// instant the recommending-authority lookup uses is passed so a single submission cannot be
				// resolved against two different moments.
				case EXPENSE_COMMITTEE -> addCommitteeMembers(resolved, approver.getCommitteeLevelId());
			}
		}
		return new ArrayList<>(resolved);
	}

	/**
	 * The department's configured recommending authority for this classification.
	 *
	 * <p>The classification comes from the request's expense category, which the assignment context does not
	 * carry — so the resolution asks for {@code BOTH}-or-specific matching against OPEX and CAPEX both. That
	 * is deliberately conservative: an authority designated for one classification only must not be treated
	 * as designated for the other, so the lookup accepts a row only when it applies to a classification the
	 * caller has stated.
	 *
	 * <p>The effective-dated designation is resolved at <em>this instant</em>, never at the task's due
	 * date: "who recommends today" is a fact about staffing, and resolving it against a future deadline
	 * would assign the task to whoever holds the post when the request is due rather than who must
	 * recommend it now.
	 *
	 * @param resolved the accumulating approver set
	 * @param type the designation being resolved (kept for logging)
	 * @param context the requesting department
	 */
	private void addRecommendingAuthority(Set<UUID> resolved, WorkflowStepApprover.ApproverType type,
			WorkflowEngine.AssignmentContext context) {
		if (context.requestingDepartmentId() == null) {
			log.warn("workflow.recommending_authority_unresolved reason=no_department type={}", type);
			return;
		}
		java.time.Instant at = java.time.Instant.now();
		java.util.Optional<com.edam.approvallimits.dto.ApprovalLimitDtos.ResolvedAuthority> opex =
				this.recommendingAuthorities.resolve(context.requestingDepartmentId(), "OPEX", at);
		java.util.Optional<com.edam.approvallimits.dto.ApprovalLimitDtos.ResolvedAuthority> capex =
				this.recommendingAuthorities.resolve(context.requestingDepartmentId(), "CAPEX", at);
		opex.ifPresent(authority -> resolved.add(authority.userId()));
		capex.ifPresent(authority -> resolved.add(authority.userId()));
		if (opex.isEmpty() && capex.isEmpty()) {
			log.warn("workflow.recommending_authority_unresolved departmentId={} type={}",
					context.requestingDepartmentId(), type);
		}
	}

	/**
	 * Members currently seated on one committee level.
	 *
	 * <p>An empty level contributes nobody, and the engine then refuses to start the step with the step named.
	 * That is the honest outcome: BRD 8.5 hides the committee section when no committee workflow applies, but
	 * it does <em>not</em> say an approver may be substituted when one was configured and cannot be staffed.
	 */
	private void addCommitteeMembers(Set<UUID> target, UUID committeeLevelId) {
		if (committeeLevelId == null) {
			log.warn("workflow.committee_unresolved reason=no_level_designated");
			return;
		}
		List<UUID> members = this.committees.resolveLevelMembers(committeeLevelId, java.time.Instant.now());
		if (members.isEmpty()) {
			log.warn("workflow.committee_unresolved levelId={}", committeeLevelId);
			return;
		}
		target.addAll(members);
	}

	private void addIfActive(Set<UUID> target, UUID userId) {
		if (userId == null) {
			return;
		}
		this.users.findById(userId).filter(User::isActive).map(User::getId).ifPresent(target::add);
	}

	/**
	 * Holders of a role, in one query.
	 *
	 * <p>The expiry and active-user predicates live in {@code UserRoleRepository#findActiveHolderIds}, so an
	 * expired grant or a deactivated account cannot make someone an approver. Resolving this by loading every
	 * user and re-checking their roles would be a full scan plus N queries per assignment.
	 */
	private void addRoleHolders(Set<UUID> target, UUID roleId) {
		if (roleId == null) {
			return;
		}
		// Confirms the role exists so a dangling id is reported rather than silently resolving nobody.
		this.roles.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
		target.addAll(this.userRoles.findActiveHolderIds(roleId));
	}

	private void addDepartmentHead(Set<UUID> target, UUID departmentId) {
		if (departmentId == null) {
			log.debug("workflow.department_head_unresolved reason=no_department");
			return;
		}
		Department department = this.departments.findById(departmentId).orElse(null);
		if (department == null || department.getHeadUserId() == null) {
			log.debug("workflow.department_head_unresolved departmentId={}", departmentId);
			return;
		}
		addIfActive(target, department.getHeadUserId());
	}

	private void addManager(Set<UUID> target, UUID requesterId) {
		if (requesterId == null) {
			return;
		}
		this.users.findById(requesterId)
			.map(User::getManagerId)
			.ifPresent(managerId -> addIfActive(target, managerId));
	}

	/**
	 * Resolves the CEO, CFO and head-of-administration offices.
	 *
	 * <p>An "office" is represented by the holder of the corresponding role. That is a deliberate modelling
	 * choice: the requirement names these offices, and a bank has exactly one CEO, so a role with a single
	 * holder is a simpler and more accurate representation than a dedicated office table. If the role has no
	 * holder, the office is vacant and the designation resolves to nobody — see the class Javadoc for why
	 * nothing is substituted.
	 */
	private void addOfficeHolder(Set<UUID> target, WorkflowStepApprover.ApproverType office) {
		String roleCode = switch (office) {
			case CEO -> "CEO";
			case CFO -> "CFO";
			default -> "HEAD_OF_ADMIN_GSD";
		};
		this.roles.findByCode(roleCode).ifPresent(role -> target.addAll(this.userRoles.findActiveHolderIds(role.getId())));
	}

}
