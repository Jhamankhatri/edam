package com.edam.common.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds a deterministic, order-insensitive fingerprint of an idempotent request.
 *
 * <p>The fingerprint hashes three inputs together (in that order, separated by NUL bytes so no
 * field can bleed into the next):
 * <ol>
 *   <li>The HTTP method and path without the query string (e.g. {@code POST /api/v1/expense-requests}).</li>
 *   <li>Path variables, sorted lexicographically by name. Path parameters are part of the resource
 *       identity — a key used against {@code /requests/A} must not replay against {@code /requests/B}.</li>
 *   <li>The request body, canonicalised via Jackson (parse tree → re-serialise) so that JSON
 *       property ordering and insignificant whitespace do not cause a spurious fingerprint mismatch.</li>
 * </ol>
 *
 * <p>Query parameters are intentionally excluded because the idempotent endpoints under Step 2 do
 * not use them for request identity; if a future endpoint does, it should be passed in as an
 * additional scoping component through {@link #forRequest} or a dedicated overload.
 */
public final class RequestFingerprint {

	private RequestFingerprint() {
	}

	/**
	 * Compute a canonical body+resource fingerprint for the supplied request.
	 *
	 * @param jsonMapper shared Jackson mapper (used to canonicalise JSON bodies)
	 * @param request current HTTP request; must be wrapped by {@link CachedBodyFilter}
	 * @param uriVariables decoded path variables from the handler mapping (name → value)
	 */
	public static String forRequest(JsonMapper jsonMapper, HttpServletRequest request,
			Map<String, String> uriVariables) {
		StringBuilder canon = new StringBuilder();
		canon.append(request.getMethod()).append(' ').append(request.getRequestURI()).append('\0');

		// Path variables sorted so that extraction order cannot change the hash.
		TreeMap<String, String> sorted = new TreeMap<>();
		if (uriVariables != null) {
			uriVariables.forEach((k, v) -> sorted.put(k, v == null ? "" : v));
		}
		sorted.forEach((k, v) -> canon.append(k).append('=').append(v).append('&'));
		canon.append('\0');

		String body = readBody(request);
		if (!body.isEmpty()) {
			canon.append(canonicalJson(jsonMapper, body));
		}
		return IdempotencyService.fingerprint(canon.toString());
	}

	private static String readBody(HttpServletRequest request) {
		if (request instanceof CachedBodyHttpServletRequest cached) {
			return cached.cachedBodyAsString();
		}
		// Defence-in-depth: if for any reason the request was not wrapped by CachedBodyFilter but
		// declares a body, consume the stream directly. This prevents a dangerous false-positive
		// match where two different bodies both hash to the empty-string fingerprint. The stream
		// is read once here; Spring MVC will see an empty body in that case — but the filter
		// should always wrap POST/PUT/PATCH requests (see CachedBodyFilter and its explicit
		// registration in ServletFilterConfig), so this branch is a last-resort safety net rather
		// than the expected path.
		if (request.getContentLength() > 0 || "POST".equalsIgnoreCase(request.getMethod())
				|| "PUT".equalsIgnoreCase(request.getMethod())
				|| "PATCH".equalsIgnoreCase(request.getMethod())) {
			try {
				byte[] bytes = request.getInputStream().readAllBytes();
				return new String(bytes, StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				return "";
			}
		}
		return "";
	}

	/**
	 * Lazily-built canonical mapper that sorts object keys alphabetically.
	 *
	 * <p>Sharing the application mapper would be wrong: the global mapper is used for response
	 * rendering and its features (NON_NULL inclusion, naming strategies, custom modules) could
	 * silently change the canonical form. The fingerprint mapper must produce output that is
	 * byte-identical regardless of how the incoming JSON was laid out.
	 *
	 * <p>{@code SORT_PROPERTIES_ALPHABETICALLY} combined with {@code ORDER_MAP_ENTRIES_BY_KEYS}
	 * causes Jackson to emit object keys in alphabetical order during serialisation; in
	 * tools.jackson these features apply recursively to ObjectNode children at any depth, so no
	 * manual tree rewriting is needed.
	 */
	private static final class CanonicalHolder {
		static final JsonMapper MAPPER = JsonMapper.builder()
				.configure(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
				.configure(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
				.build();
	}

	/**
	 * Canonicalise a JSON string: parse + re-serialise with the canonical mapper whose
	 * SORT_PROPERTIES_ALPHABETICALLY and ORDER_MAP_ENTRIES_BY_KEYS features guarantee keys are
	 * emitted in alphabetical order at every nesting level. In tools.jackson these features apply
	 * recursively when the output is produced from a tree model via ObjectCodec (which JsonMapper
	 * uses), so nested objects — including those inside arrays — are also sorted. We do NOT need
	 * to manually reorder the JsonNode tree; the MapperFeature handles it at serialisation time.
	 *
	 * <p>Public so the aspect and tests can use it without reflection. The {@code jsonMapper}
	 * parameter is accepted for API compatibility but ignored — canonicalisation always uses the
	 * internal {@link CanonicalHolder#MAPPER} so output is byte-identical regardless of how the
	 * caller's mapper is configured.
	 */
	public static String canonicalJson(JsonMapper jsonMapper, String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		try {
			JsonNode tree = CanonicalHolder.MAPPER.readTree(body);
			// Re-serialise through the canonical mapper: the SORT_PROPERTIES_ALPHABETICALLY +
			// ORDER_MAP_ENTRIES_BY_KEYS features handle key ordering at every depth.
			return CanonicalHolder.MAPPER.writeValueAsString(tree);
		}
		catch (Exception ex) {
			// Not JSON (e.g. form-urlencoded or empty); fall back to the raw trimmed body.
			return body.trim();
		}
	}

}
