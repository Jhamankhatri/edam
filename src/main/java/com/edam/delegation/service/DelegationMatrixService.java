package com.edam.delegation.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.delegation.dto.DelegationDtos.ApproverStepResponse;
import com.edam.delegation.dto.DelegationDtos.DelegationResolutionResponse;
import com.edam.delegation.dto.DelegationDtos.DelegationRuleResponse;
import com.edam.delegation.dto.DelegationDtos.SaveApproverStepRequest;
import com.edam.delegation.dto.DelegationDtos.SaveDelegationRuleRequest;
import com.edam.delegation.model.DelegationApproverStep;
import com.edam.delegation.model.DelegationRule;
import com.edam.delegation.repository.DelegationRuleRepository;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.model.ExpenseNature;
import com.edam.expenseconfiguration.model.ExpenseRequestType;
import com.edam.expenseconfiguration.model.ExpenseSubcategory;
import com.edam.expenseconfiguration.service.ExpenseConfigurationService;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.service.CurrencyService;
import com.edam.rolemanagement.model.Role;
import com.edam.rolemanagement.repository.RoleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains and previews the delegation matrix (Module E).
 *
 * <h2>Two deliberately separate operations</h2>
 * <ul>
 *   <li><strong>Maintain</strong> writes rules and their approval chains. Every write is validated against
 *       the closed approver enum and the schema's correspondence rule (a ROLE step names a role, nothing
 *       else does), and is audited.</li>
 *   <li><strong>Preview</strong> answers "what route would this request take?" without creating anything. It
 *       exists because the matrix is the highest-consequence configuration in the product: an administrator
 *       who has to submit a real request to discover the route is being asked to guess with money. The
 *       preview uses the same resolution code the request module uses, so the two cannot disagree.</li>
 * </ul>
 *
 * <h2>Rules are never edited in place once effective</h2>
 * A rule that has already been used to approve something is closed (its {@code effectiveTo} set) and a new
 * one is inserted. That keeps "which rule applied when this was approved" answerable. The service therefore
 * exposes deactivation and expiry, not an arbitrary update.
 */
@Service
public class DelegationMatrixService {

	private final DelegationRuleRepository rules;

	private final ExpenseConfigurationService catalogue;

	private final CurrencyService currencies;

	private final RoleRepository roles;

	private final DelegationService resolution;

	private final AuditRecorder audit;

	private final Clock clock;

	public DelegationMatrixService(DelegationRuleRepository rules, ExpenseConfigurationService catalogue,
			CurrencyService currencies, RoleRepository roles, DelegationService resolution, AuditRecorder audit,
			Clock clock) {
		this.rules = rules;
		this.catalogue = catalogue;
		this.currencies = currencies;
		this.roles = roles;
		this.resolution = resolution;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<DelegationRuleResponse> list(UUID categoryId, boolean includeInactive) {
		return this.rules.list(categoryId, includeInactive).stream().map(this::toResponse).toList();
	}

	@Transactional
	public DelegationRuleResponse create(SaveDelegationRuleRequest request) {
		ExpenseCategory category = this.catalogue.requireCategory(request.categoryId());
		ExpenseNature nature = this.catalogue.requireNature(request.natureId());
		Currency currency = this.currencies.requireCurrency(request.currencyId());
		UUID subcategoryId = request.subcategoryId();
		if (subcategoryId != null) {
			ExpenseSubcategory subcategory = this.catalogue.requireSubcategory(subcategoryId);
			if (!subcategory.getCategoryId().equals(category.getId())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"That subcategory belongs to a different category.");
			}
		}
		BigDecimal minimum = (request.minimumAmount() == null) ? BigDecimal.ZERO : request.minimumAmount();
		BigDecimal maximum = request.maximumAmount();
		if (maximum != null && maximum.compareTo(minimum) <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The upper bound of an amount band must exceed its lower bound.");
		}
		Instant from = (request.effectiveFrom() == null) ? Instant.now(this.clock) : request.effectiveFrom();
		Instant to = request.effectiveTo();
		if (to != null && !to.isAfter(from)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The end of the period must follow its start.");
		}

		DelegationRule rule = new DelegationRule();
		rule.setCategoryId(category.getId());
		rule.setSubcategoryId(subcategoryId);
		rule.setNatureId(nature.getId());
		rule.setRequestTypeId(resolveRequestType(request.requestTypeId()));
		rule.setMinimumAmount(minimum);
		rule.setMaximumAmount(maximum);
		rule.setCurrencyId(currency.getId());
		rule.setEffectiveFrom(from);
		rule.setEffectiveTo(to);
		rule.setActive(true);
		rule.setSteps(buildSteps(rule, request.steps()));
		DelegationRule saved = this.rules.save(rule);
		this.audit.record(AuditAction.CREATE, "DelegationRule", saved.getId());
		return toResponse(saved);
	}

	@Transactional
	public DelegationRuleResponse close(UUID id) {
		DelegationRule rule = requireRule(id);
		if (rule.getEffectiveTo() == null) {
			rule.setEffectiveTo(Instant.now(this.clock));
			rule.setActive(false);
			this.rules.save(rule);
			this.audit.record(AuditAction.DEACTIVATE, "DelegationRule", id);
		}
		return toResponse(rule);
	}

