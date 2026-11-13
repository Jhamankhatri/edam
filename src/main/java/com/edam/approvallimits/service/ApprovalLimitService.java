package com.edam.approvallimits.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.approvallimits.dto.ApprovalLimitDtos.ApprovalLimitResponse;
import com.edam.approvallimits.dto.ApprovalLimitDtos.AuthorityCheck;
import com.edam.approvallimits.dto.ApprovalLimitDtos.EffectiveLimit;
import com.edam.approvallimits.dto.ApprovalLimitDtos.SaveApprovalLimitRequest;
import com.edam.approvallimits.model.DepartmentApprovalLimit;
import com.edam.approvallimits.repository.DepartmentApprovalLimitRepository;
import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.masterdata.service.CurrencyService;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Department and personal approval limits (Module D).
 *
 * <h2>The rule this class exists to answer</h2>
 * "May this person approve this amount, in this department, for this kind of expense?" The answer is not
 * a single row lookup, because two authorities can apply: a department-wide limit and a personal limit
 * inside that department.
 *
 * <p>The resolution rules, recorded as <strong>OD-P2-04</strong>:
 * <ol>
 *   <li>The most specific rule wins. A personal limit applies over the department limit for that person.</li>
 *   <li>A personal limit may <strong>tighten</strong> a person's authority but never widen it: the
 *       effective ceiling is the <em>lower</em> of the two. A bank that wants a senior manager to approve
 *       more than their department head must say so by raising the department limit, not by quietly
 *       giving one person more authority than the department they sit in.</li>
 * </ol>
 * Rule 2 is a governance decision, stated as such. Inverting it (personal limit overrides upwards) is a
 * one-line change and a change of control, which is why it is documented rather than assumed.
 *
 * <h2>Fail-closed on absence</h2>
 * When a department has no limit configured, this class reports {@code permitted = false} with a reason
 * rather than treating absence as unlimited. "No limit configured" is an administrative omission, and
 * treating it as "approve anything" would convert an omission into a control bypass. See OD-P2-04.
 *
 * <h2>This class does not route</h2>
 * It reports what the limits are. Phase 3's engine decides which approver is asked; this class is what
 * the engine will consult, and what the preview endpoint shows an administrator today.
 */
@Service
public class ApprovalLimitService {

	private static final Logger log = LoggerFactory.getLogger(ApprovalLimitService.class);

	private final DepartmentApprovalLimitRepository limits;

	private final DepartmentRepository departments;

	private final UserRepository users;

	private final CurrencyService currencyService;

	private final AuditRecorder audit;

	private final Clock clock;

	public ApprovalLimitService(DepartmentApprovalLimitRepository limits, DepartmentRepository departments,
			UserRepository users, CurrencyService currencyService, AuditRecorder audit, Clock clock) {
		this.limits = limits;
		this.departments = departments;
		this.users = users;
		this.currencyService = currencyService;
		this.audit = audit;
		this.clock = clock;
	}

	// ---------------------------------------------------------------------------------------------
	// Resolution
	// ---------------------------------------------------------------------------------------------

	/**
	 * The effective ceiling for one person in one department, or empty when none is configured.
	 *
	 * @param classification {@code OPEX} or {@code CAPEX}
	 * @param amount the amount being approved, in the limit's currency
	 */
	@Transactional(readOnly = true)
	public Optional<EffectiveLimit> effectiveLimit(UUID departmentId, UUID userId, String classification,
			UUID currencyId, Instant at) {
		List<DepartmentApprovalLimit> candidates = this.limits.findActiveForDepartmentAndCurrency(departmentId,
				currencyId);
		List<DepartmentApprovalLimit> applicable = candidates.stream()
			.filter(limit -> userId == null || limit.getUserId() == null || limit.getUserId().equals(userId))
			.filter(limit -> limit.appliesTo(classification, currencyId, at))
			.toList();
		if (applicable.isEmpty()) {
			return Optional.empty();
		}
		Optional<DepartmentApprovalLimit> personal = applicable.stream()
			.filter(limit -> userId != null && userId.equals(limit.getUserId()))
			.max(Comparator.comparing(DepartmentApprovalLimit::getEffectiveFrom));
		Optional<DepartmentApprovalLimit> departmentWide = applicable.stream()
			.filter(limit -> limit.getUserId() == null)
			.max(Comparator.comparing(DepartmentApprovalLimit::getEffectiveFrom));
		if (personal.isEmpty() && departmentWide.isEmpty()) {
			// Only personal limits exist and none is for this user: nothing applies to them.
			return Optional.empty();
		}
		if (personal.isPresent() && departmentWide.isPresent()) {
			DepartmentApprovalLimit p = personal.get();
			DepartmentApprovalLimit d = departmentWide.get();
			// Rule 2: the lower of the two. Personal tightens, never widens.
			DepartmentApprovalLimit binding = (p.getApprovalLimit().compareTo(d.getApprovalLimit()) <= 0) ? p : d;
			return Optional.of(new EffectiveLimit(departmentId, userId, binding.getApprovalLimit(), currencyId,
					binding.getExpenseType().name(), binding.getId(),
					p.getId().equals(binding.getId()) ? "PERSONAL" : "DEPARTMENT"));
		}
		DepartmentApprovalLimit source = personal.orElseGet(departmentWide::get);
		String basis = personal.isPresent() ? "PERSONAL" : "DEPARTMENT";
		return Optional.of(new EffectiveLimit(departmentId, userId, source.getApprovalLimit(), currencyId,
				source.getExpenseType().name(), source.getId(), basis));
	}

