package com.edam.expenserequest.rules;

import java.util.Map;
import java.util.Set;

import com.edam.businessrules.spi.RuleFieldContributor;

import org.springframework.stereotype.Component;

/**
 * Declares the field/action whitelist that expense-request rules may use. No rule can be
 * published against the EXPENSE module unless every field it reads and every action
 * parameter it emits is listed here. Adding a new field requires a code change + review —
 * configuration-driven additions would reopen the reflection/SQL/property-access holes the
 * engine is designed to close.
 *
 * <p>Fields are scalar context values populated by the expense-request submission flow when
 * it calls {@code BusinessRuleService.evaluate(...)}. They are read-only names in a fixed
 * Map; there is no reflection, EL, SpEL, or path traversal.
 */
@Component
public class ExpenseRequestRuleFieldContributor implements RuleFieldContributor {

	static final String MODULE = "EXPENSE";

	private static final Set<String> FIELDS = Set.of(
			"amount",               // base_currency_amount (BigDecimal)
			"requestedAmount",      // requested_amount in original currency (BigDecimal)
			"currency",             // currency code (String, e.g. "PKR")
			"expenseType",          // "OPEX" | "CAPEX"
			"categoryCode",         // expense_categories.code
			"subcategoryCode",      // expense_subcategories.code (may be null)
			"departmentId",         // requester's department UUID as String
			"branchId",             // branch UUID as String (may be null)
			"requesterId",          // users.id UUID as String
			"isOwnDepartment",      // Boolean
			"hasReceipt",           // Boolean — true if at least one attachment of type RECEIPT exists
			"descriptionLength",    // Integer
			"merchant"              // String (may be null)
	);

	private static final Set<String> ACTIONS = Set.of(
			"REQUIRE_APPROVAL_LEVEL", "REJECT_REQUEST", "WARN",
			"REQUIRE_ATTACHMENT", "SET_FLAG"
	);

	private static final Map<String, Set<String>> ACTION_PARAMS = Map.of(
			"REQUIRE_APPROVAL_LEVEL", Set.of("level"),
			"REJECT_REQUEST",          Set.of("reason"),
			"WARN",                    Set.of("message"),
			"REQUIRE_ATTACHMENT",      Set.of("attachmentType", "message"),
			"SET_FLAG",                Set.of("flag", "value")
	);

	@Override public String moduleCode() { return MODULE; }
	@Override public Set<String> exposedFields() { return FIELDS; }
	@Override public Set<String> allowedActionTypes() { return ACTIONS; }
	@Override public Set<String> allowedActionParameters(String actionType) {
		return ACTION_PARAMS.getOrDefault(actionType, Set.of());
	}
}
