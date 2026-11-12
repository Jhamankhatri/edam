package com.edam.workflow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.configplane.category.ApproverType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The parsed, validated step model of a workflow version (BRD §8.3).
 *
 * <h2>Why a typed model instead of evaluating the document</h2>
 * The workflow is configured by an administrator, and the user's constraint is explicit: no arbitrary
 * code, expressions or SQL may be executed from admin-configurable configuration. So the stored JSON is
 * <em>parsed into this closed model</em>, every value is checked against a known token (an
 * {@link ApproverType}, a step code pattern, a quorum bound), and only the resulting objects drive
 * behaviour. There is no expression language to escape, and an unknown approver token is a validation
 * error rather than a silent no-op.
 *
 * <h2>Document shape</h2>
 * <pre>{@code
 * {
 *   "steps": [
 *     { "code": "DEPARTMENT_HEAD", "label": "Department Head approval",
 *       "approver": "DEPARTMENT_HEAD", "sequence": 1, "quorum": 1,
 *       "skipIfAmountBelow": "0.00" },
 *     { "code": "COMMITTEE", "label": "Expense Committee",
 *       "approver": "EXPENSE_COMMITTEE", "sequence": 2, "quorum": 2 }
 *   ]
 * }
 * }</pre>
 *
 * <h2>Quorum</h2>
 * {@code quorum} is the number of recorded approvals required at that step. A step whose approver is
 * an Expense Committee level also carries the level's own minimum approvals; the engine takes the
 * larger of the two so a workflow author cannot weaken a committee rule the configuration plane
 * already enforces.
 */
public final class WorkflowSteps {

	/** Maximum steps: a workflow longer than this is a modelling error, not a business need. */
	public static final int MAX_STEPS = 20;

	private final List<Step> steps;

	private WorkflowSteps(List<Step> steps) {
		this.steps = List.copyOf(steps);
	}

	/**
	 * Parse and validate a workflow document.
	 *
	 * @throws BusinessException with {@link ErrorCode#INVALID_CONFIGURATION} when the document is not
	 * valid; the message names the offending step so an administrator can act on it
	 */
	public static WorkflowSteps parse(JsonMapper jsonMapper, String json) {
		if (json == null || json.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION, "The workflow definition is empty.");
		}
		JsonNode root;
		try {
			root = jsonMapper.readTree(json);
		}
		catch (RuntimeException ex) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The workflow definition is not valid JSON.");
		}
		JsonNode stepsNode = root.get("steps");
		if (stepsNode == null || !stepsNode.isArray() || stepsNode.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The workflow definition must contain at least one step.");
		}
		if (stepsNode.size() > MAX_STEPS) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"A workflow may contain at most " + MAX_STEPS + " steps.");
		}
		List<Step> parsed = new ArrayList<>();
		Set<String> codes = new LinkedHashSet<>();
		Set<Integer> sequences = new LinkedHashSet<>();
		for (JsonNode node : stepsNode) {
			String code = text(node, "code");
			if (!code.matches("[A-Z][A-Z0-9_]{1,63}")) {
				throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
						"Step code '" + code + "' must be upper case letters, digits or underscores.");
			}
			if (!codes.add(code)) {
				throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
						"Step code '" + code + "' is duplicated.");
			}
			int sequence = integer(node, "sequence", 1);
			if (!sequences.add(sequence)) {
				throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
						"Step sequence " + sequence + " is used more than once.");
			}
			String approverToken = text(node, "approver");
			ApproverType approver;
			try {
				approver = ApproverType.valueOf(approverToken);
			}
			catch (IllegalArgumentException ex) {
				throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
						"Step '" + code + "' names an unknown approver '" + approverToken + "'.");
			}
			int quorum = integer(node, "quorum", 1);
			if (quorum < 1 || quorum > 10) {
				throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
						"Step '" + code + "' must require between 1 and 10 approvals.");
			}
			String label = node.has("label") && !node.get("label").isNull() ? node.get("label").asString() : code;
			parsed.add(new Step(code, label, approver, sequence, quorum));
		}
		parsed.sort((left, right) -> Integer.compare(left.sequence(), right.sequence()));
		return new WorkflowSteps(parsed);
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || value.asString().isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"Every workflow step must define '" + field + "'.");
		}
		return value.asString().trim();
	}

	private static int integer(JsonNode node, String field, int defaultValue) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return defaultValue;
		}
		if (!value.isNumber() && !value.isString()) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"'" + field + "' must be a number.");
		}
		try {
			return Integer.parseInt(value.asString());
		}
		catch (NumberFormatException ex) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"'" + field + "' must be a whole number.");
		}
	}

	/** Steps in execution order. */
	public List<Step> steps() {
		return this.steps;
	}

	/** The first step, which a submission enters. */
	public Step first() {
		return this.steps.get(0);
	}

	/** The step after {@code code}, or empty when {@code code} is the last. */
	public java.util.Optional<Step> after(String code) {
		for (int index = 0; index < this.steps.size(); index++) {
			if (this.steps.get(index).code().equals(code)) {
				return index + 1 < this.steps.size() ? java.util.Optional.of(this.steps.get(index + 1))
						: java.util.Optional.empty();
			}
		}
		return java.util.Optional.empty();
	}

	/** Look up one step by code. */
	public java.util.Optional<Step> find(String code) {
		return this.steps.stream().filter(step -> step.code().equals(code)).findFirst();
	}

	/**
	 * One approval step.
	 *
	 * @param code stable step code, referenced by approval tasks and history
	 * @param label human-readable label shown to approvers
	 * @param approver who must act; resolved to concrete people by the assignment service
	 * @param sequence execution order, 1-based, unique
	 * @param quorum approvals required at this step
	 */
	public record Step(String code, String label, ApproverType approver, int sequence, int quorum) {
	}

}
