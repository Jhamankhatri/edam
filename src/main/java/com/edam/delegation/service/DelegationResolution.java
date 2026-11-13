package com.edam.delegation.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.edam.delegation.model.DelegationApproverStep;

/**
 * The outcome of resolving the delegation matrix for one request (Module E).
 *
 * <h2>Why a record rather than the rule entity</h2>
 * The workflow engine needs the chain and the identity of the rule that produced it (for audit and for
 * "why did this route here?" support questions), not a live JPA entity that may change underneath it. A
 * resolved route is a value: it is computed once, at submission, and then pinned.
 *
 * @param ruleId the rule that matched, for audit and traceability
 * @param bandDescription human-readable band, e.g. {@code "100,000.00 – 500,000.00 PKR"} or
 * {@code "≥ 500,000.00 PKR"}
 * @param steps the approval chain in order
 */
public record DelegationResolution(UUID ruleId, String bandDescription, List<DelegationApproverStep> steps) {

	public DelegationResolution {
		steps = List.copyOf(steps);
	}

	/** True when the rule produced no approvers, which is a configuration error the caller must reject. */
	public boolean isEmpty() {
		return this.steps.isEmpty();
	}

	/** Describes a band for logs and for the "no route configured" message. */
	public static String describeBand(BigDecimal minimum, BigDecimal maximum, String currencyCode) {
		String floor = minimum.stripTrailingZeros().toPlainString();
		if (maximum == null) {
			return "≥ " + floor + " " + currencyCode;
		}
		return floor + " – " + maximum.stripTrailingZeros().toPlainString() + " " + currencyCode;
	}

	/** No route exists: never inferred, never defaulted to a single approver. */
	public static DelegationResolution none() {
		return new DelegationResolution(null, null, List.of());
	}

}
