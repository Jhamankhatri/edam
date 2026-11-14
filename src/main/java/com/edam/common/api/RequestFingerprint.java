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

	/**
	 * Parse and re-serialise JSON so whitespace/key-order/nesting-order differences are normalised
	 * away. Exposed for tests.
	 *
	 * <p>We parse through the canonical mapper, then recursively rewrite every ObjectNode so that
	 * fields are inserted in alphabetical order. Jackson's {@code SORT_PROPERTIES_ALPHABETICALLY}
	 * feature only sorts properties of POJOs; for JsonNode trees, field order follows insertion
	 * order, so we must reorder explicitly. We collect field names from {@code fieldNames()} via
	 * a suppressed-rawtypes Iterator (tools.jackson declares the return type as raw {@code Iterator}
	 * which confuses javac's target-type inference when using a generic witness), sort them, and
	 * rebuild the ObjectNode in sorted order. Arrays are recursed into so nested objects sort too.
	 */
	/**
	 * Canonicalise a JSON string: parse + recursively sort keys + re-serialise. Public so the
	 * aspect and tests can use it without reflection. The {@code jsonMapper} parameter is
	 * accepted for API compatibility but ignored — canonicalisation always uses the internal
	 * canonical mapper (see {@link CanonicalHolder#MAPPER}) so output is byte-identical
	 * regardless of how the caller's mapper is configured.
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
	 * Recursively sort object keys alphabetically; leave arrays and primitives alone. Implemented
	 * by detaching all children from each ObjectNode, sorting the names, and re-inserting into a
	 * new ObjectNode in that order. Because children are removed from the original before being
	 * attached to the sorted replacement, Jackson's "node already owned" invariant holds.
	 */
	@SuppressWarnings("rawtypes")
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
		List<String> names = new ArrayList<>();
		// Collect field names via reflection to avoid javac's target-type inference bug with
		// tools.jackson's raw-Iterator return type. See the helper below for the hierarchy walk.
		Object iter = invokeFieldNames(obj);
		if (iter instanceof Iterator<?> it) {
			while (it.hasNext()) {
				Object n = it.next();
				if (n instanceof String s) {
					names.add(s);
				}
			}
		}
		Collections.sort(names);
		// Detach each child from obj (so it has no parent) and re-add to sorted in order.
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
	 * Reflectively invoke {@code fieldNames()} on a JsonNode, walking the class hierarchy to
	 * find the declaring class (tools.jackson declares this method on {@code ContainerNode}, a
	 * supertype of {@code ObjectNode}, so {@code getMethod} on the leaf class alone may not
	 * resolve it in all environments). The returned object is a raw {@link Iterator} whose
	 * elements are {@link String}s; callers must cast defensively.
	 *
	 * <p>This helper exists solely to work around a javac target-type inference bug: assigning
	 * {@code obj.fieldNames()} directly to a generic-typed variable fails in this build because
	 * tools.jackson declares the return type as raw {@code Iterator}, and javac refuses to
	 * narrow the raw type through the target-type witness. Reflective invocation bypasses the
	 * compile-time generic signature entirely.
	 */
	private static Object invokeFieldNames(Object target) {
		Class<?> c = target.getClass();
		while (c != null) {
			try {
				java.lang.reflect.Method m = c.getDeclaredMethod("fieldNames");
				m.setAccessible(true);
				return m.invoke(target);
			}
			catch (NoSuchMethodException nsme) {
				// fall through to superclass
			}
			catch (Exception ex) {
				// Security / IllegalAccess / InvocationTarget: try superclass before giving up.
			}
			c = c.getSuperclass();
		}
		// Public API lookup as a last resort (method declared on an interface).
		try {
			java.lang.reflect.Method m = target.getClass().getMethod("fieldNames");
			return m.invoke(target);
		}
		catch (Exception ex) {
			return null;
		}
	}

}
