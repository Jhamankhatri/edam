package com.edam.configplane.category;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;
import com.edam.configplane.ExpenseNature;
import com.edam.configplane.authority.RecommendingAuthority;
import com.edam.configplane.authority.RecommendingAuthorityRepository;
import com.edam.configplane.committee.CommitteeLevel;
import com.edam.configplane.committee.CommitteeLevelRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expense Delegation Matrix administration and rule resolution (BRD FM-07, §8.6).
 *
 * <h2>Validation implemented here (cross-row, so not expressible as column constraints)</h2>
 * <ul>
 *   <li>No duplicate active rule for the same category/sub-category/nature (the database enforces
 *       this too, through a partial unique index).</li>
 *   <li>Amount bands must not overlap within the same classification.</li>
 *   <li>Lower bound must be below the upper bound.</li>
 *   <li>At least one recommending authority and at least one approver type.</li>
 *   <li>A referenced committee level must belong to the same institution.</li>
 * </ul>
 *
 * <h2>Resolution semantics</h2>
 * {@link #resolveRule} picks the candidate whose band covers the amount. If no band covers it, the
 * highest band applies (the legacy behaviour of "above the top range" is to use the most senior
 * route), and that decision is logged so it is auditable rather than silent. If still nothing
 * matches, the caller must fail closed — see {@code RoutingService}.
 */
@Service
public class DelegationMatrixService {

	private final DelegationRuleRepository rules;

	private final ExpenseCategoryRepository categories;

	private final ExpenseSubcategoryRepository subcategories;

	private final RecommendingAuthorityRepository authorities;

	private final CommitteeLevelRepository committeeLevels;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	public DelegationMatrixService(DelegationRuleRepository rules, ExpenseCategoryRepository categories,
			ExpenseSubcategoryRepository subcategories, RecommendingAuthorityRepository authorities,
			CommitteeLevelRepository committeeLevels, InstitutionContext institutionContext, AuditRecorder audit) {
		this.rules = rules;
		this.categories = categories;
		this.subcategories = subcategories;
		this.authorities = authorities;
		this.committeeLevels = committeeLevels;
		this.institutionContext = institutionContext;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<DelegationRule> list(boolean activeOnly) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return activeOnly ? this.rules.findByInstitutionIdAndActiveTrueOrderByAmountFromAsc(institutionId)
				: this.rules.findByInstitutionIdOrderByAmountFromAsc(institutionId);
	}

	@Transactional(readOnly = true)
	public DelegationRule get(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.rules.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Delegation rule", id));
	}

	@Transactional
	public DelegationRule create(ExpenseNature nature, Long categoryId, Long subcategoryId, BigDecimal amountFrom,
			BigDecimal amountTo, Long committeeLevelId, Set<ApproverType> approverTypes, List<Long> authorityIds) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExpenseCategory category = requireCategory(institutionId, categoryId);
		ExpenseSubcategory subcategory = requireSubcategory(institutionId, subcategoryId, categoryId);
		Set<RecommendingAuthority> authoritySet = resolveAuthorities(institutionId, authorityIds);
		CommitteeLevel level = resolveLevel(institutionId, committeeLevelId);
		validateBand(nature, categoryId, subcategoryId, amountFrom, amountTo, approverTypes, authoritySet, level, null);

		DelegationRule rule = new DelegationRule(category, subcategory, nature);
		rule.assignInstitution(institutionId);
		rule.update(category, subcategory, nature, amountFrom, amountTo, level, approverTypes, authoritySet);
		DelegationRule saved = this.rules.save(rule);
		this.audit.recordChange(AuditAction.CREATE, "DelegationRule", saved.getId(), "Delegation rule created", null,
				snapshot(saved));
		return saved;
	}

	@Transactional
	public DelegationRule update(Long id, Long categoryId, Long subcategoryId, BigDecimal amountFrom,
			BigDecimal amountTo, Long committeeLevelId, Set<ApproverType> approverTypes, List<Long> authorityIds) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		DelegationRule rule = get(id);
		ExpenseCategory category = requireCategory(institutionId, categoryId);
		ExpenseSubcategory subcategory = requireSubcategory(institutionId, subcategoryId, categoryId);
		Set<RecommendingAuthority> authoritySet = resolveAuthorities(institutionId, authorityIds);
		CommitteeLevel level = resolveLevel(institutionId, committeeLevelId);
		validateBand(rule.getExpenseNature(), categoryId, subcategoryId, amountFrom, amountTo, approverTypes, authoritySet,
				level, id);
		var before = snapshot(rule);
		rule.update(category, subcategory, rule.getExpenseNature(), amountFrom, amountTo, level, approverTypes,
				authoritySet);
		this.audit.recordChange(AuditAction.UPDATE, "DelegationRule", id, "Delegation rule updated", before,
				snapshot(rule));
		return rule;
	}

	@Transactional
	public DelegationRule setActive(Long id, boolean active) {
		DelegationRule rule = get(id);
		var before = snapshot(rule);
		if (active) {
			rule.activate();
		}
		else {
			rule.deactivate();
		}
		this.audit.recordChange(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "DelegationRule", id,
				"Delegation rule " + (active ? "activated" : "deactivated"), before, snapshot(rule));
		return rule;
	}

	@Transactional
	public void delete(Long id) {
		DelegationRule rule = get(id);
		var before = snapshot(rule);
		this.rules.delete(rule);
		this.audit.recordChange(AuditAction.DELETE, "DelegationRule", id, "Delegation rule deleted", before, null);
	}

	/**
	 * Resolve the rule that governs an amount. Returns empty when no rule exists at all; the caller
	 * must then fail closed rather than invent a route.
	 */
	@Transactional(readOnly = true)
	public java.util.Optional<DelegationRule> resolveRule(Long institutionId, ExpenseNature nature, Long categoryId,
			Long subcategoryId, BigDecimal amount) {
		List<DelegationRule> candidates = this.rules.findCandidateRules(institutionId, nature, categoryId,
				subcategoryId);
		if (candidates.isEmpty()) {
			return java.util.Optional.empty();
		}
		// Prefer a sub-category-specific rule over a category-wide one when both exist.
		List<DelegationRule> specific = candidates.stream().filter(rule -> rule.getSubcategory() != null).toList();
		List<DelegationRule> pool = specific.isEmpty() ? candidates : specific;
		return pool.stream().filter(rule -> rule.covers(amount)).findFirst()
			.or(() -> java.util.Optional.of(pool.get(pool.size() - 1)));
	}

	/**
	 * Explain the routing decision for an amount, without creating anything.
	 *
	 * <p>Distinguishes the three outcomes the BRD cares about:
	 * <ul>
	 *   <li>a rule whose <em>band</em> covers the amount;</li>
	 *   <li>a rule selected as the <em>highest band</em> because the amount is above every upper
	 *       bound — reported as such rather than presented as a clean match, because "we used the
	 *       top route" is a policy the administrator should be able to see;</li>
	 *   <li>no rule at all, which is a refusal and must fail closed at request creation.</li>
	 * </ul>
	 */
	@Transactional(readOnly = true)
	public com.edam.configplane.api.ConfigurationDtos.RoutingResolutionResponse resolve(ExpenseNature nature,
			Long categoryId, Long subcategoryId, BigDecimal amount) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		if (amount == null || amount.signum() <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The amount must be greater than zero.");
		}
		requireCategory(institutionId, categoryId);
		requireSubcategory(institutionId, subcategoryId, categoryId);
		List<DelegationRule> candidates = this.rules.findCandidateRules(institutionId, nature, categoryId,
				subcategoryId);
		if (candidates.isEmpty()) {
			return new com.edam.configplane.api.ConfigurationDtos.RoutingResolutionResponse(false, null, categoryId,
					subcategoryId, nature.name(), amount, null, Set.of(), List.of(), null,
					"No delegation rule is configured for this category, sub-category and nature. "
							+ "A request cannot be routed until one is published.");
		}
		List<DelegationRule> specific = candidates.stream().filter(rule -> rule.getSubcategory() != null).toList();
		List<DelegationRule> pool = specific.isEmpty() ? candidates : specific;
		List<DelegationRule> matching = pool.stream().filter(rule -> rule.covers(amount)).toList();
		boolean exactBandMatch = !matching.isEmpty();
		DelegationRule selected = exactBandMatch ? matching.get(0) : pool.get(pool.size() - 1);
		String bandDescription = exactBandMatch
				? "Amount falls within " + describeBand(selected)
				: "Amount is above every configured band; the highest band " + describeBand(selected)
						+ " applies by policy. Confirm this is intended.";
		return new com.edam.configplane.api.ConfigurationDtos.RoutingResolutionResponse(true, selected.getId(),
				categoryId, subcategoryId, nature.name(), amount, bandDescription,
				selected.getApproverTypes().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),
				selected.getRecommendingAuthorities()
					.stream()
					.map(RecommendingAuthority::getId)
					.sorted()
					.toList(),
				selected.getCommitteeLevel() == null ? null : selected.getCommitteeLevel().getId(), null);
	}

	private static String describeBand(DelegationRule rule) {
		return rule.getAmountFrom().toPlainString() + " to "
				+ (rule.getAmountTo() == null ? "unbounded" : rule.getAmountTo().toPlainString());
	}

	// ------------------------------------------------------------------
	// Validation helpers
	// ------------------------------------------------------------------

	private void validateBand(ExpenseNature nature, Long categoryId, Long subcategoryId, BigDecimal amountFrom,
			BigDecimal amountTo, Set<ApproverType> approverTypes, Set<RecommendingAuthority> authorities,
			CommitteeLevel level, Long excludeId) {
		if (approverTypes == null || approverTypes.isEmpty()) {
			throw new BusinessException("At least one approver type is required.");
		}
		if (authorities.isEmpty()) {
			throw new BusinessException("At least one recommending authority is required.");
		}
		if (amountFrom == null || amountFrom.signum() < 0) {
			throw new BusinessException("The lower amount bound must be zero or greater.");
		}
		if (amountTo != null && amountTo.compareTo(amountFrom) <= 0) {
			throw new BusinessException("The upper amount bound must be greater than the lower bound.");
		}
		if (level != null && !Set.copyOf(level.getMembers()).isEmpty() && level.getMinimumApprovals() > level
			.getRequiredMemberCount()) {
			throw new BusinessException("The committee level's minimum approvals exceed its required member count.");
		}
		Long institutionId = this.institutionContext.requireInstitutionId();
		List<DelegationRule> sameClass = this.rules.findSameClassification(institutionId, nature, categoryId,
				subcategoryId, excludeId);
		for (DelegationRule other : sameClass) {
			if (overlaps(amountFrom, amountTo, other.getAmountFrom(), other.getAmountTo())) {
				throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
						"This rule overlaps an existing active rule for the same classification and amount range.");
			}
		}
	}

	/** Half-open interval overlap: [a1, a2) ∩ [b1, b2) is non-empty. {@code null} upper = +infinity. */
	static boolean overlaps(BigDecimal aFrom, BigDecimal aTo, BigDecimal bFrom, BigDecimal bTo) {
		boolean aStartsBeforeBEnds = bTo == null || aFrom.compareTo(bTo) < 0;
		boolean bStartsBeforeAEnds = aTo == null || bFrom.compareTo(aTo) < 0;
		return aStartsBeforeBEnds && bStartsBeforeAEnds;
	}

	private ExpenseCategory requireCategory(Long institutionId, Long categoryId) {
		return this.categories.findByIdAndInstitutionId(categoryId, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Expense category", categoryId));
	}

	private ExpenseSubcategory requireSubcategory(Long institutionId, Long subcategoryId, Long categoryId) {
		if (subcategoryId == null) {
			return null;
		}
		ExpenseSubcategory subcategory = this.subcategories.findByIdAndInstitutionId(subcategoryId, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Expense sub-category", subcategoryId));
		if (!subcategory.getCategory().getId().equals(categoryId)) {
			throw new BusinessException("The selected sub-category does not belong to the selected category.");
		}
		return subcategory;
	}

	private Set<RecommendingAuthority> resolveAuthorities(Long institutionId, List<Long> authorityIds) {
		if (authorityIds == null || authorityIds.isEmpty()) {
			throw new BusinessException("At least one recommending authority is required.");
		}
		Set<RecommendingAuthority> resolved = new LinkedHashSet<>();
		for (Long authorityId : new ArrayList<>(new LinkedHashSet<>(authorityIds))) {
			RecommendingAuthority authority = this.authorities.findByIdAndInstitutionId(authorityId, institutionId)
				.orElseThrow(() -> new ResourceNotFoundException("Recommending authority", authorityId));
			resolved.add(authority);
		}
		return resolved;
	}

	private CommitteeLevel resolveLevel(Long institutionId, Long committeeLevelId) {
		if (committeeLevelId == null) {
			return null;
		}
		return this.committeeLevels.findByIdAndInstitutionId(committeeLevelId, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Committee level", committeeLevelId));
	}

	private static java.util.Map<String, Object> snapshot(DelegationRule rule) {
		java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
		state.put("nature", rule.getExpenseNature().name());
		state.put("category", rule.getCategory().getName());
		state.put("subcategory", rule.getSubcategory() == null ? null : rule.getSubcategory().getName());
		state.put("amountFrom", rule.getAmountFrom());
		state.put("amountTo", rule.getAmountTo());
		state.put("approverTypes", rule.getApproverTypes().stream().map(Enum::name).sorted().toList());
		state.put("committeeLevel", rule.getCommitteeLevel() == null ? null : rule.getCommitteeLevel().getName());
		state.put("active", rule.isActive());
		return state;
	}

}
