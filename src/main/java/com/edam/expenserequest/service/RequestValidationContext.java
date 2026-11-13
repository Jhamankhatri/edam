package com.edam.expenserequest.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Configuration facts a request's validation needs, resolved once per save.
 *
 * <h2>What belongs here, and what emphatically does not</h2>
 * Only facts the request itself cannot know: which classification the configured category belongs to, which
 * request type is in play, whether the department may raise CAPEX, which currency is the base currency, and
 * which conditional options the requester selected. These come from configuration and from the payload.
 *
 * <p>Counts of child rows — how many items, quotations, tax rows or allocations — are deliberately
 * <em>absent</em>. The request already holds those rows, so a copy here would be a second source of truth
 * that can disagree with the aggregate it is meant to describe. An early version of this record carried
 * such counts, and the unit tests caught exactly that failure: a request with its items cleared still
 * passed the "items are mandatory" rule because the context still claimed one item. The validator now reads
 * the counts from the request.
 *
 * <h2>Options are payload facts, not configuration</h2>
 * "Three quotations attached", "already approved", "tax exempt" and "GRN required" describe what the
 * requester claimed on this request. They are captured at submission because the form is not otherwise
 * stored, and they are immutable thereafter.
 *
 * @param expenseType the category's classification ({@code OPEX}, {@code CAPEX} or {@code BOTH})
 * @param requestTypeCode the configured request-type code, used to decide OPEX-vs-CAPEX detail
 * @param capexDepartmentEligible whether the requesting department may raise CAPEX (OD-P2-07)
 * @param baseCurrencyId the installation's base currency
 * @param threeQuotationsSelected whether the requester attested that three quotations are attached
 * @param alreadyApproved whether the expense was already approved elsewhere
 * @param taxExempt whether a tax exemption is claimed
 * @param grnRequired whether a goods-receipt note is required
 * @param hasApprovedDocument whether an approved-expense document is attached
 * @param hasTaxExemptionDocument whether the exemption certificate is attached
 * @param hasGrnDocument whether the GRN is attached
 * @param today the validation date, injected so "the past" is testable
 */
public record RequestValidationContext(String expenseType, String requestTypeCode, boolean capexDepartmentEligible,
		UUID baseCurrencyId, boolean threeQuotationsSelected, boolean alreadyApproved,
		boolean taxExempt, boolean grnRequired, boolean hasApprovedDocument, boolean hasTaxExemptionDocument,
		boolean hasGrnDocument, LocalDate today) {

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
