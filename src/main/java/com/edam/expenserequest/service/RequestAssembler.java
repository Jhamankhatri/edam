package com.edam.expenserequest.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.expenserequest.dto.ExpenseRequestDtos.AllocationPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.BudgetSummaryPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.CapexDetailPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.CreateRequest;
import com.edam.expenserequest.dto.ExpenseRequestDtos.InvoicePayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.ItemPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.OpexDetailPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.PreviousSummaryPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.QuotationPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.StaPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.UpdateRequest;
import com.edam.expenserequest.model.CapexBudgetSummary;
import com.edam.expenserequest.model.CapexRequestDetail;
import com.edam.expenserequest.model.CapexStaDetail;
import com.edam.expenserequest.model.ExpenseAllocation;
import com.edam.expenserequest.model.ExpenseInvoice;
import com.edam.expenserequest.model.ExpensePreviousSummary;
import com.edam.expenserequest.model.ExpenseQuotation;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.model.ExpenseRequestItem;
import com.edam.expenserequest.model.OpexRequestDetail;
import com.edam.expenserequest.model.OpexRequestDetail.RecurrenceFrequency;
import com.edam.organization.repository.BranchRepository;
import com.edam.organization.repository.DepartmentRepository;

import org.springframework.stereotype.Component;

/**
 * Turns a transport payload into the request aggregate (Module G).
 *
 * <h2>Why this is separate from the service</h2>
 * The mapping is the largest and most mechanical part of creating a request, and it is also where a subtle
 * mistake is most costly: a misplaced field silently records the wrong amount under the right label. Keeping
 * it in one place, with no transaction and no repository reads beyond reference existence, means the service
 * can stay a readable statement of the lifecycle, and the mapping can be reviewed on its own.
 *
 * <h2>Reference existence is checked here, not deferred to the database</h2>
 * A department, branch, sub-category or tax authority that does not exist would fail at flush time with a
 * foreign-key violation, which the caller sees as a generic persistence error. Checking each reference up
 * front produces a field-level message naming what is wrong — and, because this runs before the transaction
 * does any work, nothing is left half-written.
 *
 * <h2>The detail type is decided by the payload, and only one may be present</h2>
 * Supplying both an OPEX and a CAPEX detail is not merged or preferred: it is refused. The aggregate holds at
 * most one detail row per type and the header's classification is derived from configuration, so a payload
 * carrying both is ambiguous about what the requester actually wants to raise.
 */
@Component
public class RequestAssembler {

	private final DepartmentRepository departments;

	private final BranchRepository branches;

	private final Clock clock;

	public RequestAssembler(DepartmentRepository departments, BranchRepository branches, Clock clock) {
		this.departments = departments;
		this.branches = branches;
		this.clock = clock;
	}

	/** Applies a create payload to a new header. */
	public void applyCreate(ExpenseRequest request, CreateRequest payload, String requestNumber, UUID requestedBy,
			UUID baseCurrencyId, UUID exchangeRateId, java.math.BigDecimal baseAmount) {
		request.setClassification(payload.requestTypeId(), payload.expenseCategoryId(),
				payload.expenseSubcategoryId(), payload.expenseNatureId());
		request.setPlacement(payload.departmentId(), payload.branchId());
		request.setMoney(payload.requestedAmount(), payload.currencyId(), baseAmount, exchangeRateId);
		request.setJustification(payload.justification());
		requireReferences(payload.departmentId(), payload.branchId(), payload.opex(), payload.capex());
		applyDetails(request, payload.opex(), payload.capex());
		applyChildren(request, payload.allocations(), payload.items(), payload.quotations(), payload.invoices(),
				payload.previousSummaries(), payload.staDetails(), payload.budgetSummaries());
	}

	/** Applies a replace payload to an existing, editable header. */
	public void applyUpdate(ExpenseRequest request, UpdateRequest payload, UUID baseCurrencyId,
			UUID exchangeRateId, java.math.BigDecimal baseAmount) {
		request.setClassification(payload.requestTypeId(), payload.expenseCategoryId(),
				payload.expenseSubcategoryId(), payload.expenseNatureId());
		request.setMoney(payload.requestedAmount(), payload.currencyId(), baseAmount, exchangeRateId);
		request.setJustification(payload.justification());
		requireReferences(request.getDepartmentId(), payload.branchId(), payload.opex(), payload.capex());
		applyDetails(request, payload.opex(), payload.capex());
		applyChildren(request, payload.allocations(), payload.items(), payload.quotations(), payload.invoices(),
				payload.previousSummaries(), payload.staDetails(), payload.budgetSummaries());
	}

	// ---------------------------------------------------------------------------------------------
	// Details
	// ---------------------------------------------------------------------------------------------

	private void applyDetails(ExpenseRequest request, OpexDetailPayload opex, CapexDetailPayload capex) {
		if (opex != null && capex != null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A request is either OPEX or CAPEX; supply one set of details, not both.");
		}
		if (opex != null) {
			OpexRequestDetail detail = new OpexRequestDetail(Boolean.TRUE.equals(opex.recurring()),
					parseFrequency(opex.recurrenceFrequency()));
			detail.setJustificationFields(opex.nonRecurringJustification(), opex.paymentTerms());
			detail.setHistoricalAmounts(opex.accruedAmount(), opex.previouslyApprovedAmount(), opex.ytdAmount(),
					opex.mtdAmount());
			detail.setBudget(opex.budgetAvailable(), opex.budgetReference());
			request.replaceDetails(detail, null);
			return;
		}
		if (capex != null) {
			CapexRequestDetail detail = new CapexRequestDetail(!Boolean.FALSE.equals(capex.budgeted()));
			detail.setFinancials(capex.invoiceTotal(), capex.totalCharges(), capex.approvalAmount());
			detail.setBudget(!Boolean.FALSE.equals(capex.budgeted()), capex.budgetAmount(), capex.unbudgetedReason(),
					capex.overBudgetReason());
			detail.setAssetDetails(capex.requestType(), capex.classification(), capex.warrantyStartDate(),
					capex.warrantyEndDate());
			request.replaceDetails(null, detail);
		}
	}

