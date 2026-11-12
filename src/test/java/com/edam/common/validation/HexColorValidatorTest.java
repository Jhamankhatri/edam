package com.edam.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HexColorValidatorTest {

    private final HexColorValidator validator = new HexColorValidator();

    @Test
    void acceptsValidHexColorsAndRejectsCssInjectionPayloads() {
        assertThat(validator.isValid("#1B365D", null)).isTrue();
        assertThat(validator.isValid("#0f766e", null)).isTrue();
        assertThat(validator.isValid("red; background:url(x)", null)).isFalse();
        assertThat(validator.isValid("#12345", null)).isFalse();
        assertThat(validator.isValid("1B365D", null)).isFalse();
    }

    @Test
    void sanitizeOrDefaultFallsBackOnInvalidColor() {
        assertThat(HexColorValidator.sanitizeOrDefault("#ABCDEF", "#1B365D")).isEqualTo("#ABCDEF");
        assertThat(HexColorValidator.sanitizeOrDefault("javascript:alert(1)", "#1B365D")).isEqualTo("#1B365D");
    }
}
