package com.edam.common.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes sensitive values from audit payloads before they are persisted.
 *
 * <h2>Why a redactor rather than "don't put secrets in DTOs"</h2>
 * Audit diffs are produced generically from entity state. Without a mechanical guard, a future
 * entity that happens to hold a token, a mail password or a vendor tax identifier would silently
 * write it into an append-only, widely-readable audit table. BRD AUD-02 and §28.4 require
 * redaction, and §41 forbids recording secrets in audit payloads. Redaction therefore happens
 * centrally, at the last possible moment, so it cannot be forgotten per call site.
 *
 * <h2>Strategy</h2>
 * Field <em>names</em> are matched case-insensitively against a deny list; matched values are
 * replaced entirely (not masked by length) so the size of a secret is not disclosed either.
 * Vendor identity/tax fields (CNIC, NTN, STRN) are redacted by default because BRD §13.1 requires
 * them to be protected according to bank policy, and an audit table is exactly where they must not
 * accumulate in clear text.
 */
public final class AuditValueRedactor {

	/** Placeholder written instead of a sensitive value. */
	public static final String REDACTED = "***redacted***";

	/**
	 * Field names whose values are never recorded in an audit payload. Matched as
	 * "contains" on the lower-cased name so {@code apiSecret}, {@code smtpPassword} and
	 * {@code clientSecret} are all covered.
	 */
	private static final List<String> DENY_FRAGMENTS = List.of("password", "passwd", "secret", "credential",
			"apikey", "api_key", "token", "privatekey", "private_key", "salt", "cnic", "ntn", "strn",
			"accountnumber", "account_number", "iban", "cardnumber", "card_number", "cvv", "connectionstring",
			"connection_string", "authorization", "cookie", "sessionid", "session_id");

	/** Values that look like bearers tokens or key material are redacted even in unknown fields. */
	private static final Pattern SUSPICIOUS_VALUE = Pattern.compile(
			"(?i)^(bearer\\s+\\S+|\\S{40,}={0,2})$|^(-----BEGIN [A-Z ]+-----)");

	/** Fields that must be redacted when serialising any map/entity by property name. */
	private static final Set<String> WHOLE_OBJECT_KEYS = Set.of("passwordHash", "encodedPassword");

	private AuditValueRedactor() {
	}

	/** True when the field name must never appear in an audit payload. */
	public static boolean isSensitiveName(String name) {
		if (name == null) {
			return false;
		}
		String lower = name.toLowerCase(java.util.Locale.ROOT);
		if (WHOLE_OBJECT_KEYS.contains(name)) {
			return true;
		}
		return DENY_FRAGMENTS.stream().anyMatch(lower::contains);
	}

	/**
	 * Copy of {@code source} with sensitive entries replaced by {@link #REDACTED}.
	 * Keys are preserved so the shape of a change is still auditable.
	 */
	public static Map<String, Object> redact(Map<String, Object> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> safe = new LinkedHashMap<>();
		source.forEach((key, value) -> safe.put(key, redactValue(key, value)));
		return safe;
	}

	private static Object redactValue(String key, Object value) {
		if (value == null) {
			return null;
		}
		if (isSensitiveName(key)) {
			return REDACTED;
		}
		if (value instanceof Map<?, ?> nested) {
			Map<String, Object> safeNested = new LinkedHashMap<>();
			nested.forEach((nestedKey, nestedValue) -> safeNested.put(String.valueOf(nestedKey),
					redactValue(String.valueOf(nestedKey), nestedValue)));
			return safeNested;
		}
		if (value instanceof String text && SUSPICIOUS_VALUE.matcher(text).matches()) {
			return REDACTED;
		}
		return value;
	}

}
