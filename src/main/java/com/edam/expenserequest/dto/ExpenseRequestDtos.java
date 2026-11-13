package com.edam.expenserequest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.edam.expenserequest.model.CapexRequestDetail;
import com.edam.expenserequest.model.ExpenseAllocation;
import com.edam.expenserequest.model.ExpenseInvoice;
import com.edam.expenserequest.model.ExpensePreviousSummary;
import com.edam.expenserequest.model.ExpenseQuotation;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.model.ExpenseRequestItem;
import com.edam.expenserequest.model.OpexRequestDetail;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request and response shapes for OPEX and CAPEX requests (Module G).
 *
 * <h2>What is validated here, and what deliberately is not</h2>
 * These records carry the rules that concern a single field: presence, sign, length, scale. The rules that
 * concern fields <em>in combination</em> — a recurring expense needs a cadence, an unbudgeted purchase
 * needs a reason, the CAPEX three-figure equality — live in {@code RequestValidator}, because they cannot
 * be expressed as annotations and because putting them on the transport shape would make the same rule
 * unavailable to any other entry point (an import, a future message consumer).
 *
 * <h2>Amounts never travel as doubles</h2>
 * Every monetary field is {@link BigDecimal} with {@code @Digits(integer = 15, fraction = 4)}. A JSON number
 * with more precision than {@code NUMERIC(19,4)} can hold is rejected at the boundary rather than silently
 * rounded by the database, and a client cannot introduce binary floating-point error into a financial
 * record.
 *
 * <h2>The classification is not in the payload</h2>
 * Whether a request is OPEX or CAPEX is derived from its configured {@code requestTypeId} and the category's
 * {@code expense_type}, never chosen freely by the caller — otherwise a requester could pick the lighter
 * approval route by mislabelling a capital purchase. The detail sub-object that is supplied decides which
 * validator runs, and a mismatch is refused.
 */
public final class ExpenseRequestDtos {

	private ExpenseRequestDtos() {
	}

	// ---------------------------------------------------------------------------------------------
	// Common header
	// ---------------------------------------------------------------------------------------------

	/**
	 * Creates a request.
	 *
	 * <p>Exactly one of {@code opex} and {@code capex} must be present; that is enforced in the service
	 * rather than by a class-level annotation so the error can name the offending field.
	 */
	public record CreateRequest(@NotNull UUID requestTypeId, @NotNull UUID expenseCategoryId,
			UUID expenseSubcategoryId, @NotNull UUID expenseNatureId, @NotNull UUID departmentId, UUID branchId,
			@NotNull UUID currencyId,
			@NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal requestedAmount,
			@Size(max = 4000) String justification, @Valid OpexDetailPayload opex, @Valid CapexDetailPayload capex,
			@Valid List<AllocationPayload> allocations, @Valid List<ItemPayload> items,
			@Valid List<QuotationPayload> quotations, @Valid List<InvoicePayload> invoices,
			@Valid List<PreviousSummaryPayload> previousSummaries, @Valid List<StaPayload> staDetails,
			@Valid List<BudgetSummaryPayload> budgetSummaries,
			List<DocumentReferencePayload> documents) {
	}

	/**
	 * Replaces an editable request.
	 *
	 * <p>Deliberately a separate record from {@link CreateRequest} even though the fields coincide today: a
	 * create and a replace can diverge (for example, a replace must reject changing the currency of an
	 * already-approved request), and merging them would make that divergence a runtime check.
	 */
	public record UpdateRequest(@NotNull UUID requestTypeId, @NotNull UUID expenseCategoryId,
			UUID expenseSubcategoryId, @NotNull UUID expenseNatureId, UUID branchId, @NotNull UUID currencyId,
			@NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal requestedAmount,
			@Size(max = 4000) String justification, @Valid OpexDetailPayload opex, @Valid CapexDetailPayload capex,
			@Valid List<AllocationPayload> allocations, @Valid List<ItemPayload> items,
			@Valid List<QuotationPayload> quotations, @Valid List<InvoicePayload> invoices,
			@Valid List<PreviousSummaryPayload> previousSummaries, @Valid List<StaPayload> staDetails,
			@Valid List<BudgetSummaryPayload> budgetSummaries) {
	}

	// ---------------------------------------------------------------------------------------------
	// OPEX
	// ---------------------------------------------------------------------------------------------

