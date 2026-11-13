package com.edam.common.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The API path contract, plus helpers for deriving a safe request path for error envelopes.
 *
 * <h2>Versioning</h2>
 * Every business endpoint lives under {@link #V1}. The version is in the path rather than a header so
 * that it is visible in logs, proxies and the OpenAPI document, and so that a browser, curl and Postman
 * all address the same resource unambiguously. A future {@code /api/v2} can coexist: the old one is
 * retired on the bank's schedule, not on a deployment.
 *
 * <p>Controllers must build their mapping from this constant instead of repeating the literal, so
 * "some endpoints are versioned and some are not" cannot happen by accident.
 */
public final class ApiPaths {

	/** Versioned prefix for every business endpoint. */
	public static final String V1 = "/api/v1";

	private ApiPaths() {
	}

	/**
	 * True when the response should be a JSON envelope.
	 *
	 * <p>Everything under {@link #V1} is an API path by definition. The content-type and Accept checks
	 * remain because the health probes and the container's error dispatch sit outside that prefix, and
	 * answering a JSON probe with an HTML error page would break the monitoring that consumes it.
	 */
	public static boolean wantsApiResponse(HttpServletRequest request) {
		String path = applicationPath(request);
		if (path.equals("/api") || path.startsWith("/api/")) {
			return true;
		}
		String contentType = request.getContentType();
		if (contentType != null && contentType.contains("application/json")) {
			return true;
		}
		String accept = request.getHeader("Accept");
		return accept != null && accept.contains("application/json") && !accept.contains("text/html");
	}

	/** Request URI with the context path removed; safe for logging and error envelopes. */
	public static String applicationPath(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri == null) {
			return "/";
		}
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
			return uri.substring(contextPath.length());
		}
		return uri;
	}

}
