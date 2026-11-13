package com.edam.expenserequest.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.edam.common.api.ApiResponse.FieldErrorDetail;
import com.edam.common.exception.ValidationFailedException;
import com.edam.expenserequest.model.CapexRequestDetail;
import com.edam.expenserequest.model.ExpenseAllocation;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.model.ExpenseRequestItem;
import com.edam.expenserequest.model.OpexRequestDetail;

/**
 * The conditional-field rules for OPEX and CAPEX requests (Module G).
 *
 * <h2>Why every rule is here and not on the DTO</h2>
 * Bean-validation annotations express rules that concern <em>one field in isolation</em> — non-null, not
 * negative, at most 500 characters. The rules that matter most in this module are <em>joint</em>: "a
 * recurring expense must state its cadence", "an unbudgeted purchase must state why", "the invoice total
 * must equal the approval amount", "quotations 2 and 3 are required when three quotations are claimed".
 * None of those can be expressed as a field annotation, and putting them in the controller would put a
 * business decision in the web layer. They live here, as one pure function, so they can be tested without
 * Spring or a database and so a reviewer can read all of them in one place.
 *
 * <h2>All errors, not the first</h2>
 * A form with three mistakes returns three field errors. Failing on the first would make the requester
 * discover them one round-trip at a time, which for a CAPEX submission is the difference between a
 * five-minute correction and a lost afternoon.
 *
 * <h2>Provenance</h2>
 * Each rule names its source. Where the BRD is silent, the rule is not invented: it is recorded in
 * {@code PHASE_2_OPEN_DECISIONS.md} and left out, so that the gap is visible to the reviewer rather than
 * filled with a plausible-looking guess.
 */
public final class RequestValidator {

	private RequestValidator() {
	}

