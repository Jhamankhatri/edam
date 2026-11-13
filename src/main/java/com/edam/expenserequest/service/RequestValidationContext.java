package com.edam.expenserequest.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Configuration facts a request's validation needs, resolved once per save.
 *
 * <h2>Why a context object rather than lookups inside the validator</h2>
 * The conditional rules ("a recurring expense needs a frequency", "CAPEX needs item rows") depend on
 * configuration: which classification the category belongs to, which request type is OPEX, whether the
 * department is CAPEX-eligible. Resolving those inside the validator would make it a repository client and
 * would make every rule untestable without a database. Collecting them here keeps the validator a pure
 * function of its inputs — which is what lets the rules be unit-tested exhaustively.
 *
 * @param expenseType the category's classification ({@code OPEX}, {@code CAPEX} or {@code BOTH})
 * @param requestTypeCode the configured request-type code, used to decide OPEX-vs-CAPEX detail
 * @param capexDepartmentEligible whether the requesting department may raise CAPEX (OD-P2-02)
 * @param baseCurrencyId the installation's base currency
 * @param localCurrencyId the currency the request is stated in
 * @param quoteCount number of quotation rows supplied
 * @param threeQuotationsSelected whether the requester attested that three quotations are attached
 * @param alreadyApproved whether the expense was already approved elsewhere
 * @param taxExempt whether a tax exemption is claimed
 * @param grnRequired whether a goods-receipt note is required
 * @param hasApprovedDocument whether the approved-expense document is attached
 * @param hasTaxExemptionDocument whether the exemption certificate is attached
 * @param hasGrnDocument whether the GRN is attached
 * @param today the validation date, injected so "the past" is testable
 * @param itemCount number of item rows
 * @param allocationTotal how much of the amount is allocated to departments
 * @param allocatedPercentage how much of the amount is allocated by percentage
 * @param invoiceTotal sum of invoice rows, or null when there are none
 */
public record RequestValidationContext(String expenseType, String requestTypeCode, boolean capexDepartmentEligible,
		UUID baseCurrencyId, UUID localCurrencyId, int quoteCount, boolean threeQuotationsSelected,
		boolean alreadyApproved, boolean taxExempt, boolean grnRequired, boolean hasApprovedDocument,
		boolean hasTaxExemptionDocument, boolean hasGrnDocument, LocalDate today, int itemCount,
		BigDecimal allocationTotal, BigDecimal allocatedPercentage, BigDecimal invoiceTotal, int staDetailCount,
		int previousSummaryCount) {

	public RequestValidationContext {
		// A null list-like value must never silently satisfy a "present" check.
		quoteCount = Math.max(quoteCount, 0);
		itemCount = Math.max(itemCount, 0);
	}

	/** True when the request is CAPEX by its configured request type. */
	public boolean isCapex() {
		return this.requestTypeCode != null && this.requestTypeCode.toUpperCase(java.util.Locale.ROOT).contains("CAPEX");
	}

	/** True when the category may be used for this request type. */
	public boolean categoryMatchesRequestType() {
		if (this.expenseType == null) {
			return true;
		}
		String type = this.expenseType.toUpperCase(java.util.Locale.ROOT);
		if ("BOTH".equals(type)) {
			return true;
		}
		return isCapex() ? "CAPEX".equals(type) : "OPEX".equals(type);
	}

}
