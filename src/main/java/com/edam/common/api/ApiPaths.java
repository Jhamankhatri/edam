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
	 * True when the response should be a JSON envelope rather than an HTML error page.
	 *
	 * <p>API requests are identified by their URI prefix (after removing the context
	 * path) and by an explicit {@code Accept: application/json} header. JSP pages and
	 * form posts are deliberately excluded even when a browser sends a JSON-friendly
	 * {@code Accept} header chain.
	 */
	public static boolean wantsApiResponse(HttpServletRequest request) {
		String path = applicationPath(request);
		if (path.startsWith("/api/") || path.equals("/api")) {
			return true;
		}
		String accept = request.getHeader("Accept");
		if (accept != null && accept.contains("application/json")) {
			String contentType = request.getContentType();
			// Form submissions from JSP pages must keep the HTML flow.
			if (contentType == null || !contentType.contains("application/x-www-form-urlencoded")) {
				return true;
			}
		}
		return false;
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
