package com.edam.configplane;

/**
 * The two expense natures the product separates (BRD FM-01 vs FM-04, FM-13).
 *
 * <p>Deliberately an enum: unlike permissions or categories, OPEX and CAPEX are <em>product
 * structure</em>, not institution configuration. A third nature would be a different product
 * requirement, not a configuration value.
 */
public enum ExpenseNature {

	/** Head Office operating expenditure. */
	OPEX,

	/** Head Office capital expenditure. */
	CAPEX

}
