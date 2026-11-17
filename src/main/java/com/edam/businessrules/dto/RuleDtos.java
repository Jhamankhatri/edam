package com.edam.businessrules.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.businessrules.model.RuleVersionStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class RuleDtos {

	private RuleDtos() {}

	public record RuleSetSummary(UUID id, String code, String name, String module, String description,
			boolean active, int publishedVersion, int draftVersion) {
	}

	public record CreateRuleSetRequest(
			@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,62}", message = "Code must be UPPER_SNAKE, 2-63 chars") String code,
			@NotBlank @Size(max = 200) String name,
			@NotBlank @Size(max = 60) String module,
			@Size(max = 2000) String description) {
	}

	public record UpdateRuleSetRequest(
			@Size(max = 200) String name,
			@Size(max = 60) String module,
			@Size(max = 2000) String description,
			Boolean active) {
	}

	public record RuleVersionSummary(UUID id, int versionNumber, RuleVersionStatus status,
			Instant effectiveFrom, Instant effectiveTo, Instant publishedAt, UUID publishedBy,
			int ruleCount) {
	}

	public record RuleVersionDetail(UUID id, int versionNumber, RuleVersionStatus status,
			Instant effectiveFrom, Instant effectiveTo, Instant publishedAt, UUID publishedBy,
			int ruleCount, List<RuleDetail> rules) {
	}

	public record RuleSummary(UUID id, String code, String name, int priority, boolean active) {
	}

	public record RuleDetail(UUID id, String code, String name, Map<String, Object> condition,
			Map<String, Object> action, int priority, boolean active) {
	}

	/** Upsert a rule inside a DRAFT version. If {@code id} is null, a new rule is created. */
	public record RuleUpsertRequest(
			UUID id,
			@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,62}") String code,
			@NotBlank @Size(max = 200) String name,
			Map<String, Object> condition,
			Map<String, Object> action,
			@PositiveOrZero Integer priority,
			Boolean active) {
	}

	public record PublishRequest(Instant effectiveFrom) {
	}
}
