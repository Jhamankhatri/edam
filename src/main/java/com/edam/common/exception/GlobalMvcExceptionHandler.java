package com.edam.common.exception;

import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.logging.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralized MVC exception handler for JSP view controllers.
 * Renders a safe user-facing error view carrying a correlation ID without exposing
 * internal stack traces, SQL queries, or class names.
 */
@ControllerAdvice(annotations = Controller.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalMvcExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalMvcExceptionHandler.class);

    @ExceptionHandler(EdamException.class)
    public String handleEdamException(
            EdamException ex,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        log.warn("MVC domain exception code={} path={} message={}",
                ex.getErrorCode().getCode(),
                LogSanitizer.sanitize(request.getRequestURI()),
                LogSanitizer.sanitize(ex.getMessage()));
        return renderErrorView(
                ex.getHttpStatus(),
                ex.getErrorCode().getCode(),
                ex.getMessage(),
                response,
                model);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        log.warn("MVC access denied path={}", LogSanitizer.sanitize(request.getRequestURI()));
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("statusCode", HttpStatus.FORBIDDEN.value());
        model.addAttribute("errorCode", ErrorCode.ACCESS_DENIED.getCode());
        model.addAttribute("errorMessage", "You do not have permission to access this page.");
        model.addAttribute("correlationId", CorrelationIdProvider.currentOrNew());
        return "error/access-denied";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNotFound(
            NoResourceFoundException ex,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        log.warn("MVC page not found path={}", LogSanitizer.sanitize(request.getRequestURI()));
        return renderErrorView(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "The requested page or resource could not be found.",
                response,
                model);
    }

    @ExceptionHandler(DataAccessException.class)
    public String handleDatabaseFailure(
            DataAccessException ex,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        log.error("MVC database failure path={}", LogSanitizer.sanitize(request.getRequestURI()), ex);
        return renderErrorView(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.DATABASE_ERROR.getCode(),
                "The service is temporarily unable to process database requests. Please reference your correlation ID.",
                response,
                model);
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(
            Exception ex,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        log.error("Unhandled MVC exception path={}", LogSanitizer.sanitize(request.getRequestURI()), ex);
        return renderErrorView(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR.getCode(),
                "An unexpected error occurred while rendering this page. Please reference your correlation ID.",
                response,
                model);
    }

    private String renderErrorView(
            HttpStatus status,
            String errorCode,
            String safeMessage,
            HttpServletResponse response,
            Model model) {
        response.setStatus(status.value());
        model.addAttribute("statusCode", status.value());
        model.addAttribute("statusText", status.getReasonPhrase());
        model.addAttribute("errorCode", errorCode);
        model.addAttribute("errorMessage", safeMessage);
        model.addAttribute("correlationId", CorrelationIdProvider.currentOrNew());
        return "error/error";
    }
}
