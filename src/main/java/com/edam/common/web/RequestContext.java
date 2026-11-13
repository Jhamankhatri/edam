package com.edam.common.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped facts that services legitimately need but must not receive as method arguments
 * threaded through every layer.
 *
 * <p>Currently: the client address for audit records. Deliberately narrow — this is <em>not</em> a
 * general "current request" escape hatch, because letting services read headers or bodies from
 * here is how request data starts leaking into business logic.
 */
@Component
@RequestScope
public class RequestContext {

	private final HttpServletRequest request;

	public RequestContext(HttpServletRequest request) {
		this.request = request;
	}

	/**
	 * Client address as observed by the server.
	 *
	 * <p>{@code X-Forwarded-For} is <strong>not</strong> trusted here: it is client-controlled and
	 * only a proxy that is known to overwrite it makes it trustworthy. The correct renderer is
	 * {@code server.forward-headers-strategy}, which is configured for the {@code prod} profile and
	 * makes {@code getRemoteAddr()} the real client behind the approved proxy.
	 */
	public String clientIp() {
		return this.request.getRemoteAddr();
	}

	/**
	 * User-Agent header, truncated to the audit column's width.
	 *
	 * <p>Recorded because "which client made this call" is frequently the only way to distinguish an
	 * automated integration from a human action during an incident review. It is client-controlled and
	 * therefore never used for any decision — only stored.
	 */
	public String userAgent() {
		String agent = this.request.getHeader("User-Agent");
		if (agent == null) {
			return null;
		}
		int max = 500;
		return agent.length() <= max ? agent : agent.substring(0, max);
	}

	/** Request URI without query string, for logging and audit summaries. */
	public String path() {
		return this.request.getRequestURI();
	}

}
