package com.edam.approvallimits.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.approvallimits.dto.ApprovalLimitDtos.RecommendingAuthorityResponse;
import com.edam.approvallimits.dto.ApprovalLimitDtos.ResolvedAuthority;
import com.edam.approvallimits.dto.ApprovalLimitDtos.SaveRecommendingAuthorityRequest;
import com.edam.approvallimits.model.RecommendingAuthority;
import com.edam.approvallimits.repository.RecommendingAuthorityRepository;
import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recommending authorities: who must recommend a department's expense requests (Module D).
 *
 * <h2>Why this closes a real gap</h2>
 * {@code WorkflowAssignmentResolver} handles {@code RECOMMENDING_AUTHORITY} by logging a debug line and
 * resolving nobody. That was honest — the table existed but no code read it — but it meant a workflow step
 * designated to a recommending authority could never open a task. {@link #resolve} is the missing
 * resolution: it reads the configured authority for the department and classification, and returns the
 * person the engine should assign.
 *
 * <h2>Resolution fails loudly, never plausibly</h2>
 * If a department has no recommending authority configured, or the configured person is inactive,
 * {@link #resolve} returns an empty result with a reason rather than falling back to the department head.
 * The head may well be a sensible substitute, but "sensible substitute" is a policy the bank has not stated,
 * and routing a financial recommendation to someone who was never designated is exactly the failure this
 * configuration exists to prevent.
 *
 * <h2>Effective dating and overlap</h2>
 * The authority is resolved for an instant, so a handover mid-year is honoured without rewriting history.
 * A database exclusion constraint prevents two active authorities for the same department and
 * classification, so there is never an arbitrary choice between two people.
 */
@Service
public class RecommendingAuthorityService {

	private static final Logger log = LoggerFactory.getLogger(RecommendingAuthorityService.class);

	private final RecommendingAuthorityRepository authorities;

	private final DepartmentRepository departments;

	private final UserRepository users;

	private final AuditRecorder audit;

	private final Clock clock;

	public RecommendingAuthorityService(RecommendingAuthorityRepository authorities, DepartmentRepository departments,
			UserRepository users, AuditRecorder audit, Clock clock) {
		this.authorities = authorities;
		this.departments = departments;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	// ---------------------------------------------------------------------------------------------
	// Resolution — the engine's entry point
	// ---------------------------------------------------------------------------------------------

	/**
	 * The active recommending authority for a department and classification, at an instant.
	 *
	 * @param classification {@code OPEX} or {@code CAPEX}
	 * @return the resolved person, or an explanation of why nobody could be resolved
	 */
	@Transactional(readOnly = true)
	public Optional<ResolvedAuthority> resolve(UUID departmentId, String classification, Instant at) {
		return this.authorities.findActiveForDepartment(departmentId)
			.stream()
			.filter(authority -> authority.appliesTo(classification, at))
			.filter(authority -> isActiveUser(authority.getUserId()))
			.max(Comparator.comparing(RecommendingAuthority::getEffectiveFrom))
			.map(authority -> new ResolvedAuthority(departmentId, classification, at, authority.getUserId(),
					userLabel(authority.getUserId()), "CONFIGURED"));
	}

	/**
	 * Resolves, or states why not — for a preview endpoint and for the engine's error message.
	 *
	 * <p>Returning an explanation rather than an exception keeps the "who would approve this?" preview a
	 * 200 response that a configuration screen can display, while the engine can still refuse to start a
	 * step with no approver.
	 */
	@Transactional(readOnly = true)
	public ResolvedAuthority explain(UUID departmentId, String classification, Instant at) {
		Optional<ResolvedAuthority> resolved = resolve(departmentId, classification, at);
		if (resolved.isPresent()) {
			return resolved.get();
		}
		List<RecommendingAuthority> all = this.authorities.findActiveForDepartment(departmentId)
			.stream()
			.filter(authority -> authority.appliesTo(classification, at))
			.toList();
		String resolution = all.isEmpty() ? "NONE_CONFIGURED" : "CONFIGURED_USER_INACTIVE";
		log.warn("recommending_authority.unresolved departmentId={} classification={} reason={}", departmentId,
				classification, resolution);
		return new ResolvedAuthority(departmentId, classification, at, null, null, resolution);
	}

	// ---------------------------------------------------------------------------------------------
	// Administration
	// ---------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<RecommendingAuthorityResponse> list(UUID departmentId) {
		List<RecommendingAuthority> rows = (departmentId == null) ? this.authorities.findAllByOrderByEffectiveFromDesc()
				: this.authorities.findActiveForDepartment(departmentId);
		return rows.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public RecommendingAuthorityResponse get(UUID id) {
		return toResponse(require(id));
	}

	/**
	 * Designates a recommending authority.
	 *
	 * <p>The person must belong to the department they will recommend for. A designation across department
	 * boundaries is almost always a data-entry mistake, and allowing it would let one department's
	 * recommendation queue be staffed by someone the other department never appointed.
	 */
	@Transactional
	public RecommendingAuthorityResponse create(SaveRecommendingAuthorityRequest request) {
		if (!this.departments.existsById(request.departmentId())) {
			throw new ResourceNotFoundException("Department", request.departmentId());
		}
		User user = this.users.findById(request.userId())
			.orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
		if (user.getDepartmentId() == null || !request.departmentId().equals(user.getDepartmentId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"That user does not belong to the selected department.");
		}
		Instant from = (request.effectiveFrom() == null) ? Instant.now(this.clock) : request.effectiveFrom();
		Instant to = request.effectiveTo();
		if (to != null && !to.isAfter(from)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The end of the period must follow its start.");
		}
		RecommendingAuthority authority = new RecommendingAuthority(request.departmentId(), request.userId(),
				request.expenseType(), from, to);
		RecommendingAuthority saved = this.authorities.save(authority);
		this.audit.record(AuditAction.CREATE, "RecommendingAuthority", saved.getId());
		return toResponse(saved);
	}

	/** Transfers the designation to another person, or closes the period. */
	@Transactional
	public RecommendingAuthorityResponse update(UUID id, SaveRecommendingAuthorityRequest request) {
		RecommendingAuthority authority = require(id);
		if (request.userId() != null && !request.userId().equals(authority.getUserId())) {
			User user = this.users.findById(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
			if (user.getDepartmentId() == null || !authority.getDepartmentId().equals(user.getDepartmentId())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"That user does not belong to this department.");
			}
			authority.setUser(request.userId());
		}
		if (request.effectiveTo() != null) {
			if (!request.effectiveTo().isAfter(authority.getEffectiveFrom())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"The end of the period must follow its start.");
			}
			authority.close(request.effectiveTo());
		}
		this.audit.record(AuditAction.UPDATE, "RecommendingAuthority", id);
		return toResponse(authority);
	}

	@Transactional
	public RecommendingAuthorityResponse setActive(UUID id, boolean active) {
		RecommendingAuthority authority = require(id);
		authority.setActive(active);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "RecommendingAuthority", id);
		return toResponse(authority);
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private boolean isActiveUser(UUID userId) {
		return this.users.findById(userId).map(User::isActive).orElse(false);
	}

	private String userLabel(UUID userId) {
		return this.users.findById(userId).map(User::displayName).orElse(null);
	}

	private RecommendingAuthority require(UUID id) {
		return this.authorities.findById(id).orElseThrow(() -> new ResourceNotFoundException("RecommendingAuthority", id));
	}

	private RecommendingAuthorityResponse toResponse(RecommendingAuthority authority) {
		String departmentName = this.departments.findById(authority.getDepartmentId())
			.map(department -> department.getName())
			.orElse(null);
		return new RecommendingAuthorityResponse(authority.getId(), authority.getDepartmentId(), departmentName,
				authority.getUserId(), userLabel(authority.getUserId()), authority.getExpenseType().name(),
				authority.getEffectiveFrom(), authority.getEffectiveTo(), authority.isActive());
	}

}
