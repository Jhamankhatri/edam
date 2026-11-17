package com.edam.integrations.support;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Redacts credentials/tokens from diagnostic messages before they are persisted or returned.
 * Used by the integration service and adapters so that provider errors cannot leak
 * Authorization/Basic/ApiKey headers or long random tokens.
 */
public final class DiagnosticRedactor {

	private static final int MAX_MESSAGE = 900;

	/** Authorization/Basic/Bearer/ApiKey header patterns, plus any 30+ char token-like run. */
	private static final Pattern BASIC    = Pattern.compile("(?is)(authorization\\s*:\\s*)basic\\s+\\S+");
	private static final Pattern BEARER   = Pattern.compile("(?is)(authorization\\s*:\\s*)bearer\\s+\\S+");
	private static final Pattern APIKEYH  = Pattern.compile("(?is)(x-api-key\\s*:\\s*)\\S+");
	private static final Pattern TOKEN    = Pattern.compile("(?is)(token|apikey|api_key|secret|password|passwd|credential)\\s*[:=]\\s*\\S+");
	private static final Pattern LONGHEX  = Pattern.compile("\\b[a-f0-9]{32,}\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern LONG64   = Pattern.compile("\\b[A-Za-z0-9_\\-]{40,}={0,2}\\b");
	private static final String  MASK = "***";

	private DiagnosticRedactor() {}

	/** Redact and truncate a diagnostic message. Returns {@code null} for null input. */
	public static String sanitize(String raw) {
		if (raw == null) return null;
		String s = raw;
		s = BASIC.matcher(s).replaceAll("$1" + MASK);
		s = BEARER.matcher(s).replaceAll("$1" + MASK);
		s = APIKEYH.matcher(s).replaceAll("$1" + MASK);
		s = TOKEN.matcher(s).replaceAll("$1=" + MASK);
		s = LONGHEX.matcher(s).replaceAll(MASK);
		s = LONG64.matcher(s).replaceAll(MASK);
		if (s.length() > MAX_MESSAGE) s = s.substring(0, MAX_MESSAGE) + "…";
		return s;
	}

	/** Returns a copy of the metadata map with any sensitive top-level keys redacted. */
	public static Map<String, Object> sanitizeMetadata(Map<String, Object> in) {
		if (in == null) return Map.of();
		java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, Object> e : in.entrySet()) {
			String k = e.getKey();
			if (com.edam.common.audit.AuditValueRedactor.isSensitiveName(k)) {
				out.put(k, com.edam.common.audit.AuditValueRedactor.REDACTED);
			} else {
				Object v = e.getValue();
				if (v instanceof String s) v = sanitize(s);
				out.put(k, v);
			}
		}
		return Map.copyOf(out);
	}
}
