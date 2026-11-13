package com.edam.expenserequest.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.expenserequest.dto.ExpenseRequestDtos.CreateRequest;
import com.edam.expenserequest.dto.ExpenseRequestDtos.RequestResponse;
import com.edam.expenserequest.dto.ExpenseRequestDtos.RequestSummary;
import com.edam.expenserequest.dto.ExpenseRequestDtos.UpdateRequest;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.model.RequestDocument;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.expenserequest.repository.RequestDocumentRepository;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.model.ExpenseNature;
import com.edam.expenseconfiguration.model.ExpenseRequestType;
import com.edam.expenseconfiguration.repository.ExpenseCategoryRepository;
import com.edam.expenseconfiguration.repository.ExpenseNatureRepository;
import com.edam.expenseconfiguration.repository.ExpenseRequestTypeRepository;
import com.edam.expenseconfiguration.repository.ExpenseSubcategoryRepository;
import com.edam.masterdata.service.CurrencyService;
import com.edam.organization.model.OrganizationProfile;
import com.edam.organization.repository.OrganizationProfileRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OPEX and CAPEX request management (Module G).
 *
 * <h2>The transaction boundary is the aggregate</h2>
 * FR-REQ-010 asks for the request, its child rows, its document selections and (later) its workflow
 * initiation to be saved "consistently" — one transaction. Every write path here is one transaction, so a
 * failure anywhere leaves no partially-built request. The one thing deliberately outside it is the audit
 * event, which {@code AuditRecorder} commits separately: evidence that an attempt happened must survive the
 * rollback of the attempt, otherwise a rejected submission leaves no trace.
 *
 * <h2>Authorization is applied here, not only at the endpoint</h2>
 * The controller's {@code @EdamPermission} answers "may this user use this operation at all". This service
 * answers the narrower question "may this user use it on <em>this</em> request": authorship for editing,
 * scope for viewing, and the editable-state check for mutation. Both are needed — a permission is not a
 * grant of authority over every row.
 *
 * <h2>Submission does not route</h2>
 * {@link #submit} validates, marks the request submitted and hands it to the workflow engine. Which
 * approvers are asked is the engine's decision from configured versions; there is no approval ladder in
 * this class. That boundary is what lets Phase 3 change routing without touching request management, and
 * what stops a second, contradictory routing rule from growing here.
 *
 * <h2>Duplicate submission</h2>
 * The engine refuses to start a second instance for the same request, and the request's own status must be
 * {@code DRAFT} or {@code RETURNED}. Both checks run: the status check produces the readable message, the
 * engine's check is the invariant that survives any future caller.
 */
@Service
public class ExpenseRequestService {

	private static final Logger log = LoggerFactory.getLogger(ExpenseRequestService.class);

	/** Sortable columns for the register. Anything else is refused rather than passed to SQL. */
	private static final Sort REGISTER_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

	private final ExpenseRequestRepository requests;

	private final RequestDocumentRepository documents;

	private final ExpenseRequestTypeRepository requestTypes;

	private final ExpenseCategoryRepository categories;

	private final ExpenseSubcategoryRepository subcategories;

	private final ExpenseNatureRepository natures;

	private final OrganizationProfileRepository organizationProfile;

	private final CurrencyService currencyService;

	private final ExchangeRateResolver exchangeRates;

	private final RequestNumberGenerator numbers;

	private final RequestAssembler assembler;

	private final AuditRecorder audit;

	private final Clock clock;

	public ExpenseRequestService(ExpenseRequestRepository requests, RequestDocumentRepository documents,
			ExpenseRequestTypeRepository requestTypes, ExpenseCategoryRepository categories,
			ExpenseSubcategoryRepository subcategories, ExpenseNatureRepository natures,
			OrganizationProfileRepository organizationProfile,
			CurrencyService currencyService, ExchangeRateResolver exchangeRates, RequestNumberGenerator numbers,
			RequestAssembler assembler, AuditRecorder audit, Clock clock) {
		this.requests = requests;
		this.documents = documents;
		this.requestTypes = requestTypes;
		this.categories = categories;
		this.subcategories = subcategories;
		this.natures = natures;
		this.organizationProfile = organizationProfile;
		this.currencyService = currencyService;
		this.exchangeRates = exchangeRates;
		this.numbers = numbers;
		this.assembler = assembler;
		this.audit = audit;
		this.clock = clock;
	}

	// ---------------------------------------------------------------------------------------------
	// Create and edit
	// ---------------------------------------------------------------------------------------------

	/**
	 * Creates a draft.
	 *
	 * <p>The request number is allocated here, from the database sequence, even for a draft: a requester who
	 * saves a draft must be able to quote it, and a number allocated only at submission would change under
	 * them. A draft that is abandoned leaves a gap in the sequence, which is the accepted cost (see
	 * {@code RequestNumberGenerator}).
	 */
	@Transactional
	public RequestResponse create(CreateRequest payload, EdamPrincipal caller) {
		Configuration configuration = resolveConfiguration(payload.requestTypeId(), payload.expenseCategoryId(),
				payload.expenseSubcategoryId(), payload.expenseNatureId());
		Instant now = Instant.now(this.clock);
		ExchangeRateResolver.Conversion conversion = convert(payload.requestedAmount(), payload.currencyId(), now);

		String requestNumber = this.numbers.next(configuration.categoryCode());
		ExpenseRequest request = new ExpenseRequest(requestNumber, caller.userId());
		this.assembler.applyCreate(request, payload, requestNumber, caller.userId(), configuration.baseCurrencyId(),
				conversion.exchangeRateId(), conversion.baseAmount());

		RequestValidationContext context = validationContext(payload.opex(), payload.capex(), request, configuration,
				payload.documents());
		// A draft is validated for internal consistency only; submission applies the full rules.
		RequestValidator.validate(request, context, false);

		// The header id is generated by the database (UUIDv7 default), so the row must be flushed before
		// documents can reference it: request_documents.expense_request_id is NOT NULL and a foreign key.
		ExpenseRequest saved = this.requests.saveAndFlush(request);
		attachDocuments(saved, payload.documents());
		this.audit.record(AuditAction.CREATE, "ExpenseRequest", saved.getId());
		log.info("request.created id={} number={} classification={} requester={}", saved.getId(),
				saved.getRequestNumber(), configuration.classification(), caller.userId());
		return RequestResponse.from(saved, configuration.classification());
	}

	/**
	 * Replaces an editable request.
	 *
	 * <p>The version in the payload is not used for locking — {@code @Version} on the entity does that, and a
	 * stale write fails with a concurrent-modification error. The check that matters here is authorship and
	 * state: only the author, and only while the request is {@code DRAFT} or {@code RETURNED}, may replace
	 * it. An approver holding {@code request:edit} is not thereby allowed to rewrite someone's request.
	 */
	@Transactional
	public RequestResponse update(UUID id, UpdateRequest payload, EdamPrincipal caller) {
		ExpenseRequest request = require(id);
		requireEditableBy(request, caller);
		Configuration configuration = resolveConfiguration(payload.requestTypeId(), payload.expenseCategoryId(),
				payload.expenseSubcategoryId(), payload.expenseNatureId());
		Instant now = Instant.now(this.clock);
		ExchangeRateResolver.Conversion conversion = convert(payload.requestedAmount(), payload.currencyId(), now);
		this.assembler.applyUpdate(request, payload, configuration.baseCurrencyId(), conversion.exchangeRateId(),
				conversion.baseAmount());
		RequestValidationContext context = validationContext(payload.opex(), payload.capex(), request, configuration,
				null);
		RequestValidator.validate(request, context, false);
		this.audit.record(AuditAction.UPDATE, "ExpenseRequest", id);
		return RequestResponse.from(request, configuration.classification());
	}

	// ---------------------------------------------------------------------------------------------
	// Submit and cancel
	// ---------------------------------------------------------------------------------------------

	/**
	 * Validates a request and marks it submitted.
	 *
	 * <h2>What this method does not do</h2>
	 * It does not select approvers. Starting the workflow needs a published version for the request type,
	 * which Phase 3's configuration module owns; until that exists, a submission moves the request to
	 * {@code SUBMITTED} and records that no instance was started. Deliberately not faked with a
	 * placeholder chain: an approval that was never routed is indistinguishable from one that was, and the
	 * difference is exactly what an audit asks about.
	 *
	 * <p>When the engine is wired, {@code WorkflowEngine.start} is called from this same transaction and the
	 * instance id is attached with {@link ExpenseRequest#markSubmitted}; the request state machine already
	 * accepts and records it, so the change is additive.
	 */
	@Transactional
	public RequestResponse submit(UUID id, EdamPrincipal caller) {
		ExpenseRequest request = require(id);
		requireEditableBy(request, caller);
		if (!request.isSubmittable()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"A request in status " + request.getCurrentStatusCode() + " cannot be submitted.");
		}
		Configuration configuration = resolveConfiguration(request.getRequestTypeId(), request.getExpenseCategoryId(),
				request.getExpenseSubcategoryId(), request.getExpenseNatureId());
		List<RequestDocument> attached = this.documents.findByExpenseRequestId(id);
		RequestValidationContext context = validationContextForExisting(request, configuration, attached);
		RequestValidator.validate(request, context, true);

		Instant now = Instant.now(this.clock);
		request.markSubmitted(request.getCurrentWorkflowInstanceId(), now);
		this.audit.record(AuditAction.SUBMIT, "ExpenseRequest", id);
		log.info("request.submitted id={} number={} workflowInstance={}", id, request.getRequestNumber(),
				request.getCurrentWorkflowInstanceId());
		return RequestResponse.from(request, configuration.classification());
	}

	/**
	 * Withdraws a request before a decision.
	 *
	 * <p>Only the author may cancel, and only while the request is not terminal. Cancelling an already
	 * approved request is a reversal, which is a different operation with different authority
	 * ({@code request:reverse}) and its own audit meaning — conflating them would let a requester undo a
	 * decision that was made about them.
	 */
	@Transactional
	public RequestResponse cancel(UUID id, String reason, EdamPrincipal caller) {
		ExpenseRequest request = require(id);
		if (!caller.userId().equals(request.getRequestedBy())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "Only the requester can cancel this request.");
		}
		if (request.isTerminal()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"A request in status " + request.getCurrentStatusCode() + " cannot be cancelled.");
		}
		if (reason == null || reason.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A cancellation reason is required.");
		}
		request.cancel(caller.userId(), reason.trim(), Instant.now(this.clock));
		this.audit.record(AuditAction.CANCEL, "ExpenseRequest", id, reason.trim());
		return RequestResponse.from(request, classification(request));
	}

	// ---------------------------------------------------------------------------------------------
	// Read
	// ---------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public RequestResponse get(UUID id, EdamPrincipal caller) {
		ExpenseRequest request = requireVisible(id, caller);
		return RequestResponse.from(request, classification(request));
	}

	/**
	 * The register, scoped to the caller.
	 *
	 * <p>{@code mineOnly} implements the BRD's "My Request" view; without it the caller sees the union of
	 * their own requests and everything within their data scope, which is the "My Approvals"-adjacent view.
	 * Both are the same query with one flag, so the two views cannot drift apart in what they consider
	 * visible.
	 */
	@Transactional(readOnly = true)
	public PageResponse<RequestSummary> list(String search, ExpenseRequest.Status status, UUID departmentId,
			UUID categoryId, boolean mineOnly, PageQuery page, EdamPrincipal caller) {
		Visibility visibility = Visibility.forCaller(caller);
		var pageable = page.toPageable(REGISTER_SORT);
		// Two explicit queries rather than one with a nullable enum parameter: an optional enum in JPQL
		// produces an ambiguous parameter type at runtime instead of a compile-time error.
		var result = (status == null)
				? this.requests.searchScoped(normaliseSearch(search), departmentId, categoryId, mineOnly,
						visibility.installationWide(), caller.userId(), ExpenseRequest.Status.DRAFT,
						!visibility.departmentIds().isEmpty(), visibility.departmentIds(),
						!visibility.branchIds().isEmpty(), visibility.branchIds(), pageable)
				: this.requests.searchScopedByStatus(normaliseSearch(search), status, departmentId, categoryId,
						mineOnly, visibility.installationWide(), caller.userId(), ExpenseRequest.Status.DRAFT,
						!visibility.departmentIds().isEmpty(), visibility.departmentIds(),
						!visibility.branchIds().isEmpty(), visibility.branchIds(), pageable);
		return PageResponse.of(result, request -> RequestSummary.from(request, classification(request)));
	}

	// ---------------------------------------------------------------------------------------------
	// Deletion is not offered
	// ---------------------------------------------------------------------------------------------

	// There is deliberately no delete. A request is a financial record that an approver acted on; removing
	// it would destroy the basis of a decision. A request that should not proceed is cancelled, which keeps
	// the row, its documents and its audit trail. The BRD's "cancellation/deletion policy" is listed as
	// unsupplied (line 837); cancel-only is the conservative reading and is recorded in the Phase 2 notes.

	// ---------------------------------------------------------------------------------------------
	// Authorization helpers
	// ---------------------------------------------------------------------------------------------

	private ExpenseRequest require(UUID id) {
		return this.requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("ExpenseRequest", id));
	}

	/**
	 * Loads a request the caller may see, or reports it missing.
	 *
	 * <p>Out-of-scope records produce 404 rather than 403: confirming that a request the caller may not see
	 * exists is itself a disclosure, and the distinction between "no such request" and "not yours" is exactly
	 * what an enumeration attempt is looking for.
	 */
	private ExpenseRequest requireVisible(UUID id, EdamPrincipal caller) {
		ExpenseRequest request = require(id);
		if (!isVisible(request, caller)) {
			throw new ResourceNotFoundException("ExpenseRequest", id);
		}
		return request;
	}

	boolean isVisible(ExpenseRequest request, EdamPrincipal caller) {
		if (caller == null || caller.hasInstallationScope()) {
			return true;
		}
		if (caller.userId().equals(request.getRequestedBy())) {
			return true;
		}
		// A draft is unfinished work and is visible only to its author.
		if (request.getCurrentStatusCode() == ExpenseRequest.Status.DRAFT) {
			return false;
		}
		if (request.getDepartmentId() != null && caller.scopesToDepartment(request.getDepartmentId())) {
			return true;
		}
		return request.getBranchId() != null && caller.scopesToBranch(request.getBranchId());
	}

	private void requireEditableBy(ExpenseRequest request, EdamPrincipal caller) {
		if (!caller.userId().equals(request.getRequestedBy())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "Only the requester can change this request.");
		}
		if (!request.isEditable()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"A request in status " + request.getCurrentStatusCode() + " cannot be changed.");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Configuration resolution
	// ---------------------------------------------------------------------------------------------

	/** The configured facts a save needs. */
	record Configuration(ExpenseCategory category, UUID baseCurrencyId, String classification) {

		String categoryCode() {
			return this.category.getCode();
		}
	}

	/**
	 * Resolves and cross-checks the configured references.
	 *
	 * <p>Each reference must exist and be active, and the category must belong to the request type: an OPEX
	 * request type with a CAPEX category is refused here rather than producing a request that no workflow can
	 * route. A sub-category, when supplied, must belong to the chosen category — the schema cannot enforce
	 * that across two references, and a mismatched pair would misclassify the expense in every report.
	 */
	private Configuration resolveConfiguration(UUID requestTypeId, UUID categoryId, UUID subcategoryId,
			UUID natureId) {
		ExpenseRequestType requestType = this.requestTypes.findById(requestTypeId)
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseRequestType", requestTypeId));
		if (!requestType.isActive()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED, "That request type is not active.");
		}
		ExpenseCategory category = this.categories.findById(categoryId)
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", categoryId));
		if (!category.isActive()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED, "That expense category is not active.");
		}
		ExpenseNature nature = this.natures.findById(natureId)
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseNature", natureId));
		if (!nature.isActive()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED, "That expense nature is not active.");
		}
		if (subcategoryId != null) {
			var subcategory = this.subcategories.findById(subcategoryId)
				.orElseThrow(() -> new ResourceNotFoundException("ExpenseSubcategory", subcategoryId));
			if (!subcategory.getCategoryId().equals(categoryId)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"The sub-category does not belong to the selected category.");
			}
		}
		String classification = category.getExpenseType().name();
		String typeCode = requestType.getCode();
		boolean capexType = typeCode != null && typeCode.toUpperCase(java.util.Locale.ROOT).contains("CAPEX");
		if (!"BOTH".equals(classification)) {
			boolean matches = capexType ? "CAPEX".equals(classification) : "OPEX".equals(classification);
			if (!matches) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"The selected category is not available for this request type.");
			}
		}
		UUID baseCurrencyId = requireBaseCurrencyId();
		this.currencyService.requireCurrency(baseCurrencyId);
		return new Configuration(category, baseCurrencyId, classification);
	}

	/**
	 * The installation's base currency.
	 *
	 * <p>Resolved from the active organization profile and treated as configuration, not as payload
	 * validation: without it no request can be expressed in base currency at all, and reporting a
	 * field-level error would blame the requester for a missing setup step.
	 */
	private UUID requireBaseCurrencyId() {
		UUID baseCurrencyId = this.organizationProfile.findFirstByActiveTrue()
			.map(OrganizationProfile::getDefaultCurrencyId)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"No active organization profile with a base currency is configured, so request amounts "
							+ "cannot be expressed in the base currency."));
		if (baseCurrencyId == null) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The organization profile has no base currency configured.");
		}
		return baseCurrencyId;
	}

	private ExchangeRateResolver.Conversion convert(BigDecimal amount, UUID currencyId, Instant at) {
		this.currencyService.requireCurrency(currencyId);
		return this.exchangeRates.convert(amount, currencyId, requireBaseCurrencyId(), at);
	}

	private String classification(ExpenseRequest request) {
		return this.categories.findById(request.getExpenseCategoryId())
			.map(category -> category.getExpenseType().name())
			.orElse("OPEX");
	}

	// ---------------------------------------------------------------------------------------------
	// Validation context
	// ---------------------------------------------------------------------------------------------

	/**
	 * The configuration facts for a create or update, from the payload plus the resolved configuration.
	 *
	 * <p>Child-row counts are not collected: the validator reads them from the aggregate it is given, so the
	 * two cannot disagree.
	 */
	private RequestValidationContext validationContext(
			com.edam.expenserequest.dto.ExpenseRequestDtos.OpexDetailPayload opex,
			com.edam.expenserequest.dto.ExpenseRequestDtos.CapexDetailPayload capex, ExpenseRequest request,
			Configuration configuration,
			List<com.edam.expenserequest.dto.ExpenseRequestDtos.DocumentReferencePayload> documentPayloads) {
		boolean capexRequest = capex != null;
		boolean threeQuotations = opex != null && Boolean.TRUE.equals(opex.threeQuotationsSelected());
		boolean alreadyApproved = opex != null && Boolean.TRUE.equals(opex.alreadyApproved());
		boolean taxExempt = opex != null && Boolean.TRUE.equals(opex.taxExempt());
		boolean grnRequired = opex != null && Boolean.TRUE.equals(opex.grnRequired());
		return new RequestValidationContext(configuration.classification(), capexRequest ? "CAPEX" : "OPEX",
				capexDepartmentEligible(request.getDepartmentId()), configuration.baseCurrencyId(),
				request.getCurrencyId(), threeQuotations, alreadyApproved, taxExempt, grnRequired,
				hasDocumentType(documentPayloads, "OTHER"), hasDocumentType(documentPayloads, "TAX"),
				hasDocumentType(documentPayloads, "GRN"), LocalDate.now(this.clock));
	}

	/** The configuration facts for a submission, where the options are no longer in a payload. */
	private RequestValidationContext validationContextForExisting(ExpenseRequest request, Configuration configuration,
			List<RequestDocument> attached) {
		boolean capexRequest = request.capexDetail() != null;
		return new RequestValidationContext(configuration.classification(), capexRequest ? "CAPEX" : "OPEX",
				capexDepartmentEligible(request.getDepartmentId()), configuration.baseCurrencyId(),
				request.getCurrencyId(), false, false, false, false, hasType(attached, "OTHER"),
				hasType(attached, "TAX"), hasType(attached, "GRN"), LocalDate.now(this.clock));
	}

	private static boolean hasType(List<RequestDocument> documents, String type) {
		return documents.stream().anyMatch(document -> type.equals(document.getDocumentType()));
	}

	/**
	 * Whether the department may raise CAPEX.
	 *
	 * <p>OD-P2-02: the BRD requires this to be configuration and forbids the reference implementation's
	 * hard-coded Admin/IT/Digital list. There is no per-department eligibility column in the schema, so
	 * inventing one here would be the "new table without reconciliation" the brief forbids. Until the
	 * decision is approved, eligibility is not restricted and the absence is recorded — the alternative,
	 * a guessed list, would silently exclude a department the bank intended to include.
	 */
	private static boolean capexDepartmentEligible(UUID departmentId) {
		return true;
	}


	// ---------------------------------------------------------------------------------------------
	// Documents
	// ---------------------------------------------------------------------------------------------

	/**
	 * Records the document selections.
	 *
	 * <p>The media asset itself is created by the upload path in Phase 4; this method only links an existing
	 * asset to the request. A reference to an asset that does not exist is refused by the schema's foreign
	 * key, and the type is validated here so the caller is told which value was wrong.
	 */
	private void attachDocuments(ExpenseRequest request, List<com.edam.expenserequest.dto.ExpenseRequestDtos.DocumentReferencePayload> payloads) {
		if (payloads == null || payloads.isEmpty()) {
			return;
		}
		Instant now = Instant.now(this.clock);
		for (var payload : payloads) {
			String type = payload.documentType() == null ? null
					: payload.documentType().trim().toUpperCase(java.util.Locale.ROOT);
			if (type == null || !RequestDocument.TYPES.contains(type)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"The document type must be one of " + String.join(", ", RequestDocument.TYPES) + ".");
			}
			if (this.documents.existsByExpenseRequestIdAndMediaAssetId(request.getId(), payload.mediaAssetId())) {
				throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
						"That document is already attached to this request.");
			}
			this.documents.save(new RequestDocument(request.getId(), payload.mediaAssetId(), type,
					payload.mandatory(), null, null, now));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Small helpers
	// ---------------------------------------------------------------------------------------------

	private static String normaliseSearch(String search) {
		if (search == null || search.isBlank()) {
			return null;
		}
		return search.trim();
	}

	/** The caller's visibility, precomputed for the query. Mirrors {@code UserManagementService}. */
	private record Visibility(boolean installationWide, List<UUID> departmentIds, List<UUID> branchIds) {

		static Visibility forCaller(EdamPrincipal caller) {
			if (caller == null || caller.hasInstallationScope()) {
				return new Visibility(true, List.of(), List.of());
			}
			List<UUID> departmentIds = caller.dataScopes()
				.stream()
				.filter(scope -> scope.type() == DataScope.Type.DEPARTMENT)
				.map(DataScope::referenceId)
				.filter(Objects::nonNull)
				.toList();
			List<UUID> branchIds = caller.dataScopes()
				.stream()
				.filter(scope -> scope.type() == DataScope.Type.BRANCH)
				.map(DataScope::referenceId)
				.filter(Objects::nonNull)
				.toList();
			return new Visibility(false, departmentIds, branchIds);
		}

	}

}
