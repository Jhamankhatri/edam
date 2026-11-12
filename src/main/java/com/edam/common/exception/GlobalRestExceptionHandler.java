package com.edam.common.exception;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.ErrorDetail;
import com.edam.common.api.FieldValidationError;
import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.logging.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralized REST exception handler producing standardized {@link ApiResponse} payloads
 * with correlation IDs and safe user-facing messages. Never leaks internal stack traces,
 * SQL statements, or internal class names to clients.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalRestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalRestExceptionHandler.class);

    @ExceptionHandler(EdamException.class)
    public ResponseEntity<ApiResponse<Void>> handleEdamException(
            EdamException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.warn("Domain exception code={} status={} path={} message={}",
                ex.getErrorCode().getCode(),
                ex.getHttpStatus().value(),
                safePath,
                LogSanitizer.sanitize(ex.getMessage()));

        ErrorDetail detail = ErrorDetail.of(ex.getErrorCode().getCode(), ex.getMessage(), safePath);
        return buildErrorResponse(ex.getHttpStatus(), correlationId, detail);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());

        List<FieldValidationError> fieldErrors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new FieldValidationError(
                    fieldError.getField(),
                    safeValidationMessage(fieldError.getDefaultMessage()),
                    fieldError.getCode()));
        }
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            fieldErrors.add(new FieldValidationError(
                    globalError.getObjectName(),
                    safeValidationMessage(globalError.getDefaultMessage()),
                    globalError.getCode()));
        }

        log.warn("Request validation failed path={} errorCount={}", safePath, fieldErrors.size());
        ErrorDetail detail = ErrorDetail.validation(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "Validation failed for the submitted request.",
                safePath,
                fieldErrors);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, correlationId, detail);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());

        List<FieldValidationError> fieldErrors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String paramName = result.getMethodParameter().getParameterName();
            String fieldName = (paramName != null && !paramName.isBlank()) ? paramName : "parameter";
            result.getResolvableErrors().forEach(error -> fieldErrors.add(new FieldValidationError(
                    fieldName,
                    safeValidationMessage(error.getDefaultMessage()),
                    "ValidationError")));
        }

        log.warn("Method parameter validation failed path={} errorCount={}", safePath, fieldErrors.size());
        ErrorDetail detail = ErrorDetail.validation(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "Validation failed for one or more request parameters.",
                safePath,
                fieldErrors);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, correlationId, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());

        List<FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new FieldValidationError(
                        v.getPropertyPath().toString(),
                        safeValidationMessage(v.getMessage()),
                        "ConstraintViolation"))
                .toList();

        log.warn("Constraint violation path={} errorCount={}", safePath, fieldErrors.size());
        ErrorDetail detail = ErrorDetail.validation(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "Validation failed for one or more request values.",
                safePath,
                fieldErrors);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, correlationId, detail);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(
            Exception ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.warn("Malformed request path={} type={}", safePath, ex.getClass().getSimpleName());

        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.MALFORMED_REQUEST.getCode(),
                "The request payload or parameters could not be parsed.",
                safePath);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, correlationId, detail);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.warn("Resource not found path={}", safePath);

        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "The requested resource was not found.",
                safePath);
        return buildErrorResponse(HttpStatus.NOT_FOUND, correlationId, detail);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.warn("Method not allowed path={} method={}", safePath, LogSanitizer.sanitize(ex.getMethod()));

        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.METHOD_NOT_ALLOWED.getCode(),
                "The HTTP method is not supported for this endpoint.",
                safePath);
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, correlationId, detail);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.warn("Authentication failure path={}", safePath);

        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.AUTHENTICATION_REQUIRED.getCode(),
                "Authentication is required to access this resource.",
                safePath);
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, correlationId, detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.warn("Access denied path={}", safePath);

        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.ACCESS_DENIED.getCode(),
                "You do not have permission to perform this operation.",
                safePath);
        return buildErrorResponse(HttpStatus.FORBIDDEN, correlationId, detail);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.warn("Optimistic locking conflict path={}", safePath, ex);

        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.CONFLICT.getCode(),
                "The record was modified by another user. Please refresh and retry.",
                safePath);
        return buildErrorResponse(HttpStatus.CONFLICT, correlationId, detail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.warn("Database integrity violation path={}", safePath, ex);

        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.CONFLICT.getCode(),
                "The operation conflicts with existing data integrity constraints.",
                safePath);
        return buildErrorResponse(HttpStatus.CONFLICT, correlationId, detail);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(
            DataAccessException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.error("Database access failure path={}", safePath, ex);

        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.DATABASE_ERROR.getCode(),
                "A data persistence error occurred. Please reference the correlation ID if the issue persists.",
                safePath);
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, correlationId, detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdProvider.currentOrNew();
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        log.error("Unhandled REST exception path={}", safePath, ex);

        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.INTERNAL_ERROR.getCode(),
                "An unexpected application error occurred. Please reference the correlation ID when contacting support.",
                safePath);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, correlationId, detail);
    }

    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
            HttpStatus status,
            String correlationId,
            ErrorDetail detail) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), correlationId, detail));
    }

    private String safeValidationMessage(String raw) {
        return (raw == null || raw.isBlank()) ? "Invalid value" : raw;
    }
}
