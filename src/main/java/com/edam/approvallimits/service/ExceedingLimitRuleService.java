package com.edam.approvallimits.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.edam.approvallimits.dto.ApprovalLimitDtos.ExceedingLimitRuleResponse;
import com.edam.approvallimits.dto.ApprovalLimitDtos.RuleEvaluation;
import com.edam.approvallimits.dto.ApprovalLimitDtos.SaveExceedingLimitRuleRequest;
import com.edam.approvallimits.model.DepartmentApprovalLimit.ExpenseType;
import com.edam.approvallimits.model.ExceedingLimitRule;
import com.edam.approvallimits.model.ExceedingLimitRule.EscalationType;
import com.edam.approvallimits.repository.ExceedingLimitRuleRepository;
import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.expenseconfiguration.model.ExpenseNature;
import com.edam.expenseconfiguration.repository.ExpenseNatureRepository;
import com.edam.masterdata.service.CurrencyService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exceeding-limit rules: their configuration, and their evaluation (Module D).
 *
 * <h2>Evaluation is implemented; enforcement is not, and that is deliberate</h2>
 * The BRD requires a rule to be configurable — one per OPEX/CAPEX nature, a positive threshold, an
 * optional description — and records that "whether threshold is enforced in routing is unresolved"
 * (line 765). The reference implementation stores the escalation type and does not act on it.
 *
 * <p>So there are two honest things this service can do, and it does both:
 * <ol>
 *   <li>Store and expose the rule, including the bank's stated escalation intent.</li>
 *   <li>Evaluate it — answer "would this amount cross this threshold?" — as a pure decision, so an
 *       administrator can confirm the configuration does what they meant before anything depends on it.</li>
 * </ol>
 * It does <strong>not</strong> add a CEO approval step or notify a CFO, because the routing consequence is
 * the workflow engine's job and the business decision is unresolved (OD-P2-03). A {@code BLOCK} rule
 * therefore does not block a submission today, and every response says so via {@code enforced=false} rather
 * than allowing the configuration to imply a control that does not exist.
 *
 * <h2>Why the evaluation is capped</h2>
 * A request can match several rules; the response lists the ones it crosses in ascending threshold order so
 * the reader sees the escalation ladder rather than an arbitrary single match.
 */
@Service
public class ExceedingLimitRuleService {

	/** The wording every response carries until OD-P2-03 is decided. */
	static final String NOT_ENFORCED_NOTE = "Configured and evaluated only. The consequence of exceeding the limit "
			+ "is not yet applied to approval routing (OD-P2-03).";

	private final ExceedingLimitRuleRepository rules;

	private final ExpenseNatureRepository natures;

	private final CurrencyService currencyService;

	private final AuditRecorder audit;

	public ExceedingLimitRuleService(ExceedingLimitRuleRepository rules, ExpenseNatureRepository natures,
			CurrencyService currencyService, AuditRecorder audit) {
		this.rules = rules;
		this.natures = natures;
		this.currencyService = currencyService;
		this.audit = audit;
	}

	// ---------------------------------------------------------------------------------------------
	// Evaluation
	// ---------------------------------------------------------------------------------------------

	/**
	 * Evaluates every active rule against a hypothetical request.
	 *
	 * <p>Pure with respect to the request: nothing is written and nothing is routed. The result names the
	 * rules crossed and repeats that no consequence follows yet.
	 */
	@Transactional(readOnly = true)
	public RuleEvaluation evaluate(String classification, UUID natureId, UUID currencyId, BigDecimal amount) {
		List<ExceedingLimitRuleResponse> triggered = this.rules.findByNatureIdAndActiveTrue(natureId)
			.stream()
			.filter(rule -> rule.triggers(classification, natureId, currencyId, amount))
			.map(this::toResponse)
			.toList();
		return new RuleEvaluation(natureId, currencyId, classification, amount, triggered, NOT_ENFORCED_NOTE);
	}