	/**
	 * Validates a request about to be saved as a draft or submitted.
	 *
	 * @param submit {@code true} for submission, {@code false} for a draft save. A draft is only checked
	 * for internal consistency — a draft is by definition incomplete, and refusing to save one because it
	 * is not yet complete would make the save button useless.
	 * @throws ValidationFailedException listing every violated rule
	 */
	public static void validate(ExpenseRequest request, RequestValidationContext context, boolean submit) {
		List<FieldErrorDetail> errors = new ArrayList<>();
		validateCommon(request, context, errors, submit);
		if (context.isCapex()) {
			validateCapex(request, context, errors, submit);
		}
		else {
			validateOpex(request, context, errors, submit);
		}
		if (!errors.isEmpty()) {
			throw new ValidationFailedException(errors);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Rules shared by both classifications
	// ---------------------------------------------------------------------------------------------

	private static void validateCommon(ExpenseRequest request, RequestValidationContext context,
			List<FieldErrorDetail> errors, boolean submit) {
		// FR-REQ-003: category and nature are required; also a schema NOT NULL.
		if (request.getExpenseCategoryId() == null) {
			errors.add(new FieldErrorDetail("expenseCategoryId", "A category is required."));
		}
		if (request.getExpenseNatureId() == null) {
			errors.add(new FieldErrorDetail("expenseNatureId", "An expense nature is required."));
		}
		if (request.getRequestTypeId() == null) {
			errors.add(new FieldErrorDetail("requestTypeId", "A request type is required."));
		}
		// A category configured for the other classification must not be usable here:
		// "A configured active OPEX category is available only to the OPEX form" (BRD line 1257).
		if (request.getExpenseCategoryId() != null && !context.categoryMatchesRequestType()) {
			errors.add(new FieldErrorDetail("expenseCategoryId",
					"That category is not available for this request type."));
		}
		// Schema: requested_amount > 0.
		if (request.getRequestedAmount() == null || request.getRequestedAmount().signum() <= 0) {
			errors.add(new FieldErrorDetail("requestedAmount", "The requested amount must be greater than zero."));
		}
		if (request.getCurrencyId() == null) {
			errors.add(new FieldErrorDetail("currencyId", "A currency is required."));
		}
		if (request.getDepartmentId() == null) {
			errors.add(new FieldErrorDetail("departmentId",
					"A department is required: it determines the approval route."));
		}
		if (request.getBaseCurrencyAmount() == null) {
			errors.add(new FieldErrorDetail("baseCurrencyAmount",
					"The base-currency equivalent could not be calculated."));
		}
		// FR-REQ-004: charge allocation. A draft may be unallocated; a submission may not be, because
		// an unallocated expense has no department to charge and would be rejected by finance anyway.
		if (submit && request.getAllocations().isEmpty()) {
			errors.add(new FieldErrorDetail("allocations",
					"At least one charge allocation is required before submission."));
		}
		validateAllocationTotals(request, errors);
		// FR-REQ-005/006: if invoices are supplied, their total must equal the requested amount. The BRD
		// states this for CAPEX explicitly ("invoice total, approval amount and total charges must be
		// positive and equal") and FR-REQ-006 states it generally.
		BigDecimal invoiceTotal = request.getInvoices().isEmpty() ? null : request.getInvoices().stream()
			.map(invoice -> invoice.getInvoiceAmount())
			.filter(java.util.Objects::nonNull)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (invoiceTotal != null && request.getRequestedAmount() != null
				&& invoiceTotal.compareTo(request.getRequestedAmount()) != 0) {
			errors.add(new FieldErrorDetail("invoices", "The invoice total must equal the requested amount."));
		}
		// Schema: an item's quantity must be positive and its total must equal quantity x unit price. The
		// entity computes the total, so this only checks the description, which is NOT NULL and blank-proof
		// in the form.
		for (int i = 0; i < request.getItems().size(); i++) {
			ExpenseRequestItem item = request.getItems().get(i);
			if (item.getItemDescription() == null || item.getItemDescription().isBlank()) {
				errors.add(new FieldErrorDetail("items[" + i + "].itemDescription",
						"A description is required for each item."));
			}
		}
		if (submit && context.today() == null) {
			errors.add(new FieldErrorDetail("requestedAt", "The request date could not be determined."));
		}
	}

	/**
	 * Allocations may not exceed the amount being requested.
	 *
	 * <p>A total above the request would charge departments more than the request is for. The schema does
	 * not prevent it — each row is individually valid — so it is checked here. A total <em>below</em> the
	 * request is permitted and not an error: partial allocation is how a request that is partly borne by a
	 * cost centre and partly unallocated is represented, and the BRD does not require full allocation.
	 */
	private static void validateAllocationTotals(ExpenseRequest request, List<FieldErrorDetail> errors) {
		BigDecimal requested = request.getRequestedAmount();
		if (requested == null || request.getAllocations().isEmpty()) {
			return;
		}
		BigDecimal total = request.getAllocations().stream()
			.map(ExpenseAllocation::getAllocationAmount)
			.filter(java.util.Objects::nonNull)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (total.compareTo(requested) > 0) {
			errors.add(new FieldErrorDetail("allocations",
					"The allocated total cannot exceed the requested amount."));
		}
		BigDecimal percentage = request.getAllocations().stream()
			.map(ExpenseAllocation::getAllocationPercentage)
			.filter(java.util.Objects::nonNull)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (percentage.compareTo(new BigDecimal("100")) > 0) {
			errors.add(new FieldErrorDetail("allocations",
					"Allocated percentages cannot add up to more than 100."));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// OPEX
	// ---------------------------------------------------------------------------------------------

	private static void validateOpex(ExpenseRequest request, RequestValidationContext context,
			List<FieldErrorDetail> errors, boolean submit) {
		OpexRequestDetail detail = request.opexDetail();
		if (detail == null) {
			// A request must carry its typed detail (UK on expense_request_id). A draft may not have it yet.
			if (submit) {
				errors.add(new FieldErrorDetail("opexDetail", "OPEX details are required."));
			}
			return;
		}
		// BRD 8.2: "Non-recurring expenses require expenditure justification." Only on submission: a draft
		// may be started before the justification is written.
		if (submit && !detail.isRecurring()
				&& (detail.getNonRecurringJustification() == null || detail.getNonRecurringJustification().isBlank())) {
			errors.add(new FieldErrorDetail("opexDetail.nonRecurringJustification",
					"A justification is required for a non-recurring expense."));
		}
		// Schema CHECK: is_recurring requires a frequency. The entity clears a supplied frequency on a
		// non-recurring expense, so only the missing case can occur.
		if (detail.isRecurring() && detail.getRecurrenceFrequency() == null) {
			errors.add(new FieldErrorDetail("opexDetail.recurrenceFrequency",
					"A recurring expense must state how often it recurs."));
		}
		// BRD 8.2: "Quotation 1 is always required. Quotations 2 and 3 are required when Three Quotations
		// Attached is selected; otherwise comments are required for missing optional quotations."
		if (submit) {
			int quoteCount = request.getQuotations().size();
			if (quoteCount == 0) {
				errors.add(new FieldErrorDetail("quotations", "At least one quotation is required."));
			}
			else if (context.threeQuotationsSelected() && quoteCount < 3) {
				errors.add(new FieldErrorDetail("quotations",
						"Three quotations were declared but fewer than three were supplied."));
			}
		}
		// BRD 8.2 conditional attachments. Checked on submission only, for the same reason the other
		// completeness rules are: a draft may legitimately be saved before its documents are attached, and
		// rejecting the save would make the requester hold the form open while they fetch a certificate.
		if (submit) {
			// "Already Approved requires an approved-document attachment."
			if (context.alreadyApproved() && !context.hasApprovedDocument()) {
				errors.add(new FieldErrorDetail("documents",
						"An approved-expense document is required when the expense is marked already approved."));
			}
			// "Tax-exemption and GRN documents are required when their respective options are selected."
			if (context.taxExempt() && !context.hasTaxExemptionDocument()) {
				errors.add(new FieldErrorDetail("documents",
						"A tax-exemption certificate is required when a tax exemption is claimed."));
			}
			if (context.grnRequired() && !context.hasGrnDocument()) {
				errors.add(new FieldErrorDetail("documents",
						"A goods-received note is required for this expense."));
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// CAPEX
	// ---------------------------------------------------------------------------------------------

	private static void validateCapex(ExpenseRequest request, RequestValidationContext context,
			List<FieldErrorDetail> errors, boolean submit) {
		// BRD 8.3 / OD-P2-02: new CAPEX requests are limited to configured departments. The reference
		// implementation hard-codes Admin, IT and Digital; the target must make it configuration, so the
		// decision arrives in the context and is never a department name in this class.
		if (!context.capexDepartmentEligible()) {
			errors.add(new FieldErrorDetail("departmentId",
					"This department is not configured to raise capital expenditure requests."));
		}
		CapexRequestDetail detail = request.capexDetail();
		if (detail == null) {
			if (submit) {
				errors.add(new FieldErrorDetail("capexDetail", "CAPEX details are required."));
			}
			return;
		}
		// BRD FR-REQ-003 / 8.3: request type required for CAPEX.
		if (detail.getRequestType() == null || detail.getRequestType().isBlank()) {
			errors.add(new FieldErrorDetail("capexDetail.requestType", "A CAPEX request type is required."));
		}
		// BRD 8.3: "Request type and Sales Tax Authority selection are required."
		if (submit && request.getStaDetails().isEmpty()) {
			errors.add(new FieldErrorDetail("staDetails", "At least one sales tax authority is required."));
		}
		// BRD 8.3: "Item-wise details are mandatory before proceeding."
		if (submit && request.getItems().isEmpty()) {
			errors.add(new FieldErrorDetail("items", "At least one item is required for a CAPEX request."));
		}
		// BRD 8.3: "Invoice total, approval amount and total charges must be positive and equal."
		// This settles what was recorded as OD-P2-06: the BRD does state the rule, so it is enforced
		// rather than left open. Each figure must be positive and all three must agree.
		validateCapexAmounts(detail, errors);
		// Schema CHECK: is_budgeted OR unbudgeted_reason IS NOT NULL. The BRD words it as "Unbudgeted or
		// over-budget requests require a reason of at most 500 characters" — the length is a column limit.
		if (!detail.isBudgeted()
				&& (detail.getUnbudgetedReason() == null || detail.getUnbudgetedReason().isBlank())) {
			errors.add(new FieldErrorDetail("capexDetail.unbudgetedReason",
					"A reason is required for a request that is not budgeted."));
		}
		// Over-budget means budgeted, with an approved budget that the request exceeds. The BRD requires a
		// reason; the comparison itself is what defines "over budget".
		if (detail.isBudgeted() && detail.getBudgetAmount() != null && request.getRequestedAmount() != null
				&& request.getRequestedAmount().compareTo(detail.getBudgetAmount()) > 0
				&& (detail.getOverBudgetReason() == null || detail.getOverBudgetReason().isBlank())) {
			errors.add(new FieldErrorDetail("capexDetail.overBudgetReason",
					"A reason is required when the request exceeds its approved budget."));
		}
		// BRD 8.3: "Warranty date cannot be in the past." Compared against the injected date so the rule is
		// testable; the entity has already confirmed start <= end via the schema's CHECK.
		if (context.today() != null) {
			if (detail.getWarrantyStartDate() != null && detail.getWarrantyStartDate().isBefore(context.today())) {
				errors.add(new FieldErrorDetail("capexDetail.warrantyStartDate",
						"The warranty start date cannot be in the past."));
			}
			if (detail.getWarrantyEndDate() != null && detail.getWarrantyEndDate().isBefore(context.today())) {
				errors.add(new FieldErrorDetail("capexDetail.warrantyEndDate",
						"The warranty end date cannot be in the past."));
			}
		}
	}

	/**
	 * The three-figure equality the BRD requires for CAPEX.
	 *
	 * <p>Comparison is by {@code compareTo}, not {@code equals}: {@code 100.00} and {@code 100.0000} are
	 * the same money, and rejecting one because of its scale would be a bug the requester cannot see the
	 * cause of.
	 */
	private static void validateCapexAmounts(CapexRequestDetail detail, List<FieldErrorDetail> errors) {
		BigDecimal invoiceTotal = detail.getInvoiceTotal();
		BigDecimal approvalAmount = detail.getApprovalAmount();
		BigDecimal totalCharges = detail.getTotalCharges();
		if (approvalAmount == null || approvalAmount.signum() <= 0) {
			errors.add(new FieldErrorDetail("capexDetail.approvalAmount",
					"The approval amount must be greater than zero."));
		}
		if (invoiceTotal != null && invoiceTotal.signum() <= 0) {
			errors.add(new FieldErrorDetail("capexDetail.invoiceTotal",
					"The invoice total must be greater than zero."));
		}
		if (totalCharges != null && totalCharges.signum() <= 0) {
			errors.add(new FieldErrorDetail("capexDetail.totalCharges",
					"The total charges must be greater than zero."));
		}
		// Only compare figures that are present: a draft may have one and not the others, and the
		// mandatory-ness of each is checked above and by the form.
		if (invoiceTotal != null && approvalAmount != null && invoiceTotal.compareTo(approvalAmount) != 0) {
			errors.add(new FieldErrorDetail("capexDetail.invoiceTotal",
					"The invoice total must equal the approval amount."));
		}
		if (totalCharges != null && approvalAmount != null && totalCharges.compareTo(approvalAmount) != 0) {
			errors.add(new FieldErrorDetail("capexDetail.totalCharges",
					"The total charges must equal the approval amount."));
		}
	}

}
