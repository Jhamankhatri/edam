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
			String body = cached.cachedBodyAsString();
			if (request.getAttribute("X-Idempotency-CachedBodyLen") == null) {
				request.setAttribute("X-Idempotency-CachedBodyLen", body.length());
			}
			return body;
		}
		// Requests that aren't wrapped carry no body (GET/HEAD) or should not be fingerprinted;
		// returning an empty string is safe because those endpoints are not annotated.
		// But if content-length suggests there IS a body, it means the filter didn't wrap and we'd
		// compute an empty fingerprint (a dangerous false-match). Read the stream directly as a
		// fallback.
		if (request.getContentLength() > 0) {
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

	/** Parse and re-serialise JSON so whitespace/key-order differences are normalised away. */
	static String canonicalJson(JsonMapper jsonMapper, String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		try {
			JsonNode tree = jsonMapper.readTree(body);
			return jsonMapper.writeValueAsString(tree);
		}
		catch (RuntimeException ex) {
			// Not JSON (e.g. form-urlencoded or empty); fall back to the raw trimmed body.
			// tools.jackson.core.exc.StreamReadException is a RuntimeException in this Jackson variant.
			return body.trim();
		}
	}

}
