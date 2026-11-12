package com.edam.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The correlation ID is injected into logs and error envelopes, so it must never accept
 * attacker-controlled strings: only well-formed UUIDs are propagated.
 */
class CorrelationIdTest {

	@ParameterizedTest
	@ValueSource(strings = { "not-a-uuid", "../../etc/passwd", "0123456789abcdef0123456789abcdef", "",
			"550e8400-e29b-41d4-a716-44665544", "550e8400-e29b-41d4-a716-4466554400001",
			"550e8400-e29b-41d4-a716-44665544000g", "<script>alert(1)</script>" })
	@DisplayName("rejects malformed inbound ids and substitutes a generated UUID")
	void rejectsMalformedInput(String candidate) {
		String resolved = CorrelationId.sanitizeOrGenerate(candidate);

		assertThat(resolved).isNotEqualTo(candidate);
		assertThat(resolved).hasSize(36).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	}

	@Test
	@DisplayName("preserves a well-formed inbound UUID in lower case")
	void preservesValidUuid() {
		String inbound = "550E8400-E29B-41D4-A716-446655440000";

		assertThat(CorrelationId.sanitizeOrGenerate(inbound)).isEqualTo(inbound.toLowerCase());
	}

	@Test
	@DisplayName("generates a unique id when no candidate is supplied")
	void generatesWhenAbsent() {
		assertThat(CorrelationId.sanitizeOrGenerate(null)).isNotEqualTo(CorrelationId.generate());
	}

	@Test
	@DisplayName("returns null outside a request scope instead of throwing")
	void currentIsNullOutsideRequest() {
		assertThat(CorrelationId.current()).isNull();
	}

}
