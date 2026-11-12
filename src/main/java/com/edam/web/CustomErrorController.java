package com.edam.web;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.ErrorDetail;
import com.edam.common.exception.ErrorCode;
import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.logging.LogSanitizer;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Custom Spring Boot {@link ErrorController} handling servlet-container errors
 * safely across both REST JSON clients and browser JSP views without leaking internal details.
 */
@Controller
@RequestMapping("${spring.web.error.path:${error.path:/error}}")
public class CustomErrorController implements ErrorController {

    @RequestMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String handleHtmlError(
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        HttpStatus status = resolveStatus(request);
        response.setStatus(status.value());
        model.addAttribute("pageTitle", "Error " + status.value());
        model.addAttribute("statusCode", status.value());
        model.addAttribute("statusText", status.getReasonPhrase());
        model.addAttribute("errorCode", mapErrorCode(status).getCode());
        model.addAttribute("errorMessage", safeMessageForStatus(status));
        model.addAttribute("correlationId", CorrelationIdProvider.currentOrNew());
        return "error/error";
    }

    @RequestMapping
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> handleJsonError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String correlationId = CorrelationIdProvider.currentOrNew();
        Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = LogSanitizer.sanitize(
                originalUri != null ? originalUri.toString() : request.getRequestURI());

        ErrorDetail detail = ErrorDetail.of(
                mapErrorCode(status).getCode(),
                safeMessageForStatus(status),
                path);
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), correlationId, detail));
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode instanceof Integer code) {
            HttpStatus resolved = HttpStatus.resolve(code);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ErrorCode mapErrorCode(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case FORBIDDEN -> ErrorCode.ACCESS_DENIED;
            case UNAUTHORIZED -> ErrorCode.AUTHENTICATION_REQUIRED;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case BAD_REQUEST -> ErrorCode.MALFORMED_REQUEST;
            case CONFLICT -> ErrorCode.CONFLICT;
            case SERVICE_UNAVAILABLE -> ErrorCode.DATABASE_ERROR;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private String safeMessageForStatus(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "The requested page or resource could not be found.";
            case FORBIDDEN -> "You do not have permission to access this resource.";
            case UNAUTHORIZED -> "Authentication is required to access this resource.";
            case METHOD_NOT_ALLOWED -> "The HTTP method is not supported for this resource.";
            case BAD_REQUEST -> "The request could not be processed due to invalid input.";
            default -> "An unexpected application error occurred. Please reference your correlation ID.";
        };
    }
}
