package com.edam.businessrules.service;

import java.util.List;
import java.util.Map;

import com.edam.common.exception.BusinessException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTest {

	private final RuleEngine engine = new RuleEngine();
	private final RuleEngine.EvaluationContext ctx = RuleEngine.EvaluationContext.of(Map.of(
			"amount", new java.math.BigDecimal("15000"),
			"currency", "PKR",
			"category", "TRAVEL",
			"isDraft", false,
			"submitted", true));

	@Test
	@DisplayName("eq / ne / lt / le / gt / ge compare numbers")
	void numericComparisons() {
		assertThat(eval(Map.of("field","amount","op","gt","value",10000))).isTrue();
		assertThat(eval(Map.of("field","amount","op","ge","value",15000))).isTrue();
		assertThat(eval(Map.of("field","amount","op","lt","value",20000))).isTrue();
		assertThat(eval(Map.of("field","amount","op","le","value",15000))).isTrue();
		assertThat(eval(Map.of("field","amount","op","eq","value",15000))).isTrue();
		assertThat(eval(Map.of("field","amount","op","ne","value",15000))).isFalse();
	}

	@Test
	@DisplayName("all / any / not compose conditions")
	void logicComposition() {
		Map<String,Object> all = Map.of("all", List.of(
				Map.of("field","amount","op","gt","value",10000),
				Map.of("field","currency","op","eq","value","PKR")));
		assertThat(eval(all)).isTrue();
		Map<String,Object> any = Map.of("any", List.of(
				Map.of("field","currency","op","eq","value","USD"),
				Map.of("field","currency","op","eq","value","PKR")));
		assertThat(eval(any)).isTrue();
		Map<String,Object> not = Map.of("not", Map.of("field","currency","op","eq","value","USD"));
		assertThat(eval(not)).isTrue();
	}

	@Test
	@DisplayName("in / notIn / contains / startsartsWith / exists")
	void operators() {
		assertThat(eval(Map.of("field","currency","op","in","value",List.of("USD","PKR","EUR")))).isTrue();
		assertThat(eval(Map.of("field","currency","op","notIn","value",List.of("USD","EUR")))).isTrue();
		assertThat(eval(Map.of("field","category","op","startsWith","value","TRA"))).isTrue();
		assertThat(eval(Map.of("field","submitted","op","exists","value",true))).isTrue();
		assertThat(eval(Map.of("field","submitted","op","exists","value",false))).isFalse();
	}

	@Test
	@DisplayName("unknown field, operator, non-scalar value, and excessive nesting are rejected")
	void validationRejectsUnsafe() {
		RuleEngine.EvaluationContext c = ctx;
		assertThatThrownBy(() -> engine.validateCondition(Map.of("field","__BAD__","op","eq","value","x"), c))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> engine.validateCondition(Map.of("field","amount","op","exec","value","x"), c))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> engine.validateCondition(Map.of("field","amount","op","eq","value",Map.of("a",1)), c))
				.isInstanceOf(BusinessException.class);
		// Deep nesting is rejected
		Map<String,Object> deep = Map.of("not", Map.of("not", Map.of("not", Map.of("not", Map.of("not",
				Map.of("not", Map.of("not", Map.of("not", Map.of("not", Map.of("not",
				Map.of("not", Map.of("not", Map.of("field","amount","op","eq","value",1)))))))))))));
		assertThatThrownBy(() -> engine.validateCondition(deep, c))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("unknown action type is rejected at parse time")
	void actionValidation() {
		assertThatThrownBy(() -> engine.parseAction("{\"type\":\"DROP_TABLE\"}"))
				.isInstanceOf(BusinessException.class);
		Map<String,Object> a = engine.parseAction("{\"type\":\"WARN\",\"message\":\"high amount\"}");
		assertThat(a.get("type")).isEqualTo("WARN");
	}

	private boolean eval(Map<String,Object> cond) {
		engine.validateCondition(cond, ctx);
		return engine.evaluate(cond, ctx);
	}
}