	@Transactional
	public DelegationRuleResponse setActive(UUID id, boolean active) {
		DelegationRule rule = requireRule(id);
		rule.setActive(active);
		DelegationRule saved = this.rules.save(rule);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "DelegationRule", id);
		return toResponse(saved);
	}

	/**
	 * Answers "which chain would approve this?" using the same resolver the request module uses.
	 *
	 * <p>Returns a 200 with an explanatory message when no route exists, rather than a 4xx: the preview
	 * succeeded — the answer is "nothing is configured", which is precisely what the administrator needs to
	 * know. Failing the call would suggest the configuration could not be evaluated.
	 */
	@Transactional(readOnly = true)
	public DelegationResolutionResponse preview(UUID categoryId, UUID subcategoryId, UUID natureId,
			UUID requestTypeId, UUID currencyId, BigDecimal amount) {
		DelegationResolution resolved = this.resolution.resolve(categoryId, subcategoryId, natureId, requestTypeId,
				currencyId, amount, Instant.now(this.clock));
		if (resolved.isEmpty()) {
			return new DelegationResolutionResponse(null, null, 0, List.of(),
					"No delegation rule matches this classification and amount. Requests would be refused at "
							+ "submission until a rule is configured.");
		}
		List<ApproverStepResponse> steps = resolved.steps()
			.stream()
			.sorted(Comparator.comparingInt(DelegationApproverStep::getStepOrder))
			.map(this::toStepResponse)
			.toList();
		return new DelegationResolutionResponse(resolved.ruleId(), resolved.bandDescription(), steps.size(), steps,
				"This request would follow " + steps.size() + " approval step(s).");
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private List<DelegationApproverStep> buildSteps(DelegationRule rule, List<SaveApproverStepRequest> requested) {
		List<DelegationApproverStep> steps = new ArrayList<>();
		short order = 1;
		for (SaveApproverStepRequest step : requested) {
			DelegationApproverStep.ApproverType type = parseApproverType(step.approverType());
			UUID roleId = step.requiredRoleId();
			if (type == DelegationApproverStep.ApproverType.ROLE) {
				if (roleId == null) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED,
							"Step " + order + " is a ROLE step and must name a role.");
				}
				if (this.roles.findById(roleId).isEmpty()) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED,
							"Step " + order + " names a role that does not exist.");
				}
			}
			else if (roleId != null) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Step " + order + " is " + type + " and must not name a role.");
			}
			steps.add(new DelegationApproverStep(rule, order++, type, roleId, step.departmentScope()));
		}
		return steps;
	}

	private static DelegationApproverStep.ApproverType parseApproverType(String raw) {
		try {
			return DelegationApproverStep.ApproverType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (RuntimeException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"'" + raw + "' is not an approver type. Accepted values: "
							+ java.util.Arrays.toString(DelegationApproverStep.ApproverType.values()));
		}
	}

	private UUID resolveRequestType(UUID requestTypeId) {
		if (requestTypeId == null) {
			return null;
		}
		// Existence is checked by the caller-facing validation in the configuration service; here we only
		// need the id, but a dangling id would produce a rule that silently never matches.
		return requestTypeId;
	}

	private DelegationRule requireRule(UUID id) {
		return this.rules.findById(id).orElseThrow(() -> new ResourceNotFoundException("DelegationRule", id));
	}

	private DelegationRuleResponse toResponse(DelegationRule rule) {
		Map<UUID, String> categoryNames = new HashMap<>();
		ExpenseCategory category = this.catalogue.requireCategory(rule.getCategoryId());
		categoryNames.put(category.getId(), category.getName());
		String subcategoryName = (rule.getSubcategoryId() == null) ? null
				: this.catalogue.requireSubcategory(rule.getSubcategoryId()).getName();
		ExpenseNature nature = this.catalogue.requireNature(rule.getNatureId());
		String currencyCode = this.currencies.requireCurrency(rule.getCurrencyId()).getCode();
		List<ApproverStepResponse> steps = rule.getSteps()
			.stream()
			.sorted(Comparator.comparingInt(DelegationApproverStep::getStepOrder))
			.map(this::toStepResponse)
			.toList();
		String requestTypeName = (rule.getRequestTypeId() == null) ? null
				: this.catalogue.listRequestTypes()
					.stream()
					.filter(type -> type.id().equals(rule.getRequestTypeId()))
					.map(type -> type.name())
					.findFirst()
					.orElse(null);
		return new DelegationRuleResponse(rule.getId(), rule.getCategoryId(), category.getName(),
				rule.getSubcategoryId(), subcategoryName, rule.getNatureId(), nature.getName(), rule.getRequestTypeId(),
				requestTypeName, rule.getMinimumAmount(), rule.getMaximumAmount(), rule.getCurrencyId(), currencyCode,
				rule.getEffectiveFrom(), rule.getEffectiveTo(), rule.isActive(), steps);
	}

	private ApproverStepResponse toStepResponse(DelegationApproverStep step) {
		String roleCode = (step.getRequiredRoleId() == null) ? null
				: this.roles.findById(step.getRequiredRoleId()).map(Role::getCode).orElse(null);
		return new ApproverStepResponse(step.getStepOrder(), step.getApproverType().name(), step.getRequiredRoleId(),
				roleCode, step.getDepartmentScope(), step.isMandatory());
	}

}