	/** The highest-priority action the configured rules would prescribe, or {@link EscalationType#NONE}. */
	@Transactional(readOnly = true)
	public EscalationType prescribedEscalation(String classification, UUID natureId, UUID currencyId,
			BigDecimal amount) {
		// BLOCK is the strongest outcome, then an added CEO approval, then a notification. Ordered by
		// consequence, not by enum order, so the answer does not depend on declaration order.
		List<EscalationType> triggered = this.rules.findByNatureIdAndActiveTrue(natureId)
			.stream()
			.filter(rule -> rule.triggers(classification, natureId, currencyId, amount))
			.map(ExceedingLimitRule::getEscalationType)
			.toList();
		if (triggered.contains(EscalationType.BLOCK)) {
			return EscalationType.BLOCK;
		}
		if (triggered.contains(EscalationType.ADD_CEO_APPROVAL)) {
			return EscalationType.ADD_CEO_APPROVAL;
		}
		if (triggered.contains(EscalationType.NOTIFY_CFO)) {
			return EscalationType.NOTIFY_CFO;
		}
		return EscalationType.NONE;
	}

	// ---------------------------------------------------------------------------------------------
	// Administration
	// ---------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ExceedingLimitRuleResponse> list(boolean includeInactive) {
		List<ExceedingLimitRule> rows = includeInactive ? this.rules.findAllByOrderByThresholdAmountAsc()
				: this.rules.findByActiveTrueOrderByThresholdAmountAsc();
		return rows.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public ExceedingLimitRuleResponse get(UUID id) {
		return toResponse(require(id));
	}

	@Transactional
	public ExceedingLimitRuleResponse create(SaveExceedingLimitRuleRequest request) {
		requireNature(request.natureId());
		this.currencyService.requireCurrency(request.currencyId());
		ExceedingLimitRule rule = new ExceedingLimitRule(request.expenseType(), request.natureId(),
				request.currencyId(), request.thresholdAmount(), request.escalationType());
		ExceedingLimitRule saved = this.rules.save(rule);
		this.audit.record(AuditAction.CREATE, "ExceedingLimitRule", saved.getId());
		return toResponse(saved);
	}

	@Transactional
	public ExceedingLimitRuleResponse update(UUID id, SaveExceedingLimitRuleRequest request) {
		ExceedingLimitRule rule = require(id);
		requireNature(request.natureId());
		this.currencyService.requireCurrency(request.currencyId());
		rule.setRule(request.expenseType(), request.natureId(), request.currencyId(), request.thresholdAmount(),
				request.escalationType());
		this.audit.record(AuditAction.UPDATE, "ExceedingLimitRule", id);
		return toResponse(rule);
	}

	@Transactional
	public ExceedingLimitRuleResponse setActive(UUID id, boolean active) {
		ExceedingLimitRule rule = require(id);
		rule.setActive(active);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "ExceedingLimitRule", id);
		return toResponse(rule);
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private ExceedingLimitRule require(UUID id) {
		return this.rules.findById(id).orElseThrow(() -> new ResourceNotFoundException("ExceedingLimitRule", id));
	}

	private ExpenseNature requireNature(UUID id) {
		return this.natures.findById(id).orElseThrow(() -> new ResourceNotFoundException("ExpenseNature", id));
	}

	private ExceedingLimitRuleResponse toResponse(ExceedingLimitRule rule) {
		String natureName = this.natures.findById(rule.getNatureId()).map(ExpenseNature::getName).orElse(null);
		return new ExceedingLimitRuleResponse(rule.getId(), rule.getExpenseType().name(), rule.getNatureId(),
				natureName, rule.getCurrencyId(), rule.getThresholdAmount(), rule.getEscalationType().name(),
				rule.isActive(), false, NOT_ENFORCED_NOTE);
	}

	/** Exposed so a controller can reject a classification typo rather than evaluating nothing. */
	static ExpenseType parseExpenseType(String raw) {
		try {
			return ExpenseType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (RuntimeException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The expense type must be OPEX, CAPEX or BOTH.");
		}
	}

}
