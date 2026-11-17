package com.edam.businessrules.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A named collection of {@link BusinessRuleVersion}s grouped by {@code module} (e.g.
 * {@code EXPENSE_APPROVAL}, {@code REQUEST_VALIDATION}). A rule set is the unit that
 * rule authors create and publish; the currently-published version is what the engine
 * evaluates.
 */
@Entity
@Table(name = "business_rule_sets")
public class BusinessRuleSet extends AuditableEntity {

	@Column(name = "rule_set_code", nullable = false, length = 64, updatable = false)
	private String code;

	@Column(name = "rule_set_name", nullable = false, length = 200)
	private String name;

	@Column(name = "module", nullable = false, length = 60)
	private String module;

	@Column(name = "description")
	private String description;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("versionNumber DESC")
	private final List<BusinessRuleVersion> versions = new ArrayList<>();

	public String getCode() { return this.code; }
	public String getName() { return this.name; }
	public String getModule() { return this.module; }
	public String getDescription() { return this.description; }
	public boolean isActive() { return this.active; }
	public long getVersion() { return this.version; }
	public List<BusinessRuleVersion> getVersions() { return this.versions; }

	public void setCode(String code) { this.code = code; }
	public void setName(String name) { this.name = name; }
	public void setModule(String module) { this.module = module; }
	public void setDescription(String description) { this.description = description; }
	public void setActive(boolean active) { this.active = active; }

	public BusinessRuleVersion draftVersion() {
		int next = this.versions.stream().mapToInt(BusinessRuleVersion::getVersionNumber).max().orElse(0) + 1;
		BusinessRuleVersion v = new BusinessRuleVersion();
		v.setRuleSet(this);
		v.setVersionNumber(next);
		v.setStatus(RuleVersionStatus.DRAFT);
		this.versions.add(v);
		return v;
	}
}
