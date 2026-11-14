package com.edam.common.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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
	 * causes Jackson to sort keys during serialisation. We additionally call {@link #sortKeys}
	 * before serialising so that nested objects inside arrays (which Jackson's top-level sort does
	 * not descend into) are also ordered deterministically.
	 */
	private static final class CanonicalHolder {
		static final JsonMapper MAPPER = JsonMapper.builder()
				.configure(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
				.configure(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
				.build();
	}

	/** Parse and re-serialise JSON so whitespace/key-order/nesting-order differences are normalised away. Exposed for tests. */
	public static String canonicalJson(JsonMapper jsonMapper, String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		try {
			JsonNode tree = CanonicalHolder.MAPPER.readTree(body);
			sortKeys(tree);
			return CanonicalHolder.MAPPER.writeValueAsString(tree);
		}
		catch (RuntimeException ex) {
			// Not JSON (e.g. form-urlencoded or empty); fall back to the raw trimmed body.
			// tools.jackson.core.exc.StreamReadException is a RuntimeException in this Jackson variant.
			return body.trim();
		}
	}

	/**
	 * Recursively sort the keys of every ObjectNode so that property order in the incoming JSON
	 * cannot change the canonical serialisation. Array ordering is preserved (it carries meaning).
	 * Primitives and nulls are left untouched.
	 */
	private static void sortKeys(JsonNode node) {
		if (node == null) {
			return;
		}
		if (node.isArray()) {
			node.forEach(RequestFingerprint::sortKeys);
			return;
		}
		if (!node.isObject()) {
			return;
		}
		ObjectNode obj = (ObjectNode) node;
		// Collect field names into a sorted list first; then build a new ObjectNode in sorted order.
		// This avoids all generic-Iterator target-type inference issues across Jackson variants.
		java.util.List<String> names = new java.util.ArrayList<>();
		java.util.Iterator<?> nameIt = (java.util.Iterator<?>) invokeNoArg(node, "fieldNames");
		while (nameIt != null && nameIt.hasNext()) {
			Object n = nameIt.next();
			if (n instanceof String s) {
				names.add(s);
			}
		}
		java.util.Collections.sort(names);
		ObjectNode sorted = CanonicalHolder.MAPPER.createObjectNode();
		for (String name : names) {
			JsonNode child = obj.get(name);
			sortKeys(child);
			sorted.set(name, child);
		}
		obj.removeAll();
		obj.setAll(sorted);
	}

	/** Reflection helper used solely to avoid compile-time generic-return-type ambiguities. */
	private static java.util.Iterator<?> invokeNoArg(Object target, String method) {
		try {
			Object result = target.getClass().getMethod(method).invoke(target);
			return (java.util.Iterator<?>) result;
		}
		catch (ReflectiveOperationException ex) {
			return java.util.Collections.emptyIterator();
		}
	}

}
