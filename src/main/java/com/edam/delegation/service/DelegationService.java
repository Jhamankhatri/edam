package com.edam.delegation.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.delegation.model.DelegationApproverStep;
import com.edam.delegation.model.DelegationRule;
import com.edam.delegation.repository.DelegationRuleRepository;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.repository.ExpenseCategoryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves which approval chain applies to a request (Module E).
 *
 * <h2>The precedence rule, stated explicitly</h2>
 * When several rules could match, the most specific wins:
 * <ol>
 *   <li>A rule naming the subcategory beats one that only names the category.</li>
 *   <li>Within the same specificity, the narrowest band wins — the one with the highest
 *       {@code minimumAmount} — because a bank that configures an override for large amounts expects it to
 *       take effect.</li>
 *   <li>If two rules remain tied, that is a configuration defect: the database's EXCLUDE constraint makes
 *       it impossible to insert, so the situation indicates the constraint was dropped. The service
 *       refuses rather than picking one, because picking one would be arbitrary and would change the
 *       approval chain without anyone noticing.</li>
 * </ol>
 *
 * <h2>No route means no submission</h2>
 * An empty result is <em>not</em> defaulted to "one approver" or "auto-approved". Submitting a financial
 * request with no configured approval chain would mean spending money that nobody approved. The caller
 * receives a precondition failure that names the classification and amount, so the administrator can fix
 * the matrix rather than guessing why submission failed.
 *
 * <h2>Why resolution is date-sensitive</h2>
 * Rules are effective-dated. Resolution therefore takes the instant the decision is made — the submission
 * time — and never the current time when evaluating a historical request.
 */
@Service
public class DelegationService {

	private static final Logger log = LoggerFactory.getLogger(DelegationService.class);

	private final DelegationRuleRepository rules;

	private final ExpenseCategoryRepository categories;

	private final AuditRecorder audit;

	private final Clock clock;

	public DelegationService(DelegationRuleRepository rules, ExpenseCategoryRepository categories,
			AuditRecorder audit, Clock clock) {
		this.rules = rules;
		this.categories = categories;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Resolves the approval chain for one request.
	 *
	 * @param categoryId the expense category
	 * @param subcategoryId the subcategory, or null when the request has none
	 * @param natureId the expense nature
	 * @param requestTypeId the administrative request type, or null for an ordinary expense
	 * @param currencyId the currency the amounts are expressed in
	 * @param amount the amount the band is matched against
	 * @param at the instant the route is being decided
	 */
	@Transactional(readOnly = true)
	public DelegationResolution resolve(UUID categoryId, UUID subcategoryId, UUID natureId, UUID requestTypeId,
			UUID currencyId, BigDecimal amount, Instant at) {
		List<DelegationRule> candidates = this.rules.findCandidates(categoryId, subcategoryId, natureId, requestTypeId,
				currencyId, amount, at);
		if (candidates.isEmpty()) {
			log.info("delegation.no_route categoryId={} subcategoryId={} natureId={} amount={}", categoryId,
					subcategoryId, amount);
			return DelegationResolution.none();
		}
		// Ordering is applied here rather than relied upon from the query, so the precedence rule is
		// visible in Java where it is tested, not hidden in a SQL ORDER BY that a future edit might reorder.
		List<DelegationRule> ranked = new ArrayList<>(candidates);
		ranked.sort((left, right) -> {
			boolean leftSpecific = left.getSubcategoryId() != null;
			boolean rightSpecific = right.getSubcategoryId() != null;
			if (leftSpecific != rightSpecific) {
				return leftSpecific ? -1 : 1;
			}
			return right.getMinimumAmount().compareTo(left.getMinimumAmount());
		});
		DelegationRule best = ranked.get(0);
		if (ranked.size() > 1) {
			DelegationRule runnerUp = ranked.get(1);
			boolean sameSpecificity = (best.getSubcategoryId() != null) == (runnerUp.getSubcategoryId() != null);
			boolean sameFloor = best.getMinimumAmount().compareTo(runnerUp.getMinimumAmount()) == 0;
			if (sameSpecificity && sameFloor) {
				throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
						"The delegation matrix contains two rules that both match this request ("
								+ best.getId() + " and " + runnerUp.getId()
								+ "). Resolve the overlap before submitting.");
			}
		}
		String currency = best.getCurrencyId().toString();
		return new DelegationResolution(best.getId(),
				DelegationResolution.describeBand(best.getMinimumAmount(), best.getMaximumAmount(), currency),
				best.getSteps());
	}

	/** Resolves, or fails with a message that names what an administrator must configure. */
	@Transactional(readOnly = true)
	public DelegationResolution requireRoute(UUID categoryId, UUID subcategoryId, UUID natureId, UUID requestTypeId,
			UUID currencyId, BigDecimal amount, Instant at) {
		DelegationResolution resolution = resolve(categoryId, subcategoryId, natureId, requestTypeId, currencyId, amount,
				at);
		if (resolution.isEmpty()) {
			ExpenseCategory category = this.categories.findById(categoryId).orElse(null);
			String name = (category == null) ? categoryId.toString() : category.getName();
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"No approval route is configured for " + name + " at " + amount.toPlainString()
							+ ". An administrator must add a delegation rule covering this amount.");
		}
		validateSteps(resolution);
		return resolution;
	}

	/**
	 * Legibility check on a resolved chain.
	 *
	 * <p>The schema already forbids a ROLE step without a role, but it cannot see whether a chain makes
	 * sense — for example two consecutive steps that resolve to the same office, which would look like a
	 * duplicate approval to the approver and is almost always a configuration mistake. Refusing at
	 * submission keeps the mistake out of the approval trail.
	 */
	private static void validateSteps(DelegationResolution resolution) {
		List<DelegationApproverStep> steps = resolution.steps();
		for (int index = 1; index < steps.size(); index++) {
			DelegationApproverStep previous = steps.get(index - 1);
			DelegationApproverStep current = steps.get(index);
			if (previous.getApproverType() == current.getApproverType()
					&& previous.getApproverType() != DelegationApproverStep.ApproverType.ROLE
					&& previous.getApproverType() != DelegationApproverStep.ApproverType.USER) {
				throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
						"The approval chain contains two consecutive " + current.getApproverType()
								+ " steps. Combine them or insert the missing step.");
			}
		}
	}

	/** True when the matrix has any active rule, used by the setup checklist. */
	@Transactional(readOnly = true)
	public long activeRuleCount() {
		return this.rules.countByActiveTrue();
	}

	/** The current instant used for effective-date evaluation. */
	public Instant now() {
		return Instant.now(this.clock);
	}

	@Transactional
	public void recordMatrixChange(UUID ruleId) {
		this.audit.record(AuditAction.UPDATE, "DelegationRule", ruleId);
	}

	@Transactional(readOnly = true)
	public Optional<DelegationRule> findRule(UUID id) {
		return this.rules.findById(id);
	}

}
