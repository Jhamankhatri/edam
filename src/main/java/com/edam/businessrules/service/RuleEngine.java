package com.edam.businessrules.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.edam.businessrules.model.BusinessRule;
import com.edam.businessrules.model.BusinessRuleSet;
import com.edam.businessrules.model.BusinessRuleVersion;
import com.edam.businessrules.model.RuleVersionStatus;
import com.edam.common.exception.BusinessException;
import com.edam.common.api.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Closed, side-effect-free evaluator for declarative business-rule conditions.
 *
 * <h2>Grammar</h2>
 * <pre>
 *   condition := { "all": [condition, ...] }
 *              | { "any": [condition, ...] }
 *              | { "not": condition }
 *              | { "field": "&lt;safe path&gt;", "op": "&lt;op&gt;", "value": &lt;json scalar&gt; }
 *   op := "eq" | "ne" | "lt" | "le" | "gt" | "ge" | "in" | "notIn" | "contains" | "startsWith" | "exists"
 * </pre>
 *
 * <h2>Security properties</h2>
 * <ul>
 *   <li>Every leaf field must appear in the {@link EvaluationContext#getAllowedFields()} whitelist.</li>
 *   <li>Operators are closed (no {@code eval}/{@code script}/{@code sql}).</li>
 *   <li>Maximum nesting depth {@value #MAX_DEPTH} guards against stack overflow.</li>
 *   <li>Values are JSON scalars (string/number/boolean/null) or arrays of scalars; nested objects
 *       on the value side are rejected so rule authors cannot compose executable payloads.</li>
 *   <li>{@code EvaluationContext} supplies values; the engine never reflects over classes or
 *       loads arbitrary types.</li>
 *   <li>No reflection, no classloading, no script engine, no JDBC.</li>
 * </ul>
 */
@Component
public class RuleEngine {

	private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

	public static final int MAX_DEPTH = 10;

	/** Hard limit on the number of leaf conditions per rule, to bound evaluation work. */
	public static final int MAX_LEAVES_PER_RULE = 50;

	private static final Set<String> COMPARISON_OPS = Set.of(
			"eq", "ne", "lt", "le", "gt", "ge",
			"in", "notIn", "contains", "startsWith", "exists");

	private static final Set<String> BRANCH_KEYS = Set.of("all", "any", "not");

	private final ObjectMapper json = new ObjectMapper();

	/** Parse a condition JSON (as stored) into a Map without ever executing it. */
	@SuppressWarnings("unchecked")
	public Map<String, Object> parseCondition(String jsonStr) {
		if (jsonStr == null || jsonStr.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Condition must be a JSON object.");
		}
		try {
			Object parsed = this.json.readValue(jsonStr, Object.class);
			if (!(parsed instanceof Map)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Condition must be a JSON object.");
			}
			return (Map<String, Object>) parsed;
		} catch (java.io.IOException iox) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Condition JSON is not valid: " + iox.getMessage());
		}
	}

	/** Parse an action JSON. */
	@SuppressWarnings("unchecked")
	public Map<String, Object> parseAction(String jsonStr) {
		if (jsonStr == null || jsonStr.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Action must be a JSON object.");
		}
		try {
			Object parsed = this.json.readValue(jsonStr, Object.class);
			if (!(parsed instanceof Map)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Action must be a JSON object.");
			}
			Map<String, Object> m = (Map<String, Object>) parsed;
			Object type = m.get("type");
			if (!(type instanceof String t) || !AllowedActions.isAllowed(t)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Unknown action type: " + type + ". Supported: " + AllowedActions.supported());
			}
			return m;
		} catch (java.io.IOException iox) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Action JSON is not valid: " + iox.getMessage());
		}
	}

	public String writeCondition(Map<String, Object> condition) {
		try { return this.json.writeValueAsString(condition); }
		catch (java.io.IOException iox) { throw new RuntimeException(iox); }
	}

	public String writeAction(Map<String, Object> action) {
		try { return this.json.writeValueAsString(action); }
		catch (java.io.IOException iox) { throw new RuntimeException(iox); }
	}

	/** Validate a parsed condition AST for grammar/security BEFORE publishing the rule. */
	public void validateCondition(Map<String, Object> condition, EvaluationContext ctx) {
		List<String> errs = new ArrayList<>();
		int leaves = validate(condition, ctx, 0, new HashSet<>(), errs);
		if (!errs.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Invalid rule condition: " + String.join("; ", errs));
		}
		if (leaves > MAX_LEAVES_PER_RULE) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Rule exceeds " + MAX_LEAVES_PER_RULE + " leaf conditions (" + leaves + ").");
		}
	}

	private int validate(Map<String, Object> node, EvaluationContext ctx, int depth, Set<String> path, List<String> errs) {
		if (depth > MAX_DEPTH) { errs.add("Expression nested too deeply (max " + MAX_DEPTH + ")"); return 0; }
		if (node == null) { errs.add("Condition is null"); return 0; }
		if (!(node instanceof Map<?, ?> m)) { errs.add("Condition node must be an object"); return 0; }
		int branchKeys = 0;
		int leaves = 0;
		if (m.containsKey("all")) {
			branchKeys++;
			Object arr = m.get("all");
			if (!(arr instanceof List<?> l)) { errs.add("\"all\" must be a list"); return 0; }
			for (Object child : l) leaves += validate(asMap(child, errs), ctx, depth + 1, path, errs);
		}
		if (m.containsKey("any")) {
			branchKeys++;
			Object arr = m.get("any");
			if (!(arr instanceof List<?> l)) { errs.add("\"any\" must be a list"); return 0; }
			for (Object child : l) leaves += validate(asMap(child, errs), ctx, depth + 1, path, errs);
		}
		if (m.containsKey("not")) {
			branchKeys++;
			leaves += validate(asMap(m.get("not"), errs), ctx, depth + 1, path, errs);
		}
		if (branchKeys > 1) {
			errs.add("A condition node may contain only one of all/any/not");
			return 0;
		}
		if (branchKeys == 0) {
			// Leaf: field+op+value
			Object field = m.get("field");
			Object op = m.get("op");
			Object value = m.get("value");
			if (!(field instanceof String f)) { errs.add("\"field\" must be a string"); return 0; }
			// Field whitelist is enforced at evaluation time (the same rule text may be valid in one
			// EvaluationContext and invalid in another; upsert/publish only validates syntax + shape).
			if (ctx != null && !ctx.isWildcardAllowed() && !ctx.isAllowedField(f)) {
				errs.add("Field \"" + f + "\" is not exposed to rules");
				return 0;
			}
			if (path.contains(f)) {
				// No loop possible without arbitrary functions; this guards against accidental
				// duplicate branches that cause huge evaluations.
			}
			if (!(op instanceof String o) || !COMPARISON_OPS.contains(o)) {
				errs.add("\"op\" must be one of " + COMPARISON_OPS); return 0;
			}
			if ("exists".equals(o)) {
				if (value != null && !(value instanceof Boolean)) {
					errs.add("\"exists\" requires a boolean value (or null/omitted)");
					return 0;
				}
			} else if ("in".equals(o) || "notIn".equals(o)) {
				if (!(value instanceof List<?> lv)) { errs.add("\"" + o + "\" requires a list value"); return 0; }
				for (Object v : lv) if (!isScalar(v)) { errs.add("\"" + o + "\" list entries must be scalars"); return 0; }
			} else {
				if (!isScalar(value)) { errs.add("\"" + o + "\" value must be a scalar"); return 0; }
			}
			leaves = 1;
		}
		return leaves;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> asMap(Object o, List<String> errs) {
		if (o == null) { errs.add("null child in logical operator"); return Map.of(); }
		if (!(o instanceof Map)) { errs.add("Child of logical operator must be an object"); return Map.of(); }
		return (Map<String, Object>) o;
	}

	private static boolean isScalar(Object v) {
		return v == null || v instanceof String || v instanceof Number || v instanceof Boolean;
	}

	/** Evaluate a validated condition against a context. Throws on evaluation errors. */
	public boolean evaluate(Map<String, Object> condition, EvaluationContext ctx) {
		try {
			return eval(condition, ctx);
		} catch (EvalException ee) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Rule evaluation failed: " + ee.getMessage());
		}
	}

	private boolean eval(Map<String, Object> node, EvaluationContext ctx) {
		if (node.containsKey("all")) {
			List<?> arr = (List<?>) node.get("all");
			for (Object c : arr) if (!eval(asMap(c, null), ctx)) return false;
			return true;
		}
		if (node.containsKey("any")) {
			List<?> arr = (List<?>) node.get("any");
			for (Object c : arr) if (eval(asMap(c, null), ctx)) return true;
			return false;
		}
		if (node.containsKey("not")) {
			return !eval(asMap(node.get("not"), null), ctx);
		}
		String field = (String) node.get("field");
		String op = (String) node.get("op");
		Object value = node.get("value");
		if (!ctx.isWildcardAllowed() && !ctx.isAllowedField(field)) {
			throw new EvalException("Field \"" + field + "\" is not exposed to rules");
		}
		Object actual = ctx.get(field);
		return compare(op, actual, value);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private boolean compare(String op, Object actual, Object expected) {
		switch (op) {
			case "exists" -> {
				boolean want = expected == null || Boolean.TRUE.equals(expected);
				return want ? actual != null : actual == null;
			}
			case "eq" -> {
				if (actual == null) return expected == null;
				return compareNumbers(actual, expected) == 0;
			}
			case "ne" -> { return !compare("eq", actual, expected); }
			case "lt" -> { return compareNumbers(actual, expected) < 0; }
			case "le" -> { return compareNumbers(actual, expected) <= 0; }
			case "gt" -> { return compareNumbers(actual, expected) > 0; }
			case "ge" -> { return compareNumbers(actual, expected) >= 0; }
			case "contains" -> {
				if (actual == null || expected == null) return false;
				if (actual instanceof String s && expected instanceof String e) return s.contains(e);
				if (actual instanceof Collection<?> c) return c.contains(expected);
				throw new EvalException("contains requires a string or collection actual value");
			}
			case "startsWith" -> {
				if (actual instanceof String s && expected instanceof String e) return s.startsWith(e);
				throw new EvalException("startsWith requires string values");
			}
			case "in" -> {
				if (!(expected instanceof List<?> l)) throw new EvalException("in requires list value");
				for (Object v : l) if (compareEq(actual, v)) return true;
				return false;
			}
			case "notIn" -> { return !compare("in", actual, expected); }
			default -> throw new EvalException("Unsupported operator: " + op);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private boolean compareEq(Object a, Object b) {
		if (a == null) return b == null;
		if (b == null) return false;
		if (a instanceof Number && b instanceof Number) {
			return toBigDecimal(a).compareTo(toBigDecimal(b)) == 0;
		}
		return a.equals(b);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private int compareNumbers(Object a, Object b) {
		if (a == null || b == null) throw new EvalException("Cannot compare null values");
		if (!(a instanceof Number) || !(b instanceof Number)) {
			if (a instanceof Comparable ac && b.getClass().isInstance(a)) {
				return ac.compareTo(b);
			}
			throw new EvalException("Cannot compare " + a.getClass().getSimpleName()
					+ " with " + b.getClass().getSimpleName());
		}
		return toBigDecimal(a).compareTo(toBigDecimal(b));
	}

	private static BigDecimal toBigDecimal(Object n) {
		if (n instanceof BigDecimal bd) return bd;
		if (n instanceof Number num) {
			// Avoid double rounding for floating literals; treat all numeric inputs as their toString form.
			return new BigDecimal(num.toString());
		}
		throw new EvalException("Not a number: " + n);
	}

	/** Evaluation context exposed to rules: a fixed, read-only map of field → value. */
	public interface EvaluationContext {
		boolean isAllowedField(String field);
		/** @return true if the context accepts any field (syntax-only validation path). */
		default boolean isWildcardAllowed() { return false; }
		Object get(String field);

		static EvaluationContext of(Map<String, ?> allowedFields) {
			Set<String> allowed = Set.copyOf(allowedFields.keySet());
			Map<String, Object> values = Collections.unmodifiableMap(new LinkedHashMap<>(allowedFields));
			return new EvaluationContext() {
				@Override public boolean isAllowedField(String f) { return allowed.contains(f); }
				@Override public Object get(String f) { return values.get(f); }
			};
		}

		/** Syntax-only validation context (accepts any field name). Used at upsert/publish time. */
		static EvaluationContext wildcard() {
			return new EvaluationContext() {
				@Override public boolean isAllowedField(String f) { return true; }
				@Override public boolean isWildcardAllowed() { return true; }
				@Override public Object get(String f) { return null; }
			};
		}
	}

	static final class EvalException extends RuntimeException {
		EvalException(String msg) { super(msg); }
	}

	/** Allowed action types — action dispatch happens elsewhere in Java services. The engine
	 *  only validates that the type is recognised. */
	public static final class AllowedActions {
		/** Actions the engine itself recognises. Services may extend this set at their
		 *  discretion by simply handling additional action types at dispatch time; validation
		 *  here only ensures the rule engine doesn't encounter an unknown type silently. */
		private static final Set<String> ALLOWED = Set.of(
				"REQUIRE_APPROVAL_LEVEL",    // elevate approval level
				"REJECT_REQUEST",            // hard-reject (validation policy)
				"WARN",                      // attach a warning to the request
				"REQUIRE_ATTACHMENT",        // require a document category present
				"SET_FLAG",                  // tag the request for downstream processing
				"SET_DELEGATION_TARGET"      // override delegation target (rule-gated)
		);
		static boolean isAllowed(String t) { return ALLOWED.contains(t); }
		static String supported() { return String.join(", ", ALLOWED); }
		private AllowedActions() {}
	}
}
