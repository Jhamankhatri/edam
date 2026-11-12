package com.edam.common.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Helpers for deciding whether a request expects an API response or an HTML page, and for
 * deriving a safe request path for error envelopes.
 */
public final class ApiPaths {

	private ApiPaths() {
	}

	/**
	 * True when the response should be a JSON envelope rather than an HTML page.
	 *
	 * <p>API requests are identified by URI prefix (after removing the context path), by a
	 * JSON content type, or by an explicit {@code Accept: application/json}. Classic JSP
	 * form posts send {@code application/x-www-form-urlencoded} and are routed to the HTML
	 * flow even though the browser's {@code Accept} header prefers HTML with a JSON fallback.
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
