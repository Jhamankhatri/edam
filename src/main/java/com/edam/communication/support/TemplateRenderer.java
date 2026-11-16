package com.edam.communication.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.edam.common.exception.BusinessException;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Renders email templates with strict placeholder whitelist validation.
 *
 * <h2>Security model (no script execution)</h2>
 * <ul>
 *   <li>Placeholders use a simple {@code {{placeholder}}} syntax. We DO NOT use a general-purpose
 *       template engine (FreeMarker/Thymeleaf/StringTemplate) and therefore no arbitrary EL/script
 *       execution is possible.</li>
 *   <li>Only placeholders declared in the template's {@code allowed_placeholders} list are
 *       substituted; unknown placeholders are rejected at render-time, not silently removed.</li>
 *   <li>Replacement values are HTML-escaped when rendered into the HTML body (to prevent stored-XSS
 *       via user-controlled fields like vendor name / description). The plain-text body receives
 *       raw substitution (no HTML context).</li>
 *   <li>Subject and text bodies allow newlines but no HTML; placeholders are escaped for text too
 *       when needed.</li>
 * </ul>
 *
 * <p>This keeps template authors from accidentally opening an HTML-injection vector while still
 * supporting useful per-event dynamic content.
 */
@Component
public class TemplateRenderer {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

	public static final int MAX_PLACEHOLDERS = 40;
	public static final int MAX_PLACEHOLDER_NAME = 60;

	/** Render an HTML body with HTML-escaped substitutions. The admin-supplied template is
	 *  then sanitized via Jsoup to remove scripts/handlers/event attributes (defense in depth). */
	public String renderHtml(String template, Map<String, String> values, Set<String> allowed) {
		validateTemplate(template, allowed);
		String substituted = substitute(template, values, true);
		return sanitizeHtml(substituted);
	}

	/** Render a plain-text body or subject line. Values are not HTML-escaped. */
	public String renderText(String template, Map<String, String> values, Set<String> allowed) {
		validateTemplate(template, allowed);
		return substitute(template, values, false);
	}

	/** Extract the set of {@code {{name}}} placeholders declared in a template string. */
	public Set<String> extractPlaceholders(String template) {
		Set<String> names = new HashSet<>();
		if (template == null) return names;
		Matcher m = PLACEHOLDER.matcher(template);
		while (m.find()) {
			names.add(m.group(1));
		}
		return names;
	}

	/**
	 * Validate that every placeholder in {@code template} is in {@code allowed}. Called both at
	 * template save time (to catch typos early) and at render time (defense in depth).
	 */
	public void validateTemplate(String template, Set<String> allowed) {
		if (template == null) return;
		if (allowed == null) allowed = Set.of();
		Set<String> used = extractPlaceholders(template);
		if (used.size() > MAX_PLACEHOLDERS) {
			throw new BusinessException("edam.template.too_many_placeholders: " + used.size());
		}
		List<String> disallowed = new ArrayList<>();
		for (String name : used) {
			if (name.length() > MAX_PLACEHOLDER_NAME) {
				throw new BusinessException("edam.template.placeholder_name_too_long: " + name);
			}
			if (!allowed.contains(name)) {
				disallowed.add(name);
			}
		}
		if (!disallowed.isEmpty()) {
			throw new BusinessException("edam.template.disallowed_placeholders: " + String.join(",", disallowed));
		}
	}

	private String substitute(String template, Map<String, String> values, boolean htmlEscape) {
		if (template == null) return null;
		if (values == null) values = Map.of();
		Matcher m = PLACEHOLDER.matcher(template);
		StringBuilder sb = new StringBuilder(template.length() + 64);
		while (m.find()) {
			String name = m.group(1);
			String value = values.getOrDefault(name, "");
			String safe = htmlEscape ? htmlEscape(value) : value;
			m.appendReplacement(sb, Matcher.quoteReplacement(safe));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/** Minimal HTML escaping (only 5 entities). No dependency on a templating lib. */
	public static String htmlEscape(String s) {
		if (s == null) return "";
		StringBuilder out = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '&' -> out.append("&amp;");
				case '<' -> out.append("&lt;");
				case '>' -> out.append("&gt;");
				case '"' -> out.append("&quot;");
				case '\'' -> out.append("&#39;");
				default -> out.append(c);
			}
		}
		return out.toString();
	}

	/** Parse a comma-separated placeholder list from DB into an ordered set. */
	public static Set<String> parsePlaceholders(String stored) {
		Set<String> out = new HashSet<>();
		if (stored == null || stored.isBlank()) return out;
		for (String token : stored.split(",")) {
			String t = token.trim();
			if (!t.isEmpty()) out.add(t);
		}
		return out;
	}

	/** Join placeholder set into comma-separated form for persistence. */
	public static String joinPlaceholders(Set<String> placeholders) {
		if (placeholders == null || placeholders.isEmpty()) return "";
		return String.join(",", new LinkedHashSet<>(placeholders));
	}

	/**
	 * Allowlist-based HTML sanitization. We deliberately permit a conservative set of
	 * formatting/link tags that make transactional emails readable while stripping all
	 * {@code <script>}, {@code <style>}, {@code on*} handlers, {@code javascript:} URLs, etc.
	 */
	static String sanitizeHtml(String html) {
		if (html == null) return null;
		Safelist safelist = Safelist.relaxed()
				.addEnforcedAttribute("a", "rel", "noopener noreferrer")
				.addProtocols("a", "href", "http", "https", "mailto");
		return Jsoup.clean(html, safelist);
	}
}
