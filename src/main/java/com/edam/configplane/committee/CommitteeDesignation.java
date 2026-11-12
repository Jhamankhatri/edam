package com.edam.configplane.committee;

/**
 * The institutional office a committee member holds (BRD FM-08).
 *
 * <p>The BRD singles out three offices with a "only one active holder" rule: CEO, CFO and Head of
 * Admin &amp; GSD (HOD). Those are modelled as <em>designation values</em>, while ordinary member
 * designations are free text, because the BRD also allows arbitrary designations in the member
 * pool. Putting the three special offices in code and everything else in data is the closest
 * faithful reading; a fully configurable "unique office" model would be inventing a rule the BRD
 * does not state.
 */
public enum CommitteeDesignation {

	/** Chief Executive Officer. Single active holder; may approve or revert but cannot reject. */
	CEO(true, false),

	/** Chief Financial Officer. Single active holder. */
	CFO(true, false),

	/** Head of Admin &amp; General Services Department. Single active holder. */
	HEAD_OF_ADMIN_GSD(true, false),

	/** An ordinary Expense Committee member or other designation. */
	MEMBER(false, false);

	private final boolean singleActiveHolder;

	private final boolean mayReject;

	CommitteeDesignation(boolean singleActiveHolder, boolean mayReject) {
		this.singleActiveHolder = singleActiveHolder;
		this.mayReject = mayReject;
	}

	/** True when the institution may have only one active member holding this office. */
	public boolean isSingleActiveHolder() {
		return this.singleActiveHolder;
	}

	/** Whether the office may reject a committee request. CEO explicitly may not (BRD §8.5). */
	public boolean mayReject() {
		return this.mayReject;
	}

}