	/**
	 * Whether the given people could approve an amount, for one department and classification.
	 *
	 * <p>This is the check Phase 3 will run before opening an approval step, and what the preview endpoint
	 * exposes today. It does not decide routing.
	 */
	@Transactional(readOnly = true)
	public AuthorityCheck checkAuthority(UUID departmentId, String classification, UUID currencyId,
			BigDecimal amount, List<UUID> candidateUserIds) {
		Instant now = Instant.now(this.clock);
		List<UUID> permitted = new ArrayList<>();
		List<String> reasons = new ArrayList<>();
		for (UUID userId : candidateUserIds) {
			Optional<EffectiveLimit> limit = effectiveLimit(departmentId, userId, classification, currencyId, now);
			if (limit.isEmpty()) {
				reasons.add("No approval limit is configured for this person in this department.");
				continue;
			}
			EffectiveLimit effective = limit.get();
			if (effective.limit().compareTo(amount) >= 0) {
				permitted.add(userId);
			}
			else {
				reasons.add("The amount exceeds the configured approval limit for this person.");
			}
		}
		log.debug("approval_limit.checked department={} classification={} amount={} permitted={} candidates={}",
				departmentId, classification, amount, permitted.size(), candidateUserIds.size());
		return new AuthorityCheck(departmentId, classification, currencyId, amount, permitted, reasons);
	}

	// ---------------------------------------------------------------------------------------------
	// Administration
	// ---------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ApprovalLimitResponse> list(UUID departmentId) {
		List<DepartmentApprovalLimit> rows = (departmentId == null) ? this.limits.findAllByOrderByEffectiveFromDesc()
				: this.limits.findByDepartmentIdOrderByEffectiveFromDesc(departmentId);
		return rows.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public ApprovalLimitResponse get(UUID id) {
		return toResponse(require(id));
	}

	/**
	 * Creates a limit.
	 *
	 * <p>Overlap is refused with a readable message; the database's EXCLUDE constraint refuses it anyway.
	 * The message matters because the constraint's own error names a constraint, not the business problem.
	 */
	@Transactional
	public ApprovalLimitResponse create(SaveApprovalLimitRequest request) {
		if (!this.departments.existsById(request.departmentId())) {
			throw new ResourceNotFoundException("Department", request.departmentId());
		}
		this.currencyService.requireCurrency(request.currencyId());
		if (request.userId() != null) {
			User user = this.users.findById(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
			// A personal limit for someone outside the department would never apply and would mislead the
			// administrator who configured it, so it is refused rather than stored inert.
			if (!request.departmentId().equals(user.getDepartmentId())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"That user does not belong to the selected department.");
			}
		}
		Instant from = (request.effectiveFrom() == null) ? Instant.now(this.clock) : request.effectiveFrom();
		Instant to = request.effectiveTo();
		if (to != null && !to.isAfter(from)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The end of the period must follow its start.");
		}
		DepartmentApprovalLimit limit = new DepartmentApprovalLimit(request.departmentId(), request.userId(),
				request.expenseType(), request.currencyId(), request.approvalLimit(), from, to);
		DepartmentApprovalLimit saved = this.limits.save(limit);
		this.audit.record(AuditAction.CREATE, "DepartmentApprovalLimit", saved.getId());
		return toResponse(saved);
	}

	/** Changes an amount, or closes a period. The authority a row belongs to is not editable. */
	@Transactional
	public ApprovalLimitResponse update(UUID id, SaveApprovalLimitRequest request) {
		DepartmentApprovalLimit limit = require(id);
		if (request.approvalLimit() != null) {
			limit.setLimit(request.approvalLimit());
		}
		Instant to = request.effectiveTo();
		if (to != null) {
			if (!to.isAfter(limit.getEffectiveFrom())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"The end of the period must follow its start.");
			}
			limit.close(to);
		}
		this.audit.record(AuditAction.UPDATE, "DepartmentApprovalLimit", id);
		return toResponse(limit);
	}

	@Transactional
	public ApprovalLimitResponse setActive(UUID id, boolean active) {
		DepartmentApprovalLimit limit = require(id);
		limit.setActive(active);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "DepartmentApprovalLimit", id);
		return toResponse(limit);
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private DepartmentApprovalLimit require(UUID id) {
		return this.limits.findById(id).orElseThrow(() -> new ResourceNotFoundException("ApprovalLimit", id));
	}

	private ApprovalLimitResponse toResponse(DepartmentApprovalLimit limit) {
		String departmentName = this.departments.findById(limit.getDepartmentId())
			.map(department -> department.getName())
			.orElse(null);
		String userLabel = (limit.getUserId() == null) ? null
				: this.users.findById(limit.getUserId()).map(User::displayName).orElse(null);
		return new ApprovalLimitResponse(limit.getId(), limit.getDepartmentId(), departmentName, limit.getUserId(),
				userLabel, limit.getExpenseType().name(), limit.getCurrencyId(), limit.getApprovalLimit(),
				limit.getEffectiveFrom(), limit.getEffectiveTo(), limit.isActive());
	}

}
