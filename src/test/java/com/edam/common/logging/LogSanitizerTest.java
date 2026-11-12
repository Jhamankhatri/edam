package com.edam.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void sanitizeStripsControlCharactersAndNewlines() {
        String malicious = "user-123\r\nINFO forged log entry\tvalue";
        assertThat(LogSanitizer.sanitize(malicious))
                .doesNotContain("\r")
                .doesNotContain("\n")
                .doesNotContain("\t")
                .isEqualTo("user-123__INFO forged log entry_value");
    }

    @Test
    void isValidCorrelationIdAcceptsSafeValuesAndRejectsInjection() {
        assertThat(LogSanitizer.isValidCorrelationId("req-abc_123.45")).isTrue();
        assertThat(LogSanitizer.isValidCorrelationId("bad\nid")).isFalse();
        assertThat(LogSanitizer.isValidCorrelationId("bad id with spaces")).isFalse();
        assertThat(LogSanitizer.isValidCorrelationId("a".repeat(65))).isFalse();
    }

    @Test
    void maskIfSensitiveRedactsConfidentialKeys() {
        assertThat(LogSanitizer.maskIfSensitive("Authorization", "Bearer secret-token"))
                .isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.maskIfSensitive("cnic", "42101-1234567-1"))
                .isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.maskIfSensitive("institutionCode", "BANK_A"))
                .isEqualTo("BANK_A");
    }
}
