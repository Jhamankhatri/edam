package com.edam.common.logging;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility methods for preventing log injection (CRLF / control characters)
 * and redacting sensitive attributes before writing diagnostic logs.
 */
public final class LogSanitizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\t\\p{Cntrl}]");
    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passcode", "secret", "token", "authorization",
            "cookie", "set-cookie", "cnic", "ntn", "strn", "apikey", "api_key"
    );

    private LogSanitizer() {
    }

    /**
     * Strips control characters (including CR/LF) and truncates untrusted values for safe logging.
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        String cleaned = CONTROL_CHARS.matcher(input).replaceAll("_").trim();
        return cleaned.length() > 256 ? cleaned.substring(0, 256) + "..." : cleaned;
    }

    /**
     * Validates an incoming correlation ID header value against a strict safe character allowlist.
     */
    public static boolean isValidCorrelationId(String candidate) {
        return candidate != null && VALID_CORRELATION_ID.matcher(candidate).matches();
    }

    /**
     * Masks potentially sensitive values by key name before diagnostic output.
     */
    public static String maskIfSensitive(String key, String value) {
        if (key != null && SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
            return "[REDACTED]";
        }
        return sanitize(value);
    }
}