	/**
	 * The OPEX detail map (BRD 8.2).
	 *
	 * <p>{@code recurring} and {@code recurrenceFrequency} are separate fields because the form has a
	 * checkbox and a select; the pair's consistency is a cross-field rule and is checked in the validator.
	 */
	public record OpexDetailPayload(@NotNull Boolean recurring, String recurrenceFrequency,
			@Size(max = 4000) String nonRecurringJustification,
			@DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal accruedAmount,
			@DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal previouslyApprovedAmount,
			@DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal ytdAmount,
			@DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal mtdAmount,
			@Size(max = 4000) String paymentTerms,
			@DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal budgetAvailable,
			@Size(max = 150) String budgetReference, Boolean threeQuotationsSelected, Boolean alreadyApproved,
			Boolean taxExempt, Boolean grnRequired) {
	}

	// ---------------------------------------------------------------------------------------------
	// CAPEX
	// ---------------------------------------------------------------------------------------------

	/**
	 * The CAPEX detail map (BRD 8.3).
	 *
	 * <p>{@code budgeted} defaults to {@code true} when absent, matching the column default and the
	 * ordinary case. {@code unbudgetedReason} and {@code overBudgetReason} are capped at 500 characters
	 * because the columns are {@code VARCHAR(500)}: accepting more and truncating would lose the reason
	 * silently, which is exactly the field an auditor reads.
	 */
	public record CapexDetailPayload(@Size(max = 50) String requestType, @Digits(integer = 15, fraction = 4) BigDecimal invoiceTotal,
			@Digits(integer = 15, fraction = 4) BigDecimal approvalAmount,
			@Digits(integer = 15, fraction = 4) BigDecimal totalCharges, Boolean budgeted,
			@DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal budgetAmount,
			@Size(max = 500) String unbudgetedReason, @Size(max = 500) String overBudgetReason,
			@Size(max = 100) String classification, LocalDate warrantyStartDate, LocalDate warrantyEndDate,
			@Size(max = 4000) String purchaseNature, @Size(max = 4000) String financeComment,
			@Size(max = 4000) String budgetComment, @Size(max = 4000) String termsAndConditions) {
	}

	// ---------------------------------------------------------------------------------------------
	// Supporting rows
	// ---------------------------------------------------------------------------------------------

	public record AllocationPayload(@NotNull UUID departmentId, UUID branchId, @Size(max = 100) String costCenter,
			@NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal allocationAmount,
			@DecimalMin("0.0001") @Digits(integer = 5, fraction = 4) BigDecimal allocationPercentage) {
	}

	public record ItemPayload(@NotBlank @Size(max = 500) String itemDescription,
			@NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal quantity,
			@NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
			LocalDate warrantyDate) {
	}

	public record QuotationPayload(UUID vendorId, @NotBlank @Size(max = 100) String quotationNumber,
			@NotNull LocalDate quotationDate,
			@NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal quotationAmount,
			@NotNull UUID currencyId, UUID mediaAssetId) {
	}

	public record InvoicePayload(@NotBlank @Size(max = 100) String invoiceNumber, @NotNull LocalDate invoiceDate,
			@NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal invoiceAmount,
			@NotNull UUID currencyId, UUID vendorId, UUID mediaAssetId) {
	}

	public record PreviousSummaryPayload(@NotBlank @Size(max = 40) String period,
			@DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal previousAmount,
			@DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal approvedAmount,
			@Size(max = 4000) String description) {
	}

	public record StaPayload(@NotNull UUID taxAuthorityId, @Size(max = 150) String staReference,
			@NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal taxAmount,
			@Size(max = 4000) String description) {
	}

	public record BudgetSummaryPayload(@NotBlank @Size(max = 150) String budgetReference,
			@NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal allocatedAmount,
			@NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal consumedAmount) {
	}

	/** A document to attach. The file itself is uploaded in Phase 4; the reference must already exist. */
	public record DocumentReferencePayload(@NotNull UUID mediaAssetId, @NotBlank @Size(max = 60) String documentType,
			boolean mandatory) {
	}

	// ---------------------------------------------------------------------------------------------
	// Responses
	// ---------------------------------------------------------------------------------------------

