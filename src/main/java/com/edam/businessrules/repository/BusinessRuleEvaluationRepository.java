package com.edam.businessrules.repository;

import java.util.List;
import java.util.UUID;

/**
 * Data-access operations required by the rule evaluator. Lives in the repository layer
 * because it issues SQL; services coordinate but do not hold native SQL.
 */
public interface BusinessRuleEvaluationRepository {

	/**
	 * Load every active rule from the currently PUBLISHED version of the named rule set
	 * (if the set itself is active and a PUBLISHED version exists). Returns the raw
	 * (code, priority, conditionJson, actionJson) tuples already ordered by priority
	 * ascending so the engine can evaluate in deterministic order.
	 */
	List<ActiveRule> loadActiveRules(String module, String ruleSetCode);

	/** Load every active rule from every active rule set in the given module. */
	List<ActiveRule> loadActiveRulesForModule(String module);

	record ActiveRule(UUID ruleId, String ruleCode, String ruleSetCode, int priority,
			String conditionJson, String actionJson) {}
}
