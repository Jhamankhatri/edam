package com.edam.common.exception;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.ErrorCode;
import com.edam.common.logging.CorrelationId;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Terminal handler for the container's error dispatch.
 *
 * <h2>Why a JSON body here, unlike before</h2>
 * This used to render a JSP error page. With the presentation layer removed, EDAM is a REST
 * service whose only client is a React application, so the browser never navigates to
 * {@code /error} in normal operation. What remains is the case where a request fails
 * <em>before</em> reaching a {@code @RestController} — a URL that matches no mapping, a filter
 * exception, a container-level 404. Those must still produce the same envelope as every other error,
 * because a client that has to parse two different error formats will eventually mishandle one.
 *
 * <h2>What it will not do</h2>
 * It never renders the exception, its message or its stack trace. The response carries a
 * status-derived safe message and the correlation id, which is the only detail a client can usefully
 * quote to support and the only one that cannot leak internals.
 */
@Controller
@RequestMapping("${server.error.path:${error.path:/error}}")
public class MvcExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(MvcExceptionHandler.class);

	@RequestMapping
	public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
		// A bare GET /error has no ERROR_STATUS_CODE; 500 is the safe interpretation.
		Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		int statusCode = (statusAttribute instanceof Integer code) ? code : HttpStatus.INTERNAL_SERVER_ERROR.value();
		HttpStatus resolved = HttpStatus.resolve(statusCode);
		HttpStatus status = (resolved != null) ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;

		Object exceptionAttribute = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
		if (status.is5xxServerError() && exceptionAttribute == null) {
			// Container-level failures never reach the advice, so this is the only place they are logged.
			log.error("container.error_dispatch correlationId={} status={} path={}", correlationId(request),
					statusCode, errorPath(request));
		}

		ErrorCode code = (status == HttpStatus.NOT_FOUND) ? ErrorCode.NOT_FOUND
				: (status == HttpStatus.FORBIDDEN) ? ErrorCode.ACCESS_DENIED
				: (status == HttpStatus.UNAUTHORIZED) ? ErrorCode.UNAUTHENTICATED
				: (status.is4xxClientError()) ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_ERROR;
		return ResponseEntity.status(status)
			.body(ApiResponse.failure(code, safeMessage(status), correlationId(request), errorPath(request)));
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

	private static String errorPath(HttpServletRequest request) {
		Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		return (original instanceof String path) ? path : ApiPaths.applicationPath(request);
	}

	private static String correlationId(HttpServletRequest request) {
		String current = CorrelationId.current();
		return (current != null) ? current : CorrelationId.sanitizeOrGenerate(request.getHeader(CorrelationId.HEADER));
	}

}