	/**
	 * One request, flattened to what a client needs.
	 *
	 * <p>The action flags are computed from the entity's own lifecycle rather than left to the client. A
	 * React form that has to decide for itself whether {@code SUBMITTED} is editable will get it wrong, and
	 * a wrong button is a support ticket; {@code canEdit} / {@code canSubmit} / {@code canCancel} state the
	 * server's view, and the server still enforces it.
	 */
	public record RequestResponse(UUID id, String requestNumber, String status, String classification,
			UUID requestTypeId, UUID expenseCategoryId, UUID expenseSubcategoryId, UUID expenseNatureId,
			UUID requestedBy, UUID departmentId, UUID branchId, UUID currencyId, BigDecimal requestedAmount,
			BigDecimal baseCurrencyAmount, UUID exchangeRateId, String justification, UUID workflowInstanceId,
			Instant submittedAt, Instant createdAt, Instant updatedAt, long version, boolean canEdit,
			boolean canSubmit, boolean canCancel, boolean terminal, OpexDetailResponse opex,
			CapexDetailResponse capex, List<AllocationResponse> allocations, List<ItemResponse> items,
			List<QuotationResponse> quotations, List<InvoiceResponse> invoices,
			List<PreviousSummaryResponse> previousSummaries, List<StaResponse> staDetails,
			List<BudgetSummaryResponse> budgetSummaries) {

		public static RequestResponse from(ExpenseRequest request, String classification) {
			return new RequestResponse(request.getId(), request.getRequestNumber(),
					request.getCurrentStatusCode().name(), classification, request.getRequestTypeId(),
					request.getExpenseCategoryId(), request.getExpenseSubcategoryId(), request.getExpenseNatureId(),
					request.getRequestedBy(), request.getDepartmentId(), request.getBranchId(),
					request.getCurrencyId(), request.getRequestedAmount(), request.getBaseCurrencyAmount(),
					request.getExchangeRateId(), request.getJustification(),
					request.getCurrentWorkflowInstanceId(), request.getSubmittedAt(), request.getCreatedAt(),
					request.getUpdatedAt(), request.getVersion(), request.isEditable(), request.isSubmittable(),
					!request.isTerminal(), request.isTerminal(),
					OpexDetailResponse.from(request.opexDetail()), CapexDetailResponse.from(request.capexDetail()),
					request.getAllocations().stream().map(AllocationResponse::from).toList(),
					request.getItems().stream().map(ItemResponse::from).toList(),
					request.getQuotations().stream().map(QuotationResponse::from).toList(),
					request.getInvoices().stream().map(InvoiceResponse::from).toList(),
					request.getPreviousSummaries().stream().map(PreviousSummaryResponse::from).toList(),
					request.getStaDetails().stream().map(StaResponse::from).toList(),
					request.getBudgetSummaries().stream().map(BudgetSummaryResponse::from).toList());
		}
	}

	public record OpexDetailResponse(UUID id, boolean recurring, String recurrenceFrequency,
			String nonRecurringJustification, BigDecimal accruedAmount, BigDecimal previouslyApprovedAmount,
			BigDecimal ytdAmount, BigDecimal mtdAmount, String paymentTerms, BigDecimal budgetAvailable,
			String budgetReference) {

		static OpexDetailResponse from(OpexRequestDetail detail) {
			if (detail == null) {
				return null;
			}
			return new OpexDetailResponse(detail.getId(), detail.isRecurring(),
					detail.getRecurrenceFrequency() == null ? null : detail.getRecurrenceFrequency().name(),
					detail.getNonRecurringJustification(), detail.getAccruedAmount(),
					detail.getPreviouslyApprovedAmount(), detail.getYtdAmount(), detail.getMtdAmount(),
					detail.getPaymentTerms(), detail.getBudgetAvailable(), detail.getBudgetReference());
		}
	}

	public record CapexDetailResponse(UUID id, String requestType, UUID staId, BigDecimal invoiceTotal,
			BigDecimal approvalAmount, BigDecimal totalCharges, boolean budgeted, BigDecimal budgetAmount,
			String unbudgetedReason, String overBudgetReason, String classification, LocalDate warrantyStartDate,
			LocalDate warrantyEndDate) {

		static CapexDetailResponse from(CapexRequestDetail detail) {
			if (detail == null) {
				return null;
			}
			return new CapexDetailResponse(detail.getId(), detail.getRequestType(), detail.getStaId(),
					detail.getInvoiceTotal(), detail.getApprovalAmount(), detail.getTotalCharges(),
					detail.isBudgeted(), detail.getBudgetAmount(), detail.getUnbudgetedReason(),
					detail.getOverBudgetReason(), detail.getClassification(), detail.getWarrantyStartDate(),
					detail.getWarrantyEndDate());
		}
	}

