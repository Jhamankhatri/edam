package com.edam.common.security;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.ErrorDetail;
import com.edam.common.exception.ErrorCode;
import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.logging.LogSanitizer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Handles authorization denials consistently across REST endpoints (JSON 403)
 * and MVC browser views ({@code /access-denied}).
 */
public class EdamAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(EdamAccessDeniedHandler.class);

    private final JsonMapper jsonMapper;

    public EdamAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        String safePath = LogSanitizer.sanitize(request.getRequestURI());
        String correlationId = CorrelationIdProvider.currentOrNew();
        log.warn("Authorization denied path={}", safePath);

        if (SecurityRequestSupport.isApiOrJsonRequest(request, safePath)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ErrorDetail detail = ErrorDetail.of(
                    ErrorCode.ACCESS_DENIED.getCode(),
                    "You do not have permission to perform this operation.",
                    safePath);
            jsonMapper.writeValue(
                    response.getWriter(),
                    ApiResponse.error(HttpServletResponse.SC_FORBIDDEN, correlationId, detail));
            return;
        }

        request.getRequestDispatcher("/access-denied").forward(request, response);
    }
}
