package com.edam.common.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
	 * provides deterministic ordering for POJO and {@code Map} serialisation; for JsonNode trees
	 * we perform an explicit recursive key sort in {@link #sortKeys} (see that method for why).
	 */
	private static final class CanonicalHolder {
		static final JsonMapper MAPPER = JsonMapper.builder()
				.configure(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
				.configure(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
				.build();
	}

	/**
	 * Canonicalise a JSON string: parse + recursively sort every ObjectNode's keys +
	 * re-serialise. Jackson's {@code SORT_PROPERTIES_ALPHABETICALLY} feature alone does NOT
	 * sort JsonNode tree children in tools.jackson — it only applies to POJO properties — so we
	 * must walk the tree ourselves and rebuild each ObjectNode in alphabetical key order.
	 * Arrays are recursed into so nested objects sort too; array element order is preserved
	 * because it carries semantic meaning.
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
			sortKeys(tree);
			return CanonicalHolder.MAPPER.writeValueAsString(tree);
		}
		catch (Exception ex) {
			// Not JSON (e.g. form-urlencoded or empty); fall back to the raw trimmed body.
			return body.trim();
		}
	}

	/**
	 * Recursively sort object keys alphabetically; leave arrays and primitives untouched.
	 * Implemented by collecting each ObjectNode's field names into a sorted list and rebuilding
	 * the node in that order. Children are removed from the original before being attached to
	 * the replacement to preserve Jackson's single-parent invariant.
	 *
	 * <p>To work around a javac target-type inference quirk with tools.jackson (where {@code
	 * ObjectNode.fieldNames()} is declared to return raw {@code Iterator} and javac refuses to
	 * narrow it via a generic witness), we cast the receiver to a raw {@code JsonNode} and
	 * suppress rawtypes at the variable declaration site. The returned Iterator is iterated
	 * with casts and {@code instanceof} checks.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void sortKeys(JsonNode node) {
		if (node == null) {
			return;
		}
		if (node.isArray()) {
			for (JsonNode child : node) {
				sortKeys(child);
			}
			return;
		}
		if (!node.isObject()) {
			return;
		}
		ObjectNode obj = (ObjectNode) node;
		// Collect field names via JsonNode.fields(), which returns Iterator<Map.Entry<String,JsonNode>>
		// (a properly generic signature in tools.jackson). The raw-typed fieldNames() Iterator
		// returned by earlier approaches tripped javac target-type inference bugs and in one
		// iteration also returned an empty iterator due to a reflective dispatch quirk, so we
		// avoid fieldNames() entirely and use fields() instead.
		List<String> names = new ArrayList<>();
		// obj.fields() returns a raw Iterator in tools.jackson (same javac inference quirk as
		// fieldNames()); cast through Object and iterate with instanceof checks so we never
		// have to mention the raw type at the declaration site.
		Object rawFields = obj.fields();
		if (rawFields instanceof Iterator<?> it) {
			while (it.hasNext()) {
				Object entry = it.next();
				if (entry instanceof Map.Entry<?, ?> e && e.getKey() instanceof String s) {
					names.add(s);
				}
			}
		}
		if (names.isEmpty()) {
			// Fallback: reflective fieldNames() in case fields() returned nothing for this
			// tools.jackson version.
			//noinspection unchecked
			// Last-resort fallback: use reflection on fieldNames() if fields() is unavailable for
			// some reason (defensive).
			Object rawIterable = invokeFieldNamesRaw(obj);
			if (rawIterable instanceof Iterator it) {
				while (it.hasNext()) {
					Object n = it.next();
					if (n instanceof String s) {
						names.add(s);
					}
				}
			}
		}
		Collections.sort(names);
		ObjectNode sorted = CanonicalHolder.MAPPER.createObjectNode();
		for (String name : names) {
			JsonNode child = obj.remove(name);
			if (child != null) {
				sortKeys(child);
				sorted.set(name, child);
			}
		}
		obj.removeAll();
		obj.setAll(sorted);
	}

	/**
	 * Invoke {@code fieldNames()} reflectively so we don't trigger javac's raw-to-generic
	 * inference bug on the direct call. Uses the declaring class from JsonNode's hierarchy.
	 */
	private static Object invokeFieldNamesRaw(Object target) {
		try {
			java.lang.reflect.Method m = JsonNode.class.getMethod("fieldNames");
			return m.invoke(target);
		}
		catch (Exception ex) {
			// Field-names lookup failed; fall through to empty names (canonical form will be
			// "{}" for this object, which is incorrect but will fail loudly in tests rather
			// than silently double-execute).
			return null;
		}
	}

}
