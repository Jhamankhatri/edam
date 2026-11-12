package com.edam.expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.edam.approval.ApprovalEngine;
import com.edam.approval.ApprovalTaskRepository;
import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.audit.Auditor;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.configplane.ExpenseNature;
import com.edam.configplane.category.CategoryService;
import com.edam.configplane.category.DelegationMatrixService;
import com.edam.configplane.category.ExpenseCategory;
import com.edam.configplane.category.ExpenseSubcategory;
import com.edam.configplane.currency.Currency;
import com.edam.configplane.currency.CurrencyRepository;
import com.edam.configplane.feature.FeatureFlagService;
import com.edam.configplane.feature.FeatureKey;
import com.edam.department.DepartmentRepository;
import com.edam.iam.UserMembership;
import com.edam.iam.UserMembershipRepository;
import com.edam.workflow.WorkflowService;
import com.edam.workflow.WorkflowVersion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, edits, submits, cancels and reverses expense requests (BRD FM-01, FM-04, §8.1..8.2).
 *
 * <h2>Validation this service owns</h2>
 * <ul>
 *   <li><strong>Currency.</strong> The chosen currency must be active in the institution, and the
 *       base-currency amount is computed once from the currency's current rate and stored. The BRD does
 *       not fix the rounding rule (OD-31), so the rate is captured and the conversion uses
 *       {@link RoundingMode#HALF_UP} to two decimals — the conventional financial rounding — and that
 *       choice is documented rather than buried.</li>
 *   <li><strong>Classification.</strong> Category must be active, and a sub-category must belong to the
 *       chosen category. The service re-checks the parent relationship because the API accepts both ids
 *       and a mismatched pair would put the request under a category it was never routed for.</li>
 *   <li><strong>Routing must exist.</strong> A request cannot be submitted unless the delegation matrix
 *       resolves an approver route for its amount. Otherwise the request would sit in a workflow nobody
 *       can act on; failing at submit is the only safe behaviour, and the error names the configuration
 *       that is missing.</li>
 *   <li><strong>Department assignment.</strong> A requester must have a department (BRD §5: "Department
 *       assignment required for OPEX submission").</li>
 *   <li><strong>CAPEX department restriction.</strong> When the institution's feature flag is on, only
 *       CAPEX-eligible departments may raise a CAPEX request (OD-21 default, now a per-institution flag
 *       instead of a hardcoded list).</li>
 *   <li><strong>Editability.</strong> Content can only change in {@code DRAFT} or {@code RETURNED}. A
 *       submitted request is not editable, which is what makes the approval record meaningful.</li>
 * </ul>
 *
 * <h2>Request numbering</h2>
 * {@code OPEX-2026-000123} / {@code CAPEX-2026-000123}. The counter is per institution, nature and
 * year, and uniqueness is enforced by a unique index. Numbering is derived from the current row count
 * plus a collision check, and a clash retries once: this is adequate for the expected volume and
 * explicitly not a distributed sequence — if the bank needs gap-free numbering under concurrency,
 * that is a design decision (and a sequence table) the requirements do not yet specify.
 */
@Service
public class ExpenseRequestService {

	private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy")
		.withZone(ZoneOffset.UTC);

	private final ExpenseRequestRepository requests;

	private final RequestHistoryRepository history;

	private final ApprovalTaskRepository tasks;

	private final ApprovalEngine engine;

	private final CurrencyRepository currencies;

	private final CategoryService categories;

	private final DelegationMatrixService delegation;

	private final WorkflowService workflows;

	private final UserMembershipRepository memberships;

	private final DepartmentRepository departments;

	private final FeatureFlagService featureFlags;

	private final InstitutionContext institutionContext;

	private final Auditor auditor;

	private final AuditRecorder audit;

	/**
	 * Optional port implemented by the document module. Null when that module is absent, in which case
	 * the "documents required" flag cannot be satisfied and submission is refused — the safe direction.
	 */
	private DocumentCounter documentCounter;

	public ExpenseRequestService(ExpenseRequestRepository requests, RequestHistoryRepository history,
			ApprovalTaskRepository tasks, ApprovalEngine engine, CurrencyRepository currencies,
			CategoryService categories, DelegationMatrixService delegation, WorkflowService workflows,
			UserMembershipRepository memberships, DepartmentRepository departments, FeatureFlagService featureFlags,
			InstitutionContext institutionContext, Auditor auditor, AuditRecorder audit) {
		this.requests = requests;
		this.history = history;
		this.tasks = tasks;
		this.engine = engine;
		this.currencies = currencies;
		this.categories = categories;
		this.delegation = delegation;
		this.workflows = workflows;
		this.memberships = memberships;
		this.departments = departments;
		this.featureFlags = featureFlags;
		this.institutionContext = institutionContext;
		this.auditor = auditor;
		this.audit = audit;
	}

	/** Wired only when the document module is present; not a constructor dependency to avoid a cycle. */
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	void setDocumentCounter(DocumentCounter documentCounter) {
		this.documentCounter = documentCounter;
	}

	// ------------------------------------------------------------------
	// Create / edit
	// ------------------------------------------------------------------

	@Transactional
	public ExpenseRequest create(ExpenseNature nature, Long categoryId, Long subcategoryId, String title,
			String description, BigDecimal amount, String currencyCode, String branchCode) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		EdamPrincipal principal = requirePrincipal();
		UserMembership membership = requireMembership(institutionId, principal.employeeId());
		requireDepartment(membership);
		requirePositiveAmount(amount);
		String cleanCurrency = currencyCode == null ? null
				: currencyCode.trim().toUpperCase(java.util.Locale.ROOT);
		Currency currency = this.currencies.findByInstitutionIdAndCode(institutionId, cleanCurrency)
			.filter(Currency::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The selected currency is not active in this institution."));
		ExpenseCategory category = this.categories.getCategory(categoryId);
		ExpenseSubcategory subcategory = resolveSubcategory(categoryId, subcategoryId);
		requireCategoryActive(category, subcategory);
		requireCapexAllowed(institutionId, nature, membership.getDepartmentCode());

		BigDecimal amountInBase = convert(amount, currency.getRateToBase());
		ExpenseRequest request = new ExpenseRequest(nextRequestNumber(institutionId, nature), title.trim(), amount,
				cleanCurrency, amountInBase, nature, category, subcategory, membership.getDepartmentCode(),
				principal.employeeId(), principal.getUsername(), principal.displayName());
		request.assignInstitution(institutionId);
		request.updateContent(description, amount, cleanCurrency, amountInBase, nature, category, subcategory,
				membership.getDepartmentCode(), branchCode);
		request.captureRate(new RequestCurrencySnapshot(institutionId, cleanCurrency, currency.getRateToBase()));
		ExpenseRequest saved = this.requests.save(request);
		appendHistory(saved, "CREATE", null, RequestState.DRAFT.name(), "Request created");
		this.audit.record(AuditAction.CREATE, "ExpenseRequest", saved.getId(),
				"Request " + saved.getRequestNumber() + " created");
		return saved;
	}

	@Transactional
	public ExpenseRequest update(Long id, Long categoryId, Long subcategoryId, String description, BigDecimal amount,
			String currencyCode, ExpenseNature nature, String branchCode) {
		ExpenseRequest request = get(id, this.institutionContext.requireInstitutionId());
		requireEditable(request);
		requireOwnerOrInstitutionAdmin(request);
		requirePositiveAmount(amount);
		Long institutionId = request.getInstitutionId();
		Currency currency = this.currencies.findByInstitutionIdAndCode(institutionId, currencyCode)
			.filter(Currency::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The selected currency is not active in this institution."));
		ExpenseCategory category = this.categories.getCategory(categoryId);
		ExpenseSubcategory subcategory = resolveSubcategory(categoryId, subcategoryId);
		requireCapexAllowed(institutionId, nature, request.getDepartmentCode());
		BigDecimal amountInBase = convert(amount, currency.getRateToBase());
		request.updateContent(description, amount, currency.getCode(), amountInBase, nature, category, subcategory,
				request.getDepartmentCode(), branchCode);
		appendHistory(request, "UPDATE", request.getState().name(), request.getState().name(),
				"Request content updated");
		this.audit.record(AuditAction.UPDATE, "ExpenseRequest", id, "Request content updated");
		return request;
	}

	// ------------------------------------------------------------------
	// Submit
	// ------------------------------------------------------------------

	/**
	 * Submit a draft or returned request into its workflow (BRD §8.2).
	 *
	 * <p>The workflow version is pinned <em>here</em>, once, and never changes afterwards. That is what
	 * makes a publish safe for in-flight work.
	 */
	@Transactional
	public ExpenseRequest submit(Long id, Long workflowDefinitionId) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExpenseRequest request = get(id, institutionId);
		requireOwnerOrInstitutionAdmin(request);
		if (request.getState() != RequestState.DRAFT && request.getState() != RequestState.RETURNED) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"Only a draft or a returned request can be submitted.");
		}
		if (this.featureFlags.isEnabled(FeatureKey.REQUIRE_SUPPORTING_DOCUMENTS)) {
			// Documents are optional in the requirements (OD-29); the flag exists so the policy is
			// explicit. When it is on, absence is a refusal with a message, not a silent pass.
			requireSupportingDocument(request);
		}
		WorkflowVersion version = this.workflows.requirePublishedVersion(workflowDefinitionId);
		if (!version.getInstitutionId().equals(institutionId)) {
			throw new ResourceNotFoundException("Workflow definition", workflowDefinitionId);
		}
		requireRouteResolvable(request);
		// A resubmission reuses the pinned version when one exists: an in-flight request must not be
		// re-routed by a version published after it first started.
		if (request.getWorkflowVersion() != null) {
			version = request.getWorkflowVersion();
		}
		var steps = this.workflows.stepsOf(version);
		String fromState = request.getState().name();
		request.pinWorkflow(version.getDefinition(), version, steps.first().code());
		Instant now = Instant.now();
		request.markSubmitted(now, steps.first().code());
		appendHistory(request, "SUBMIT", fromState, RequestState.SUBMITTED.name(),
				"Submitted into workflow " + version.getDefinition().getCode() + " v"
						+ version.getVersionNumber());
		this.engine.openFirstTask(request);
		this.audit.record(AuditAction.SUBMIT, "ExpenseRequest", id,
				"Request " + request.getRequestNumber() + " submitted");
		return request;
	}

	// ------------------------------------------------------------------
	// Cancel / reverse
	// ------------------------------------------------------------------

	/**
	 * Cancel an in-flight request (BRD §8.1, gated by {@link FeatureKey#REQUESTER_CANCELLATION}).
	 *
	 * <p>Only before the first approval has been recorded. The BRD does not fix the exact boundary
	 * (OD-28), so the conservative reading is used: once someone has approved, cancelling would
	 * discard a recorded decision and the correct operation is a reversal, which preserves it.
	 */
	@Transactional
	public ExpenseRequest cancel(Long id, String reason) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExpenseRequest request = get(id, institutionId);
		requireOwnerOrInstitutionAdmin(request);
		if (!this.featureFlags.isEnabled(FeatureKey.REQUESTER_CANCELLATION)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Cancellation is disabled for this institution. Contact your administrator.");
		}
		if (!request.getState().isInFlight() && request.getState() != RequestState.DRAFT) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"Only a draft or an in-flight request can be cancelled.");
		}
		long approvals = this.tasks.countApprovalsAtStep(institutionId, id, 1);
		if (approvals > 0) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"An approval has already been recorded. Use the reversal operation instead of cancelling.");
		}
		Instant now = Instant.now();
		String fromState = request.getState().name();
		request.markCancelled(reason, now);
		this.tasks.findByInstitutionIdAndRequestIdOrderBySequenceNumberAsc(institutionId, id)
			.stream()
			.filter(com.edam.approval.ApprovalTask::isPending)
			.forEach(task -> task.cancel(now));
		appendHistory(request, "CANCEL", fromState, RequestState.CANCELLED.name(), reason);
		this.audit.record(AuditAction.CANCEL, "ExpenseRequest", id, "Request cancelled: " + reason);
		return request;
	}

	/**
	 * Reverse an approved request.
	 *
	 * <p>The BRD requires a reversal to preserve the original approval in history; it does not define
	 * the finance mechanics (ledger posting, refund), which belong to the finance system and remain an
	 * open integration decision. This operation therefore records the state change and the reason and
	 * deliberately posts nothing.
	 */
	@Transactional
	public ExpenseRequest reverse(Long id, String reason) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExpenseRequest request = get(id, institutionId);
		if (request.getState() != RequestState.APPROVED) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"Only an approved request can be reversed.");
		}
		if (reason == null || reason.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A reversal reason is required.");
		}
		String fromState = request.getState().name();
		request.markReversed(Instant.now());
		appendHistory(request, "REVERSE", fromState, RequestState.REVERSED.name(), reason);
		this.audit.record(AuditAction.REVERT, "ExpenseRequest", id, "Approved request reversed: " + reason);
		return request;
	}

	// ------------------------------------------------------------------
	// Reads
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public ExpenseRequest get(Long id) {
		return get(id, this.institutionContext.requireInstitutionId());
	}

	@Transactional(readOnly = true)
	public Page<ExpenseRequest> search(RequestState state, ExpenseNature nature, String departmentCode,
			String search, boolean ownOnly, Pageable pageable) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		String requester = ownOnly ? requirePrincipal().getUsername() : null;
		return this.requests.search(institutionId, requester, state, nature, departmentCode, search, pageable);
	}

	@Transactional(readOnly = true)
	public java.util.List<RequestHistoryEntry> history(Long requestId) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		get(requestId, institutionId);
		return this.history.findByInstitutionIdAndRequestIdOrderBySequenceNumberAsc(institutionId, requestId);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private ExpenseRequest get(Long id, Long institutionId) {
		return this.requests.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Expense request", id));
	}

	private EdamPrincipal requirePrincipal() {
		return this.institutionContext.currentPrincipal()
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
	}

	private UserMembership requireMembership(Long institutionId, String employeeId) {
		return this.memberships.findByInstitutionIdAndEmployeeId(institutionId, employeeId)
			.filter(UserMembership::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_DENIED,
					"You do not have an active membership in this institution."));
	}

	private void requireDepartment(UserMembership membership) {
		if (membership.getDepartmentCode() == null || membership.getDepartmentCode().isBlank()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Your membership has no department assigned. A department is required to raise a request.");
		}
	}

	private void requireOwnerOrInstitutionAdmin(ExpenseRequest request) {
		EdamPrincipal principal = this.institutionContext.currentPrincipal()
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
		boolean owner = request.isOwnedBy(principal.getUsername());
		boolean admin = principal.hasPermissionIn(this.institutionContext.requireInstitution(),
				com.edam.common.security.Permissions.REQUEST_VIEW_ALL);
		if (!owner && !admin) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED,
					"You can only change your own requests.");
		}
	}

	private void requireEditable(ExpenseRequest request) {
		if (!request.getState().isEditable()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"A request can only be edited while it is a draft or has been returned for correction.");
		}
	}

	private ExpenseSubcategory resolveSubcategory(Long categoryId, Long subcategoryId) {
		if (subcategoryId == null) {
			return null;
		}
		ExpenseSubcategory subcategory = this.categories.getSubcategory(subcategoryId);
		if (!subcategory.getCategory().getId().equals(categoryId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The selected sub-category does not belong to the selected category.");
		}
		return subcategory;
	}

	private void requireCategoryActive(ExpenseCategory category, ExpenseSubcategory subcategory) {
		if (!category.isActive()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The selected category is inactive and cannot be used for a new request.");
		}
		if (subcategory != null && !subcategory.isActive()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The selected sub-category is inactive and cannot be used for a new request.");
		}
	}

	private void requireCapexAllowed(Long institutionId, ExpenseNature nature, String departmentCode) {
		if (nature != ExpenseNature.CAPEX) {
			return;
		}
		if (!this.featureFlags.isEnabled(institutionId, FeatureKey.CAPEX_DEPARTMENT_RESTRICTION)) {
			return;
		}
		boolean eligible = this.departments.findByInstitutionIdAndCode(institutionId, departmentCode)
			.map(com.edam.department.Department::isCapexEligible)
			.orElse(false);
		if (!eligible) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Your department is not permitted to raise CAPEX requests.");
		}
	}

	/**
	 * Fail closed when no delegation rule covers the request. Without this check the request would
	 * enter a workflow whose first step cannot be assigned, and the failure would surface to an
	 * approver instead of the person who can fix the configuration.
	 */
	private void requireRouteResolvable(ExpenseRequest request) {
		var resolution = this.delegation.resolve(request.getExpenseNature(), request.getCategory().getId(),
				request.getSubcategory() == null ? null : request.getSubcategory().getId(),
				request.getAmountInBase());
		if (!resolution.resolved()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED, "This request cannot be routed yet. "
					+ resolution.refusalReason() + " An administrator must configure the delegation matrix.");
		}
	}

	private void requireSupportingDocument(ExpenseRequest request) {
		// Document metadata is owned by the document module; the check is delegated so this service
		// does not depend on storage details.
		long documents = this.documentCount(request);
		if (documents == 0) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"This institution requires at least one supporting document before submission.");
		}
	}

	long documentCount(ExpenseRequest request) {
		return this.documentCounter == null ? 0 : this.documentCounter.count(request.getInstitutionId(),
				request.getId());
	}

	/**
	 * Port for counting a request's attachments. Implemented by the document module; absent when that
	 * module is not on the classpath, in which case the "documents required" flag cannot be satisfied
	 * and submission is refused rather than silently allowed.
	 */
	public interface DocumentCounter {

		long count(Long institutionId, Long requestId);

	}

	private void requirePositiveAmount(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The amount must be greater than zero.");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The amount may have at most two decimal places.");
		}
	}

	/** Convert to base currency using the captured rate; HALF_UP to two decimals (see class notes). */
	static BigDecimal convert(BigDecimal amount, BigDecimal rateToBase) {
		return amount.multiply(rateToBase).setScale(2, RoundingMode.HALF_UP);
	}

	private String nextRequestNumber(Long institutionId, ExpenseNature nature) {
		String year = YEAR.format(Instant.now());
		long sequence = this.requests.countForInstitution(institutionId) + 1;
		for (int attempt = 0; attempt < 5; attempt++) {
			String candidate = "%s-%s-%06d".formatted(nature.name(), year, sequence + attempt);
			if (this.requests.findByInstitutionIdAndRequestNumber(institutionId, candidate).isEmpty()) {
				return candidate;
			}
		}
		throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
				"Could not allocate a request number. Please retry.");
	}

	private void appendHistory(ExpenseRequest request, String action, String fromState, String toState,
			String comment) {
		int sequence = this.history.maxSequence(request.getId()) + 1;
		this.history.save(new RequestHistoryEntry(request.getInstitutionId(), request.getId(), sequence, action,
				fromState, toState, this.auditor.actor(), this.auditor.username(), comment));
	}

}
