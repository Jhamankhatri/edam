package com.edam.common.security;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.ErrorDetail;
import com.edam.common.exception.ErrorCode;
import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.logging.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

/**
 * Handles unauthenticated access attempts consistently:
 * returns standardized JSON 401 {@link ApiResponse} for API clients and redirects browser traffic to {@code /login}.
 */
public class EdamAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(EdamAuthenticationEntryPoint.class);

    private final JsonMapper jsonMapper;

    public EdamAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        String correlationId = CorrelationIdProvider.currentOrNew();
        log.warn("Unauthenticated access blocked path={}", safePath);

        if (SecurityRequestSupport.isApiOrJsonRequest(request, safePath)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ErrorDetail detail = ErrorDetail.of(
                    ErrorCode.AUTHENTICATION_REQUIRED.getCode(),
                    "Authentication is required to access this resource.",
                    safePath);
            jsonMapper.writeValue(
                    response.getWriter(),
                    ApiResponse.error(HttpServletResponse.SC_UNAUTHORIZED, correlationId, detail));
            return;
        }

        response.sendRedirect(request.getContextPath() + "/login");
    }
}
