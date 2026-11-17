package com.edam.businessrules.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.edam.common.audit.AuditableEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One rule inside a {@link BusinessRuleVersion}: a declarative condition (JSON object
 * in closed grammar) paired with an action (JSON object in closed grammar) and a
 * priority. The evaluator only reads rules from PUBLISHED versions whose parent
 * rule set is active.
 */
@Entity
@Table(name = "business_rules")
public class BusinessRule extends AuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rule_version_id", nullable = false, updatable = false)
	private BusinessRuleVersion ruleVersion;

	@Column(name = "rule_code", nullable = false, length = 64)
	private String code;

	@Column(name = "rule_name", nullable = false, length = 200)
	private String name;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "condition_definition", nullable = false, columnDefinition = "jsonb")
	private String conditionDefinition;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "action_definition", nullable = false, columnDefinition = "jsonb")
	private String actionDefinition;

	@Column(name = "priority", nullable = false)
	private int priority = 100;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	public BusinessRuleVersion getRuleVersion() { return this.ruleVersion; }
	public String getCode() { return this.code; }
	public String getName() { return this.name; }
	public Object getConditionDefinition() { return this.conditionDefinition; }
	public Object getActionDefinition() { return this.actionDefinition; }
	public int getPriority() { return this.priority; }
	public boolean isActive() { return this.active; }
	public long getVersion() { return this.version; }

	public void setRuleVersion(BusinessRuleVersion v) { this.ruleVersion = v; }
	public void setCode(String c) { this.code = c; }
	public void setName(String n) { this.name = n; }
	public void setConditionDefinition(Object c) { this.conditionDefinition = c; }
	public void setActionDefinition(Object a) { this.actionDefinition = a; }
	public void setPriority(int p) { this.priority = p; }
	public void setActive(boolean a) { this.active = a; }
}
