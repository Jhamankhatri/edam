package com.edam.common.exception;

import com.edam.common.api.ApiPaths;
import com.edam.common.logging.CorrelationId;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * HTML (JSP) counterpart of {@link GlobalExceptionHandler}.
 *
 * <p>Spring Boot documents that a custom {@code error.jsp} does not override the default
 * error view, so EDAM maps the container's error dispatch target here and renders its own
 * MVC error page. The advice's JSON envelopes are bypassed because the container re-enters
 * the dispatcher with a fresh request; a second REST advice would risk leaking details, so
 * this controller maps only status to a safe page.
 *
 * <p>The template receives only a status-derived title, a safe message and the
 * correlation ID. It never receives the exception object.
 */
@Controller
@RequestMapping("${server.error.path:${error.path:/error}}")
public class MvcExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(MvcExceptionHandler.class);

	@GetMapping
	public ModelAndView error(HttpServletRequest request) {
		Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		int statusCode = (statusAttribute instanceof Integer code) ? code : HttpStatus.INTERNAL_SERVER_ERROR.value();
		HttpStatus status = HttpStatus.resolve(statusCode) != null ? HttpStatus.resolve(statusCode)
				: HttpStatus.INTERNAL_SERVER_ERROR;

		Object exceptionAttribute = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
		if (status.is5xxServerError() && exceptionAttribute == null) {
			// Container-level failures (e.g. a JSP render error) never reach the advice.
			log.error("mvc.error_page correlationId={} status={} path={}", correlationId(request), statusCode,
					errorPath(request));
		}

		ModelAndView modelAndView = new ModelAndView("error");
		modelAndView.addObject("statusCode", status.value());
		modelAndView.addObject("statusReason", status.getReasonPhrase());
		modelAndView.addObject("statusMessage", safeMessage(status));
		modelAndView.addObject("correlationId", correlationId(request));
		modelAndView.setStatus(status);
		return modelAndView;
	}

	private static String safeMessage(HttpStatus status) {
		if (status == HttpStatus.NOT_FOUND) {
			return "The page or record you requested could not be found.";
		}
		if (status == HttpStatus.FORBIDDEN) {
			return "You are not permitted to open this page.";
		}
		if (status == HttpStatus.UNAUTHORIZED) {
			return "Please sign in to continue.";
		}
		if (status.is4xxClientError()) {
			return "The request could not be completed as sent.";
		}
		return "An unexpected error occurred. Quote the reference below when contacting support.";
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
