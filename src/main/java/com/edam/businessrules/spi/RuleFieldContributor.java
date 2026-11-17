package com.edam.businessrules.spi;

import java.util.Set;

/**
 * Service Provider Interface for declaring which fields a module exposes to business rules
 * and which parameters each action type accepts. Modules that evaluate rules implement this
 * interface; the rule engine rejects any rule at publish time that references an unknown
 * field, an unknown action, or an action parameter the module does not declare.
 *
 * <p>Implementations are discovered as Spring beans. There is one contributor per module
 * (matched by {@link #moduleCode()}). The engine aggregates all contributors on startup.
 * Fields are declared as a fixed Set&lt;String&gt; so the whitelist is authoritative and there is
 * no path to reflect over arbitrary objects or application properties.
 *
 * <p>No contributor ⇒ no rules can be published for that module (fail closed). This avoids
 * the previous bug where {@code wildcard()} accepted any field name at publish time.
 */
public interface RuleFieldContributor {

	/** Uppercase module code this contributor owns (e.g. {@code EXPENSE}). Matched against
	 *  {@code business_rule_sets.module}. */
	String moduleCode();

	/** Whitelisted field names this module exposes to rule conditions. Only scalars from the
	 *  evaluation context are read; nested/reflective access is not representable. */
	Set<String> exposedFields();

	/** Whitelisted action types this module acts on, plus the parameter names each action
	 *  accepts. An action with no parameters is represented by an empty set. The engine
	 *  rejects actions containing extra undeclared parameters. */
	Set<String> allowedActionTypes();

	/** Parameter names allowed on a given action type. Return an empty set for parameterless
	 *  actions. */
	Set<String> allowedActionParameters(String actionType);
}
