package com.edam.expenserequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiResponse.FieldErrorDetail;
import com.edam.common.exception.ValidationFailedException;
import com.edam.expenserequest.model.CapexRequestDetail;
import com.edam.expenserequest.model.ExpenseAllocation;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.model.ExpenseRequestItem;
import com.edam.expenserequest.model.OpexRequestDetail;
import com.edam.expenserequest.service.RequestValidationContext;
import com.edam.expenserequest.service.RequestValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The conditional-field rules, tested as decisions rather than through the web layer.
 *
 * <h2>Why these are pure unit tests</h2>
 * Every rule here is a function of the request and a small configuration context. A database-backed test
 * would prove the same thing more slowly and would obscure which rule failed, because a persistence error
 * and a validation error look alike in a stack trace. The integration tests elsewhere cover the wiring; these
 * cover the rules.
 *
 * <h2>What "valid" means in these tests</h2>
 * A baseline object is built once and each test perturbs exactly one thing, so a failure names one rule. The
 * baseline is deliberately a complete, submittable request: if the baseline itself were incomplete, every
 * assertion would pass for the wrong reason.
 */
class RequestValidatorTest {

	private static final UUID DEPARTMENT = UUID.randomUUID();

	private static final UUID CURRENCY = UUID.randomUUID();

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

	// ---------------------------------------------------------------------------------------------
	// Baseline builders
	// ---------------------------------------------------------------------------------------------

	/** A complete OPEX request: every submission rule satisfied. */
	private static ExpenseRequest validOpex() {
		ExpenseRequest request = header();
		OpexRequestDetail detail = new OpexRequestDetail(true, OpexRequestDetail.RecurrenceFrequency.MONTHLY);
		detail.setBudget(new BigDecimal("50000.0000"), "BUD-2026");
		request.replaceDetails(detail, null);
		request.replaceAllocations(List.of(allocation(new BigDecimal("1000.0000"))));
		return request;
	}