	/**
	 * Parses a cadence.
	 *
	 * <p>A supplied but unrecognised value is a client error and is reported as one. It is not silently
	 * dropped, because a recurring expense whose cadence was discarded is indistinguishable from one whose
	 * cadence was never asked for.
	 */
	private static RecurrenceFrequency parseFrequency(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return RecurrenceFrequency.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The recurrence frequency must be MONTHLY, QUARTERLY, HALF_YEARLY or ANNUAL.");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Child rows
	// ---------------------------------------------------------------------------------------------

	private void applyChildren(ExpenseRequest request, List<AllocationPayload> allocations, List<ItemPayload> items,
			List<QuotationPayload> quotations, List<InvoicePayload> invoices,
			List<PreviousSummaryPayload> previousSummaries, List<StaPayload> staDetails,
			List<BudgetSummaryPayload> budgetSummaries) {
		List<ExpenseAllocation> allocationRows = new ArrayList<>();
		if (allocations != null) {
			for (AllocationPayload payload : allocations) {
				if (!this.departments.existsById(payload.departmentId())) {
					throw new ResourceNotFoundException("Department", payload.departmentId());
				}
				requireBranch(payload.branchId());
				allocationRows.add(new ExpenseAllocation(payload.departmentId(), payload.branchId(),
						payload.costCenter(), payload.allocationAmount(), payload.allocationPercentage()));
			}
		}
		request.replaceAllocations(allocationRows);

		List<ExpenseRequestItem> itemRows = new ArrayList<>();
		if (items != null) {
			for (ItemPayload payload : items) {
				try {
					itemRows.add(new ExpenseRequestItem(payload.itemDescription(), payload.quantity(),
							payload.unitPrice(), payload.warrantyDate()));
				}
				catch (IllegalArgumentException ex) {
					// The entity refuses a non-positive quantity or a negative price, matching the schema's
					// CHECK. Translated into a field error so the caller learns which row is wrong.
					throw new BusinessException(ErrorCode.VALIDATION_FAILED,
							"An item must have a quantity greater than zero and a unit price that is not negative.");
				}
			}
		}
		request.replaceItems(itemRows);

		List<ExpenseQuotation> quotationRows = new ArrayList<>();
		if (quotations != null) {
			for (QuotationPayload payload : quotations) {
				quotationRows.add(new ExpenseQuotation(payload.vendorId(), payload.quotationNumber(),
						payload.quotationDate(), payload.quotationAmount(), payload.currencyId(),
						payload.mediaAssetId()));
			}
		}
		request.replaceQuotations(quotationRows);

		List<ExpenseInvoice> invoiceRows = new ArrayList<>();
		if (invoices != null) {
			for (InvoicePayload payload : invoices) {
				invoiceRows.add(new ExpenseInvoice(payload.invoiceNumber(), payload.invoiceDate(),
						payload.invoiceAmount(), payload.currencyId(), payload.vendorId(), payload.mediaAssetId()));
			}
		}
		request.replaceInvoices(invoiceRows);

		List<ExpensePreviousSummary> summaryRows = new ArrayList<>();
		if (previousSummaries != null) {
			for (PreviousSummaryPayload payload : previousSummaries) {
				summaryRows.add(new ExpensePreviousSummary(payload.period(), payload.previousAmount(),
						payload.approvedAmount(), payload.description()));
			}
		}
		request.replacePreviousSummaries(summaryRows);

		List<CapexStaDetail> staRows = new ArrayList<>();
		if (staDetails != null) {
			for (StaPayload payload : staDetails) {
				staRows.add(new CapexStaDetail(payload.taxAuthorityId(), payload.staReference(), payload.taxAmount(),
						payload.description()));
			}
		}
		request.replaceStaDetails(staRows);

		List<CapexBudgetSummary> budgetRows = new ArrayList<>();
		if (budgetSummaries != null) {
			for (BudgetSummaryPayload payload : budgetSummaries) {
				budgetRows.add(new CapexBudgetSummary(payload.budgetReference(), payload.allocatedAmount(),
						payload.consumedAmount()));
			}
		}
		request.replaceBudgetSummaries(budgetRows);
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private void requireReferences(UUID departmentId, UUID branchId, OpexDetailPayload opex,
			CapexDetailPayload capex) {
		if (departmentId != null && !this.departments.existsById(departmentId)) {
			throw new ResourceNotFoundException("Department", departmentId);
		}
		requireBranch(branchId);
	}

	private void requireBranch(UUID branchId) {
		if (branchId != null && !this.branches.existsById(branchId)) {
			throw new ResourceNotFoundException("Branch", branchId);
		}
	}

	/** Sum of quotation amounts, used by the OPEX quotation rules. */
	public static java.math.BigDecimal quotationTotal(List<ExpenseQuotation> quotations) {
		return quotations.stream()
			.map(ExpenseQuotation::getQuotationAmount)
			.filter(java.util.Objects::nonNull)
			.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
	}

	/** The instant the assembler stamps documents with. */
	public Instant now() {
		return Instant.now(this.clock);
	}

}
