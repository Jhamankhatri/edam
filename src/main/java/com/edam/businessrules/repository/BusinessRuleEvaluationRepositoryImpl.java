package com.edam.businessrules.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessRuleEvaluationRepositoryImpl implements BusinessRuleEvaluationRepository {

	/**
	 * SQL lives here, not in services. The query joins rule_set → published version →
	 * active rules, ordered by priority so the evaluator sees rules in deterministic order.
	 * A partial unique index on (rule_set_id) WHERE status='PUBLISHED' guarantees at most
	 * one PUBLISHED version per set.
	 */
	private static final String ACTIVE_RULES_SQL = """
			select r.id, r.rule_code, s.rule_set_code, v.version_number, r.priority,
			       r.condition_definition::text, r.action_definition::text
			  from business_rules r
			  join business_rule_versions v on v.id = r.rule_version_id
			  join business_rule_sets    s on s.id = v.rule_set_id
			 where s.is_active = true
			   and s.module = ?
			   and s.rule_set_code = ?
			   and v.version_status = 'PUBLISHED'
			   and (v.effective_from is null or v.effective_from <= now())
			   and (v.effective_to   is null or v.effective_to   >  now())
			   and r.is_active = true
			 order by r.priority asc, r.id asc
			""";

	private static final String ACTIVE_RULES_MODULE_SQL = """
			select r.id, r.rule_code, s.rule_set_code, v.version_number, r.priority,
			       r.condition_definition::text, r.action_definition::text
			  from business_rules r
			  join business_rule_versions v on v.id = r.rule_version_id
			  join business_rule_sets    s on s.id = v.rule_set_id
			 where s.is_active = true
			   and s.module = ?
			   and v.version_status = 'PUBLISHED'
			   and (v.effective_from is null or v.effective_from <= now())
			   and (v.effective_to   is null or v.effective_to   >  now())
			   and r.is_active = true
			 order by s.rule_set_code, r.priority asc, r.id asc
			""";

	private final JdbcTemplate jdbc;

	public BusinessRuleEvaluationRepositoryImpl(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ActiveRule> loadActiveRules(String module, String ruleSetCode) {
		return this.jdbc.query(ACTIVE_RULES_SQL,
				ps -> { ps.setString(1, module); ps.setString(2, ruleSetCode); },
				(rs, n) -> new ActiveRule(
						(UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
						rs.getInt(4), rs.getInt(5), rs.getString(6), rs.getString(7)));
	}

	@Override
	public List<ActiveRule> loadActiveRulesForModule(String module) {
		return this.jdbc.query(ACTIVE_RULES_MODULE_SQL,
				ps -> ps.setString(1, module),
				(rs, n) -> new ActiveRule(
						(UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
						rs.getInt(4), rs.getInt(5), rs.getString(6), rs.getString(7)));
	}
}
