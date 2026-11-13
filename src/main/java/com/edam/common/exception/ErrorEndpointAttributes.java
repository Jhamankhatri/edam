package com.edam.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.ErrorCode;
import com.edam.common.logging.CorrelationId;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Makes the container's error dispatch answer in EDAM's envelope.
 *
 * <h2>Why an {@link ErrorAttributes} bean rather than our own error controller</h2>
 * The first attempt declared a {@code @Controller} mapped to {@code /error}. That collides with Spring
 * Boot's {@code BasicErrorController}, which is registered for the same paths and for both producers —
 * the context then fails with "Ambiguous mapping", which is a startup failure rather than a runtime one, so
 * it is caught immediately. Boot already owns {@code /error}; the supported extension point is the
 * {@code ErrorAttributes} it renders, and using it means the dispatch semantics (status, exception
 * attributes, content negotiation) stay exactly as the framework tests them.
 *
 * <h2>What reaches the client, and what never does</h2>
 * A status-derived {@link ErrorCode}, a safe message chosen per status family, the correlation id and the
 * request path. The exception, its message and its stack trace are never included: this path exists for
 * failures that happen <em>before</em> the advice can see them, which is exactly when a leaked internal
 * detail is most likely.
 *
 * <h2>Why the attributes map is built here rather than serialised directly</h2>
 * Boot serialises the returned map itself, honouring the client's {@code Accept} header and its own
 * {@code include-message}/{@code include-stacktrace} settings. Producing an {@link ApiResponse} value in
 * the map keeps a single envelope definition, so the container error body is byte-identical in shape to
 * every controller error body.
 */
@Component
public class ErrorEndpointAttributes extends DefaultErrorAttributes {

	/** Attribute key under which the client-facing envelope is exposed. */
	public static final String ENVELOPE_ATTRIBUTE = "edamEnvelope";

	@Override
	public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
		HttpServletRequest request = (requestOf(webRequest));
		HttpStatus status = resolveStatus(webRequest);
		String path = resolvePath(request);
		String correlationId = (CorrelationId.current() != null) ? CorrelationId.current()
				: CorrelationId.generate();
		ErrorCode code = codeFor(status);
		ApiResponse<Void> envelope = ApiResponse.failure(code, safeMessage(status), correlationId, path);

		Map<String, Object> attributes = new LinkedHashMap<>();
		// The envelope is nested so a client sees the identical {success,error,correlationId,path,timestamp}
		// shape whether the failure came from a controller or from the container.
		attributes.put(ENVELOPE_ATTRIBUTE, envelope);
		attributes.put("status", status.value());
		attributes.put("error", status.getReasonPhrase());
		attributes.put("path", path);
		return attributes;
	}

	private static HttpServletRequest requestOf(WebRequest webRequest) {
		if (webRequest instanceof ServletWebRequest servletWebRequest) {
			return servletWebRequest.getRequest();
		}
		return null;
	}

	private static HttpStatus resolveStatus(WebRequest webRequest) {
		Object statusAttribute = (webRequest instanceof ServletWebRequest servletWebRequest)
				? servletWebRequest.getRequest().getAttribute(RequestDispatcher.ERROR_STATUS_CODE) : null;
		if (statusAttribute instanceof Integer statusCode) {
			HttpStatus resolved = HttpStatus.resolve(statusCode);
			return (resolved == null) ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
		}
		Integer frameworkStatus = (Integer) webRequest.getAttribute("jakarta.servlet.error.status_code",
				WebRequest.SCOPE_REQUEST);
		if (frameworkStatus != null) {
			HttpStatus resolved = HttpStatus.resolve(frameworkStatus);
			return (resolved == null) ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

	private static String resolvePath(HttpServletRequest request) {
		if (request == null) {
			return "/";
		}
		Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		return (original instanceof String uri) ? uri : com.edam.common.api.ApiPaths.applicationPath(request);
	}

	private static ErrorCode codeFor(HttpStatus status) {
		if (status == HttpStatus.NOT_FOUND) {
			return ErrorCode.NOT_FOUND;
		}
		if (status == HttpStatus.FORBIDDEN) {
			return ErrorCode.ACCESS_DENIED;
		}
		if (status == HttpStatus.UNAUTHORIZED) {
			return ErrorCode.UNAUTHENTICATED;
		}
		if (status == HttpStatus.PAYLOAD_TOO_LARGE) {
			return ErrorCode.INVALID_DOCUMENT;
		}
		if (status.is4xxClientError()) {
			return ErrorCode.INVALID_REQUEST;
		}
		return ErrorCode.INTERNAL_ERROR;
	}

	private static String safeMessage(HttpStatus status) {
		if (status == HttpStatus.NOT_FOUND) {
			return "The requested resource could not be found.";
		}
		if (status == HttpStatus.FORBIDDEN) {
			return "You are not permitted to perform this operation.";
		}
		if (status == HttpStatus.UNAUTHORIZED) {
			return "Sign in is required.";
		}
		if (status == HttpStatus.METHOD_NOT_ALLOWED) {
			return "That HTTP method is not supported for this resource.";
		}
		if (status.is4xxClientError()) {
			return "The request could not be processed as received.";
		}
		return "An unexpected error occurred.";
	}

}
