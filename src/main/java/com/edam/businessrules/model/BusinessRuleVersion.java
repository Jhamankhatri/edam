package com.edam.businessrules.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.edam.common.audit.AuditableEntity;

/**
 * One numbered version of a {@link BusinessRuleSet}. DRAFT versions hold editable
 * {@link BusinessRule}s; publishing freezes the rule set into a PUBLISHED version
 * whose rules are immutable. RETIRED versions are retained for audit and so that
 * historical evaluations can identify which version was in force.
 */
@Entity
@Table(name = "business_rule_versions")
public class BusinessRuleVersion extends AuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rule_set_id", nullable = false, updatable = false)
	private BusinessRuleSet ruleSet;

	@Column(name = "version_number", nullable = false)
	private int versionNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "version_status", nullable = false, length = 20)
	private RuleVersionStatus status = RuleVersionStatus.DRAFT;

	@Column(name = "effective_from")
	private Instant effectiveFrom;

	@Column(name = "effective_to")
	private Instant effectiveTo;

	@Column(name = "published_by")
	private UUID publishedBy;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@OneToMany(mappedBy = "ruleVersion", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("priority ASC, code ASC")
	private final List<BusinessRule> rules = new ArrayList<>();

	public BusinessRuleSet getRuleSet() { return this.ruleSet; }
	public int getVersionNumber() { return this.versionNumber; }
	public RuleVersionStatus getStatus() { return this.status; }
	public Instant getEffectiveFrom() { return this.effectiveFrom; }
	public Instant getEffectiveTo() { return this.effectiveTo; }
	public UUID getPublishedBy() { return this.publishedBy; }
	public Instant getPublishedAt() { return this.publishedAt; }
	public long getVersion() { return this.version; }
	public List<BusinessRule> getRules() { return this.rules; }

	public void setRuleSet(BusinessRuleSet ruleSet) { this.ruleSet = ruleSet; }
	public void setVersionNumber(int n) { this.versionNumber = n; }
	public void setStatus(RuleVersionStatus s) { this.status = s; }
	public void setEffectiveFrom(Instant t) { this.effectiveFrom = t; }
	public void setEffectiveTo(Instant t) { this.effectiveTo = t; }

	public void markPublished(UUID by, Instant at) {
		this.status = RuleVersionStatus.PUBLISHED;
		this.publishedBy = by;
		this.publishedAt = at;
		this.effectiveFrom = (this.effectiveFrom == null) ? at : this.effectiveFrom;
	}

	public void markRetired(Instant at) {
		this.status = RuleVersionStatus.RETIRED;
		this.effectiveTo = at;
	}

	public BusinessRule addRule(String code, String name, String condition, String action, int priority) {
		BusinessRule r = new BusinessRule();
		r.setRuleVersion(this);
		r.setCode(code);
		r.setName(name);
		r.setConditionDefinition(condition);
		r.setActionDefinition(action);
		r.setPriority(priority);
		r.setActive(true);
		this.rules.add(r);
		return r;
	}
}