	public record AllocationResponse(UUID id, UUID departmentId, UUID branchId, String costCenter,
			BigDecimal allocationAmount, BigDecimal allocationPercentage) {

		static AllocationResponse from(ExpenseAllocation allocation) {
			return new AllocationResponse(allocation.getId(), allocation.getDepartmentId(),
					allocation.getBranchId(), allocation.getCostCenter(), allocation.getAllocationAmount(),
					allocation.getAllocationPercentage());
		}
	}

	public record ItemResponse(UUID id, String itemDescription, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal totalAmount, LocalDate warrantyDate) {

		static ItemResponse from(ExpenseRequestItem item) {
			return new ItemResponse(item.getId(), item.getItemDescription(), item.getQuantity(),
					item.getUnitPrice(), item.getTotalAmount(), item.getWarrantyDate());
		}
	}

	public record QuotationResponse(UUID id, UUID vendorId, String quotationNumber, LocalDate quotationDate,
			BigDecimal quotationAmount, UUID currencyId, UUID mediaAssetId) {

		static QuotationResponse from(ExpenseQuotation quotation) {
			return new QuotationResponse(quotation.getId(), quotation.getVendorId(),
					quotation.getQuotationNumber(), quotation.getQuotationDate(), quotation.getQuotationAmount(),
					quotation.getCurrencyId(), quotation.getMediaAssetId());
		}
	}

	public record InvoiceResponse(UUID id, String invoiceNumber, LocalDate invoiceDate, BigDecimal invoiceAmount,
			UUID currencyId, UUID vendorId, UUID mediaAssetId) {

		static InvoiceResponse from(ExpenseInvoice invoice) {
			return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
					invoice.getInvoiceAmount(), invoice.getCurrencyId(), invoice.getVendorId(),
					invoice.getMediaAssetId());
		}
	}

	public record PreviousSummaryResponse(UUID id, String period, BigDecimal previousAmount,
			BigDecimal approvedAmount, String description) {

		static PreviousSummaryResponse from(ExpensePreviousSummary summary) {
			return new PreviousSummaryResponse(summary.getId(), summary.getPeriod(), summary.getPreviousAmount(),
					summary.getApprovedAmount(), summary.getDescription());
		}
	}

	public record StaResponse(UUID id, UUID taxAuthorityId, String staReference, BigDecimal taxAmount,
			String description) {

		static StaResponse from(com.edam.expenserequest.model.CapexStaDetail detail) {
			return new StaResponse(detail.getId(), detail.getTaxAuthorityId(), detail.getStaReference(),
					detail.getTaxAmount(), detail.getDescription());
		}
	}

	public record BudgetSummaryResponse(UUID id, String budgetReference, BigDecimal allocatedAmount,
			BigDecimal consumedAmount, BigDecimal availableAmount) {

		static BudgetSummaryResponse from(com.edam.expenserequest.model.CapexBudgetSummary summary) {
			return new BudgetSummaryResponse(summary.getId(), summary.getBudgetReference(),
					summary.getAllocatedAmount(), summary.getConsumedAmount(), summary.getAvailableAmount());
		}
	}

	/**
	 * A row in the register (BRD 8.4).
	 *
	 * <p>Deliberately narrow: the register shows reference, type, date, amount and status, and loading the
	 * full aggregate for every row would make a 200-row page load its documents and allocations too.
	 */
	public record RequestSummary(UUID id, String requestNumber, String status, String classification,
			UUID departmentId, UUID currencyId, BigDecimal requestedAmount, Instant createdAt,
			Instant submittedAt) {

		public static RequestSummary from(ExpenseRequest request, String classification) {
			return new RequestSummary(request.getId(), request.getRequestNumber(),
					request.getCurrentStatusCode().name(), classification, request.getDepartmentId(),
					request.getCurrencyId(), request.getRequestedAmount(), request.getCreatedAt(),
					request.getSubmittedAt());
		}
	}

	/** A state change with an optional explanation. */
	public record CancelRequest(@NotBlank @Size(max = 500) String reason) {
	}

}
