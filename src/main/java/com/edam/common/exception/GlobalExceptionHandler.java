package com.edam.common.exception;

import java.util.List;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.ApiResponse.FieldErrorDetail;
import com.edam.common.api.ApiPaths;
import com.edam.common.api.ErrorCode;
import com.edam.common.logging.CorrelationId;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Central translation of exceptions into {@link ApiResponse} envelopes for API clients.
 *
 * <p>Rules enforced here:
 * <ul>
 * <li>Internal detail (stack traces, SQL, class names, framework messages) never reaches
 * a client.</li>
 * <li>Every response carries the correlation ID; the same ID appears in the server log
 * with the full diagnostic record.</li>
 * <li>Expected conditions (validation, not found, access denied) are logged at WARN
 * without stack traces. Unexpected conditions are logged at ERROR with the stack
 * trace.</li>
 * <li>HTML requests never reach this advice for handled conditions; the servlet container
 * error path renders the MVC error page instead (see {@code MvcExceptionHandler}).</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// ------------------------------------------------------------------------
	// Validation and request binding
	// ------------------------------------------------------------------------

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<FieldErrorDetail> fieldErrors = ex.getBindingResult()
			.getAllErrors()
			.stream()
			.map(error -> new FieldErrorDetail(
					(error instanceof org.springframework.validation.FieldError fe) ? fe.getField()
							: error.getObjectName(),
					error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid"))
			.toList();
		log.warn("request.validation_failed errors={}", fieldErrors.size());
		return respond(HttpStatus.BAD_REQUEST, ApiResponse.validationFailure(fieldErrors, correlationId(request),
				ApiPaths.applicationPath(request)));
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex, HttpServletRequest request) {
		ValidationFailedException converted = ValidationFailedException.from(ex.getBindingResult());
		log.warn("request.binding_failed errors={}", converted.getFieldErrors().size());
		return respond(HttpStatus.BAD_REQUEST,
				ApiResponse.validationFailure(converted.getFieldErrors(), correlationId(request),
						ApiPaths.applicationPath(request)));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex,
			HttpServletRequest request) {
		// Spring 7 exposes per-parameter results; the parameter name is the field path.
		List<FieldErrorDetail> fieldErrors = ex.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors()
				.stream()
				.map(error -> new FieldErrorDetail(parameterName(result.getMethodParameter()),
						error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid")))
			.toList();
		log.warn("request.handler_validation_failed errors={}", fieldErrors.size());
		return respond(HttpStatus.BAD_REQUEST, ApiResponse.validationFailure(fieldErrors, correlationId(request),
				ApiPaths.applicationPath(request)));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations()
			.stream()
			.map(GlobalExceptionHandler::toFieldError)
			.toList();
		log.warn("request.constraint_violation errors={}", fieldErrors.size());
		return respond(HttpStatus.BAD_REQUEST, ApiResponse.validationFailure(fieldErrors, correlationId(request),
				ApiPaths.applicationPath(request)));
	}

	@ExceptionHandler(ValidationFailedException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationFailed(ValidationFailedException ex,
			HttpServletRequest request) {
		log.warn("request.validation_failed errors={}", ex.getFieldErrors().size());
		return respond(HttpStatus.BAD_REQUEST,
				ApiResponse.validationFailure(ex.getFieldErrors(), correlationId(request),
						ApiPaths.applicationPath(request)));
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class, HttpRequestMethodNotSupportedException.class })
	public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex, HttpServletRequest request) {
		// The framework messages for these exceptions can leak type names, so they are
		// logged (WARN, no stack trace) but never returned.
		log.warn("request.malformed type={} detail={}", ex.getClass().getSimpleName(), ex.getMessage());
		return respond(HttpStatus.BAD_REQUEST, ApiResponse.failure(ErrorCode.INVALID_REQUEST,
				ErrorCode.INVALID_REQUEST.safeMessage(), correlationId(request), ApiPaths.applicationPath(request)));
	}

	// ------------------------------------------------------------------------
	// Business conditions
	// ------------------------------------------------------------------------

	@ExceptionHandler(EdamException.class)
	public ResponseEntity<ApiResponse<Void>> handleEdamException(EdamException ex, HttpServletRequest request) {
		HttpStatus status = statusFor(ex.getErrorCode());
		log.warn("business.rule_violation code={} status={} message={}", ex.getErrorCode().code(), status.value(),
				ex.getMessage());
		return respond(status, ApiResponse.failure(ex.getErrorCode(), ex.getMessage(), correlationId(request),
				ApiPaths.applicationPath(request)));
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
		log.warn("request.no_handler path={}", ApiPaths.applicationPath(request));
		return respond(HttpStatus.NOT_FOUND, ApiResponse.failure(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.safeMessage(),
				correlationId(request), ApiPaths.applicationPath(request)));
	}

	// ------------------------------------------------------------------------
	// Security
	// ------------------------------------------------------------------------

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		log.warn("security.access_denied path={}", ApiPaths.applicationPath(request));
		return respond(HttpStatus.FORBIDDEN, ApiResponse.failure(ErrorCode.ACCESS_DENIED,
				ErrorCode.ACCESS_DENIED.safeMessage(), correlationId(request), ApiPaths.applicationPath(request)));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex,
			HttpServletRequest request) {
		log.warn("security.authentication_failed type={}", ex.getClass().getSimpleName());
		return respond(HttpStatus.UNAUTHORIZED, ApiResponse.failure(ErrorCode.UNAUTHENTICATED,
				ErrorCode.UNAUTHENTICATED.safeMessage(), correlationId(request), ApiPaths.applicationPath(request)));
	}

	// ------------------------------------------------------------------------
	// Persistence
	// ------------------------------------------------------------------------

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex,
			HttpServletRequest request) {
		// The entity name is internal detail for the log only; the caller sees a safe code.
		log.warn("persistence.optimistic_lock_conflict type={}", ex.getClass().getSimpleName());
		return respond(HttpStatus.CONFLICT, ApiResponse.failure(ErrorCode.CONCURRENT_MODIFICATION,
				ErrorCode.CONCURRENT_MODIFICATION.safeMessage(), correlationId(request),
				ApiPaths.applicationPath(request)));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		// Constraint names and SQL are internal: log the root cause only.
		log.error("persistence.integrity_violation correlationId={} cause={}", correlationId(request),
				rootCauseMessage(ex));
		return respond(HttpStatus.CONFLICT, ApiResponse.failure(ErrorCode.PERSISTENCE_ERROR,
				ErrorCode.PERSISTENCE_ERROR.safeMessage(), correlationId(request), ApiPaths.applicationPath(request)));
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
		log.error("persistence.failure correlationId={} type={}", correlationId(request), ex.getClass().getSimpleName(),
				ex);
		return respond(HttpStatus.SERVICE_UNAVAILABLE, ApiResponse.failure(ErrorCode.PERSISTENCE_ERROR,
				ErrorCode.PERSISTENCE_ERROR.safeMessage(), correlationId(request), ApiPaths.applicationPath(request)));
	}

	// ------------------------------------------------------------------------
	// Catch-all
	// ------------------------------------------------------------------------

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
		// The correlation ID is the contract with the caller; the stack trace stays here.
		log.error("application.unexpected_error correlationId={} type={}", correlationId(request),
				ex.getClass().getName(), ex);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, ApiResponse.failure(ErrorCode.INTERNAL_ERROR,
				ErrorCode.INTERNAL_ERROR.safeMessage(), correlationId(request), ApiPaths.applicationPath(request)));
	}

	// ------------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------------

	private static String parameterName(org.springframework.core.MethodParameter parameter) {
		String name = parameter.getParameterName();
		return (name != null && !name.isBlank()) ? name : "request";
	}

	private static FieldErrorDetail toFieldError(ConstraintViolation<?> violation) {
		String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "request";
		return new FieldErrorDetail(path, violation.getMessage());
	}

	private static HttpStatus statusFor(ErrorCode errorCode) {
		return switch (errorCode) {
			case VALIDATION_FAILED, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
			case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
			case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case CONCURRENT_MODIFICATION, PERSISTENCE_ERROR -> HttpStatus.CONFLICT;
			case BUSINESS_RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT;
			case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}

	private static ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, ApiResponse<Void> body) {
		return ResponseEntity.status(status)
			.contentType(MediaType.APPLICATION_JSON)
			.header("Cache-Control", "no-store")
			.headers(headers -> headers.add(HttpHeaders.PRAGMA, "no-cache"))
			.body(body);
	}

	private static String correlationId(HttpServletRequest request) {
		String current = CorrelationId.current();
		if (current != null) {
			return current;
		}
		String header = request.getHeader(CorrelationId.HEADER);
		return CorrelationId.sanitizeOrGenerate(header);
	}

	private static String rootCauseMessage(Throwable throwable) {
		Throwable root = throwable;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String message = root.getMessage();
		return (message != null) ? message : root.getClass().getSimpleName();
	}

}