	/** A complete CAPEX request: every submission rule satisfied. */
	private static ExpenseRequest validCapex() {
		ExpenseRequest request = header();
		CapexRequestDetail detail = new CapexRequestDetail(true);
		detail.setFinancials(new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), new BigDecimal("1000.0000"));
		detail.setBudget(true, new BigDecimal("2000.0000"), null, null);
		detail.setAssetDetails("PURCHASE", "IT_EQUIPMENT", TODAY, TODAY.plusYears(1));
		request.replaceDetails(null, detail);
		request.replaceAllocations(List.of(allocation(new BigDecimal("1000.0000"))));
		request.replaceItems(List.of(new ExpenseRequestItem("Laptop", BigDecimal.ONE, new BigDecimal("1000.0000"),
				null)));
		return request;
	}

	private static ExpenseRequest header() {
		ExpenseRequest request = new ExpenseRequest("TRAVEL-202611-000001", UUID.randomUUID());
		request.setClassification(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID());
		request.setPlacement(DEPARTMENT, null);
		request.setMoney(new BigDecimal("1000.0000"), CURRENCY, new BigDecimal("1000.0000"), null);
		request.setJustification("Approved travel for the audit visit.");
		return request;
	}

	private static ExpenseAllocation allocation(BigDecimal amount) {
		return new ExpenseAllocation(DEPARTMENT, null, "CC-100", amount, new BigDecimal("100.0000"));
	}

	/**
	 * An OPEX context with nothing optional selected.
	 *
	 * <p>Child-row facts (quotation count, item count, invoice total) are deliberately not passed here: the
	 * validator reads them from the request it is given. An earlier version duplicated those counts in the
	 * context, and the tests caught the resulting contradiction — a request with its items cleared still
	 * passed "items are mandatory" because the context still claimed one item.
	 */
	private static RequestValidationContext opexContext() {
		return contextOf("OPEX", "OPEX");
	}

	/** A CAPEX context for the eligible department. */
	private static RequestValidationContext capexContext() {
		return contextOf("CAPEX", "CAPEX");
	}

	/** A context with no options selected and no documents attached. */
	private static RequestValidationContext contextOf(String expenseType, String requestTypeCode) {
		return new RequestValidationContext(expenseType, requestTypeCode, true, CURRENCY, false, false, false,
				false, false, false, false, TODAY);
	}

	/** An OPEX context with the named conditional options selected. */
	private static RequestValidationContext contextWithOptions(boolean threeQuotations, boolean alreadyApproved,
			boolean taxExempt, boolean grnRequired) {
		return new RequestValidationContext("OPEX", "OPEX", true, CURRENCY, threeQuotations, alreadyApproved,
				taxExempt, grnRequired, false, false, false, TODAY);
	}

	private static List<FieldErrorDetail> errorsOf(ExpenseRequest request, RequestValidationContext context) {
		try {
			RequestValidator.validate(request, context, true);
			return List.of();
		}
		catch (ValidationFailedException ex) {
			return ex.getFieldErrors();
		}
	}

	private static List<String> fieldsOf(ExpenseRequest request, RequestValidationContext context) {
		return errorsOf(request, context).stream().map(FieldErrorDetail::field).toList();
	}

	// ---------------------------------------------------------------------------------------------
	// Baseline: the rules accept a complete request
	// ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a complete OPEX request passes submission validation")
	void completeOpexIsValid() {
		ExpenseRequest request = validOpex();
		RequestValidationContext context = opexContext();
		assertThatCode(() -> RequestValidator.validate(request, context, true)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("a complete CAPEX request passes submission validation")
	void completeCapexIsValid() {
		ExpenseRequest request = validCapex();
		RequestValidationContext context = capexContext();
		assertThatCode(() -> RequestValidator.validate(request, context, true)).doesNotThrowAnyException();
	}

	// ---------------------------------------------------------------------------------------------
	// Required fields
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("required fields")
	class RequiredFields {

		@Test
		@DisplayName("a missing category is reported")
		void missingCategory() {
			ExpenseRequest request = validOpex();
			request.setClassification(UUID.randomUUID(), null, null, UUID.randomUUID());
			assertThat(fieldsOf(request, opexContext())).contains("expenseCategoryId");
		}

		@Test
		@DisplayName("a missing nature is reported")
		void missingNature() {
			ExpenseRequest request = validOpex();
			request.setClassification(UUID.randomUUID(), UUID.randomUUID(), null, null);
			assertThat(fieldsOf(request, opexContext())).contains("expenseNatureId");
		}

		@Test
		@DisplayName("a zero amount is rejected")
		void zeroAmount() {
			ExpenseRequest request = validOpex();
			request.setMoney(BigDecimal.ZERO, CURRENCY, BigDecimal.ZERO, null);
			assertThat(fieldsOf(request, opexContext())).contains("requestedAmount");
		}

		@Test
		@DisplayName("a negative amount is rejected")
		void negativeAmount() {
			ExpenseRequest request = validOpex();
			request.setMoney(new BigDecimal("-1.0000"), CURRENCY, BigDecimal.ZERO, null);
			assertThat(fieldsOf(request, opexContext())).contains("requestedAmount");
		}

		@Test
		@DisplayName("a missing department is reported, because routing depends on it")
		void missingDepartment() {
			ExpenseRequest request = validOpex();
			request.setPlacement(null, null);
			assertThat(fieldsOf(request, opexContext())).contains("departmentId");
		}

		@Test
		@DisplayName("a submission with no allocation is rejected")
		void noAllocationOnSubmit() {
			ExpenseRequest request = validOpex();
			request.replaceAllocations(List.of());
			assertThat(fieldsOf(request, opexContext())).contains("allocations");
		}

		@Test
		@DisplayName("a draft with no allocation is accepted")
		void noAllocationOnDraft() {
			ExpenseRequest request = validOpex();
			request.replaceAllocations(List.of());
			RequestValidationContext context = opexContext();
			assertThatCode(() -> RequestValidator.validate(request, context, false)).doesNotThrowAnyException();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// OPEX conditional rules
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("OPEX conditional rules")
	class OpexRules {

		@Test
		@DisplayName("a non-recurring expense without justification is rejected")
		void nonRecurringNeedsJustification() {
			ExpenseRequest request = header();
			OpexRequestDetail detail = new OpexRequestDetail(false, null);
			request.replaceDetails(detail, null);
			request.replaceAllocations(List.of(allocation(new BigDecimal("1000.0000"))));
			assertThat(fieldsOf(request, opexContext())).contains("opexDetail.nonRecurringJustification");
		}

		@Test
		@DisplayName("a non-recurring expense with justification is accepted")
		void nonRecurringWithJustification() {
			ExpenseRequest request = header();
			OpexRequestDetail detail = new OpexRequestDetail(false, null);
			detail.setJustificationFields("One-off regulatory filing fee.", null);
			request.replaceDetails(detail, null);
			request.replaceAllocations(List.of(allocation(new BigDecimal("1000.0000"))));
			assertThat(fieldsOf(request, opexContext())).doesNotContain("opexDetail.nonRecurringJustification");
		}

		@Test
		@DisplayName("a recurring expense without a cadence is rejected")
		void recurringNeedsFrequency() {
			ExpenseRequest request = header();
			// The entity clears a frequency on a non-recurring expense; here the flag is set with no cadence,
			// which is the one inconsistent state the entity permits a caller to build.
			OpexRequestDetail detail = new OpexRequestDetail(true, null);
			request.replaceDetails(detail, null);
			request.replaceAllocations(List.of(allocation(new BigDecimal("1000.0000"))));
			assertThat(fieldsOf(request, opexContext())).contains("opexDetail.recurrenceFrequency");
		}

		@Test
		@DisplayName("a supplier frequency on a non-recurring expense is discarded, not stored inconsistently")
		void frequencyDiscardedWhenNotRecurring() {
			OpexRequestDetail detail = new OpexRequestDetail(false, OpexRequestDetail.RecurrenceFrequency.ANNUAL);
			assertThat(detail.getRecurrenceFrequency()).isNull();
		}

		@Test
		@DisplayName("no quotation is rejected: quotation 1 is always required")
		void needsOneQuotation() {
			ExpenseRequest request = validOpex();
			request.replaceQuotations(List.of());
			assertThat(fieldsOf(request, opexContext())).contains("quotations");
		}

		@Test
		@DisplayName("declaring three quotations but supplying two is rejected")
		void threeQuotationsDeclaredButTwoSupplied() {
			ExpenseRequest request = validOpex();
			request.replaceQuotations(List.of(
					new com.edam.expenserequest.model.ExpenseQuotation(null, "Q-1", TODAY,
							new BigDecimal("1000.0000"), CURRENCY, null),
					new com.edam.expenserequest.model.ExpenseQuotation(null, "Q-2", TODAY,
							new BigDecimal("1000.0000"), CURRENCY, null)));
			RequestValidationContext context = contextWithOptions(true, false, false, false);
			assertThat(fieldsOf(request, context)).contains("quotations");
		}

		@Test
		@DisplayName("declaring three quotations and supplying three is accepted")
		void threeQuotationsSupplied() {
			ExpenseRequest request = validOpex();
			request.replaceQuotations(List.of(
					new com.edam.expenserequest.model.ExpenseQuotation(null, "Q-1", TODAY,
							new BigDecimal("1000.0000"), CURRENCY, null),
					new com.edam.expenserequest.model.ExpenseQuotation(null, "Q-2", TODAY,
							new BigDecimal("1000.0000"), CURRENCY, null),
					new com.edam.expenserequest.model.ExpenseQuotation(null, "Q-3", TODAY,
							new BigDecimal("1000.0000"), CURRENCY, null)));
			RequestValidationContext context = contextWithOptions(true, false, false, false);
			assertThat(fieldsOf(request, context)).doesNotContain("quotations");
		}

		@Test
		@DisplayName("an already-approved expense without its document is rejected")
		void alreadyApprovedNeedsDocument() {
			RequestValidationContext context = contextWithOptions(false, true, false, false);
			assertThat(fieldsOf(validOpex(), context)).contains("documents");
		}

		@Test
		@DisplayName("a tax exemption without its certificate is rejected")
		void taxExemptNeedsCertificate() {
			RequestValidationContext context = contextWithOptions(false, false, true, false);
			assertThat(fieldsOf(validOpex(), context)).contains("documents");
		}

		@Test
		@DisplayName("a required GRN that is missing is rejected")
		void grnNeedsDocument() {
			RequestValidationContext context = contextWithOptions(false, false, false, true);
			assertThat(fieldsOf(validOpex(), context)).contains("documents");
		}

		@Test
		@DisplayName("the conditional document rules do not block saving a draft")
		void draftMayLackDocuments() {
			ExpenseRequest request = validOpex();
			request.replaceQuotations(List.of());
			RequestValidationContext context = new RequestValidationContext("OPEX", "OPEX", true, CURRENCY,
					true, true, true, true, false, false, false, TODAY);
			assertThatCode(() -> RequestValidator.validate(request, context, false)).doesNotThrowAnyException();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// CAPEX conditional rules
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("CAPEX conditional rules")
	class CapexRules {

		@Test
		@DisplayName("a CAPEX request without an item is rejected")
		void itemsMandatory() {
			ExpenseRequest request = validCapex();
			request.replaceItems(List.of());
			assertThat(fieldsOf(request, capexContext())).contains("items");
		}

		@Test
		@DisplayName("a CAPEX request without a sales tax authority is rejected")
		void staMandatory() {
			ExpenseRequest request = validCapex();
			request.replaceStaDetails(List.of());
			assertThat(fieldsOf(request, capexContext())).contains("staDetails");
		}

		@Test
		@DisplayName("a CAPEX request without a request type is rejected")
		void requestTypeMandatory() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setAssetDetails(null, null, TODAY, TODAY.plusYears(1));
			assertThat(fieldsOf(request, capexContext())).contains("capexDetail.requestType");
		}

		@Test
		@DisplayName("an invoice total that differs from the approval amount is rejected")
		void invoiceTotalMustEqualApprovalAmount() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setFinancials(new BigDecimal("900.0000"), new BigDecimal("1000.0000"),
					new BigDecimal("1000.0000"));
			assertThat(fieldsOf(request, capexContext())).contains("capexDetail.invoiceTotal");
		}

		@Test
		@DisplayName("total charges that differ from the approval amount are rejected")
		void totalChargesMustEqualApprovalAmount() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setFinancials(new BigDecimal("1000.0000"), new BigDecimal("1001.0000"),
					new BigDecimal("1000.0000"));
			assertThat(fieldsOf(request, capexContext())).contains("capexDetail.totalCharges");
		}

		@Test
		@DisplayName("equal figures with different scales are accepted: 1000.00 equals 1000.0000")
		void equalityIgnoresScale() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setFinancials(new BigDecimal("1000.00"), new BigDecimal("1000.00000"),
					new BigDecimal("1000.0"));
			RequestValidationContext context = capexContext();
			assertThatCode(() -> RequestValidator.validate(request, context, true)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("an unbudgeted request without a reason is rejected")
		void unbudgetedNeedsReason() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setBudget(false, null, null, null);
			assertThat(fieldsOf(request, capexContext())).contains("capexDetail.unbudgetedReason");
		}

		@Test
		@DisplayName("an unbudgeted request with a reason is accepted")
		void unbudgetedWithReason() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setBudget(false, null, "Emergency generator replacement.", null);
			assertThat(fieldsOf(request, capexContext())).doesNotContain("capexDetail.unbudgetedReason");
		}

		@Test
		@DisplayName("a budgeted request above its budget without an explanation is rejected")
		void overBudgetNeedsReason() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setBudget(true, new BigDecimal("500.0000"), null, null);
			assertThat(fieldsOf(request, capexContext())).contains("capexDetail.overBudgetReason");
		}

		@Test
		@DisplayName("a budgeted request within its budget needs no over-budget reason")
		void withinBudgetNeedsNoReason() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setBudget(true, new BigDecimal("5000.0000"), null, null);
			assertThat(fieldsOf(request, capexContext())).doesNotContain("capexDetail.overBudgetReason");
		}

		@Test
		@DisplayName("a warranty date in the past is rejected")
		void warrantyDateNotInPast() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setAssetDetails("PURCHASE", "IT", TODAY.minusDays(1), TODAY.plusYears(1));
			assertThat(fieldsOf(request, capexContext())).contains("capexDetail.warrantyStartDate");
		}

		@Test
		@DisplayName("a warranty starting today is accepted")
		void warrantyStartingTodayIsAccepted() {
			ExpenseRequest request = validCapex();
			request.capexDetail().setAssetDetails("PURCHASE", "IT", TODAY, TODAY.plusYears(1));
			assertThat(fieldsOf(request, capexContext())).doesNotContain("capexDetail.warrantyStartDate");
		}

		@Test
		@DisplayName("a department not configured for CAPEX is rejected")
		void ineligibleDepartment() {
			RequestValidationContext ineligible = new RequestValidationContext("CAPEX", "CAPEX", false, CURRENCY,
					false, false, false, false, false, false, false, TODAY);
			assertThat(fieldsOf(validCapex(), ineligible)).contains("departmentId");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Cross-classification rules
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("classification and amount rules")
	class ClassificationRules {

		@Test
		@DisplayName("a CAPEX category on an OPEX request type is rejected")
		void categoryMustMatchRequestType() {
			RequestValidationContext mismatch = contextOf("CAPEX", "OPEX");
			assertThat(fieldsOf(validOpex(), mismatch)).contains("expenseCategoryId");
		}

		@Test
		@DisplayName("a BOTH category is usable on either request type")
		void bothCategoryMatches() {
			RequestValidationContext both = contextOf("BOTH", "OPEX");
			assertThat(fieldsOf(validOpex(), both)).doesNotContain("expenseCategoryId");
		}

		@Test
		@DisplayName("allocations exceeding the requested amount are rejected")
		void allocationsCannotExceedRequest() {
			ExpenseRequest request = validOpex();
			request.replaceAllocations(List.of(allocation(new BigDecimal("600.0000")),
					allocation(new BigDecimal("600.0000"))));
			assertThat(fieldsOf(request, opexContext())).contains("allocations");
		}

		@Test
		@DisplayName("allocations equal to the requested amount are accepted")
		void allocationsMayEqualRequest() {
			ExpenseRequest request = validOpex();
			request.replaceAllocations(List.of(allocation(new BigDecimal("600.0000")),
					allocation(new BigDecimal("400.0000"))));
			assertThat(fieldsOf(request, opexContext())).doesNotContain("allocations");
		}

		@Test
		@DisplayName("percentages above 100 in total are rejected")
		void percentagesCannotExceedHundred() {
			ExpenseRequest request = validOpex();
			request.replaceAllocations(List.of(
					new ExpenseAllocation(DEPARTMENT, null, null, new BigDecimal("500.0000"),
							new BigDecimal("60.0000")),
					new ExpenseAllocation(DEPARTMENT, null, null, new BigDecimal("500.0000"),
							new BigDecimal("60.0000"))));
			assertThat(fieldsOf(request, opexContext())).contains("allocations");
		}

		@Test
		@DisplayName("an invoice total that differs from the requested amount is rejected")
		void invoiceTotalMustEqualRequest() {
			ExpenseRequest request = validOpex();
			request.replaceInvoices(List.of(new com.edam.expenserequest.model.ExpenseInvoice("INV-1", TODAY,
					new BigDecimal("999.0000"), CURRENCY, null, null)));
			assertThat(fieldsOf(request, opexContext())).contains("invoices");
		}

		@Test
		@DisplayName("an invoice total equal to the requested amount is accepted")
		void invoiceTotalMatchingRequest() {
			ExpenseRequest request = validOpex();
			request.replaceInvoices(List.of(new com.edam.expenserequest.model.ExpenseInvoice("INV-1", TODAY,
					new BigDecimal("1000.0000"), CURRENCY, null, null)));
			assertThat(fieldsOf(request, opexContext())).doesNotContain("invoices");
		}

		@Test
		@DisplayName("all violations are reported, not only the first")
		void reportsEveryViolation() {
			ExpenseRequest request = header();
			request.setClassification(null, null, null, null);
			request.setPlacement(null, null);
			request.setMoney(BigDecimal.ZERO, null, null, null);
			List<String> fields = fieldsOf(request, opexContext());
			assertThat(fields).contains("expenseCategoryId", "expenseNatureId", "departmentId", "requestedAmount",
					"allocations", "opexDetail");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Line-item arithmetic
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("line-item arithmetic")
	class ItemArithmetic {

		@Test
		@DisplayName("the total is quantity x unit price, rounded to four places")
		void totalIsComputed() {
			ExpenseRequestItem item = new ExpenseRequestItem("Cable", new BigDecimal("3.0000"),
					new BigDecimal("33.3333"), null);
			assertThat(item.getTotalAmount()).isEqualByComparingTo("99.9999");
		}

		@Test
		@DisplayName("rounding is half-up, matching PostgreSQL's round(numeric, 4)")
		void roundingIsHalfUp() {
			ExpenseRequestItem item = new ExpenseRequestItem("Unit", new BigDecimal("0.33335"),
					new BigDecimal("1.0000"), null);
			// 0.33335 at four places is 0.3334 under half-up; the scale is always exactly four.
			assertThat(item.getTotalAmount()).isEqualByComparingTo("0.3334");
			assertThat(item.getTotalAmount().scale()).isEqualTo(4);
		}

		@Test
		@DisplayName("a zero quantity is refused, matching the schema CHECK")
		void zeroQuantityRefused() {
			assertThatThrownBy(() -> new ExpenseRequestItem("x", BigDecimal.ZERO, BigDecimal.ONE, null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a negative unit price is refused")
		void negativeUnitPriceRefused() {
			assertThatThrownBy(() -> new ExpenseRequestItem("x", BigDecimal.ONE, new BigDecimal("-1"), null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a negative quantity is refused")
		void negativeQuantityRefused() {
			assertThatThrownBy(() -> new ExpenseRequestItem("x", new BigDecimal("-2"), BigDecimal.ONE, null))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Aggregated budget arithmetic
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("CAPEX budget summary arithmetic")
	class BudgetArithmetic {

		@Test
		@DisplayName("available is allocated minus consumed, matching the schema CHECK")
		void availableIsDerived() {
			var summary = new com.edam.expenserequest.model.CapexBudgetSummary("BUD-1", new BigDecimal("10000.0000"),
					new BigDecimal("2500.0000"));
			assertThat(summary.getAvailableAmount()).isEqualByComparingTo("7500.0000");
		}

		@Test
		@DisplayName("a summary cannot be constructed with an inconsistent availability")
		void inconsistentAvailabilityIsUnrepresentable() {
			var summary = new com.edam.expenserequest.model.CapexBudgetSummary("BUD-2", new BigDecimal("100.0000"),
					new BigDecimal("0.0000"));
			// The whole point: there is no constructor that accepts all three, so drift is impossible.
			assertThat(summary.getAvailableAmount()).isEqualByComparingTo(summary.getAllocatedAmount()
				.subtract(summary.getConsumedAmount()));
		}

		@Test
		@DisplayName("several summaries sum without floating-point error")
		void sumsExactly() {
			List<com.edam.expenserequest.model.CapexBudgetSummary> rows = new ArrayList<>();
			rows.add(new com.edam.expenserequest.model.CapexBudgetSummary("A", new BigDecimal("0.1000"),
					new BigDecimal("0.0000")));
			rows.add(new com.edam.expenserequest.model.CapexBudgetSummary("B", new BigDecimal("0.2000"),
					new BigDecimal("0.0000")));
			BigDecimal total = rows.stream().map(com.edam.expenserequest.model.CapexBudgetSummary::getAvailableAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
			assertThat(total).isEqualByComparingTo("0.3000");
		}
	}

}
