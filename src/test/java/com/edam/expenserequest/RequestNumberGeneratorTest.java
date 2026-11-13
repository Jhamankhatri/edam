package com.edam.expenserequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.edam.expenserequest.service.RequestNumberGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The request-number format (OD-P2-05).
 *
 * <h2>Why the format is tested even though it is provisional</h2>
 * The format is recorded as a pending business decision, but the properties that make it usable are not
 * optional for any candidate scheme: a number must be unique, stable, and quotable. Those properties are
 * asserted here, so an approved change of format is a change to a small, covered function rather than a
 * leap into the unknown. Sequence allocation itself needs a database and is covered by the integration
 * suite.
 */
class RequestNumberGeneratorTest {

	@Test
	@DisplayName("a number is category code, period and zero-padded sequence")
	void format() {
		assertThat(RequestNumberGenerator.format("TRAVEL", "202611", 42)).isEqualTo("TRAVEL-202611-000042");
	}

	@Test
	@DisplayName("the sequence is padded to six digits and grows beyond it without truncation")
	void sequencePadding() {
		assertThat(RequestNumberGenerator.format("IT", "202611", 1)).isEqualTo("IT-202611-000001");
		assertThat(RequestNumberGenerator.format("IT", "202611", 999999)).isEqualTo("IT-202611-999999");
		assertThat(RequestNumberGenerator.format("IT", "202611", 1_000_000)).isEqualTo("IT-202611-1000000");
	}

	@Test
	@DisplayName("a blank or absent category code falls back rather than producing an empty prefix")
	void blankCategoryFallsBack() {
		assertThat(RequestNumberGenerator.format(null, "202611", 7)).startsWith("REQ-202611-");
		assertThat(RequestNumberGenerator.format("   ", "202611", 7)).startsWith("REQ-202611-");
	}

	@ParameterizedTest
	@CsvSource({ "'travel travel',TRAVELTRAVEL", "'admin/gsd',ADMINGSD", "'im-it',IM-IT", "'r&d',RD" })
	@DisplayName("characters that would break a number in a URL or an email are removed")
	void sanitisesCategoryCode(String raw, String expectedPrefix) {
		assertThat(RequestNumberGenerator.format(raw, "202611", 5)).isEqualTo(expectedPrefix + "-202611-000005");
	}

	@Test
	@DisplayName("two different categories never produce the same number for the same sequence")
	void categoryIsPartOfIdentity() {
		String opex = RequestNumberGenerator.format("TRAVEL", "202611", 100);
		String capex = RequestNumberGenerator.format("ASSET", "202611", 100);
		assertThat(opex).isNotEqualTo(capex);
	}

	@ParameterizedTest
	@ValueSource(strings = { "TRAVEL-202611-000042", "REQ-202611-1000000", "IM-IT-202612-000007" })
	@DisplayName("a generated number matches the declared shape")
	void matchesDeclaredShape(String candidate) {
		assertThat(RequestNumberGenerator.matchesFormat(candidate)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "TRAVEL-2026-000042", "travel-202611-000042", "TRAVEL202611000042", "" })
	@DisplayName("a malformed candidate does not match")
	void rejectsMalformed(String candidate) {
		assertThat(RequestNumberGenerator.matchesFormat(candidate)).isFalse();
	}

}
