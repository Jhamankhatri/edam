package com.edam.common.security;

import java.io.IOException;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.ApiPaths;
import com.edam.common.api.ErrorCode;
import com.edam.common.logging.CorrelationId;

import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Authorization failure at the filter level: JSON envelope for API callers, HTML
 * {@code /access-denied} page for browsers.
 */
public class JsonApiAccessDeniedHandler implements AccessDeniedHandler {

	private static final Logger log = LoggerFactory.getLogger(JsonApiAccessDeniedHandler.class);

	private final JsonMapper jsonMapper;

	public JsonApiAccessDeniedHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException, ServletException {
		log.warn("security.access_denied path={}", ApiPaths.applicationPath(request));
		if (ApiPaths.wantsApiResponse(request)) {
			String correlationId = CorrelationId.current() != null ? CorrelationId.current()
					: CorrelationId.generate();
			ApiResponse<Void> body = ApiResponse.failure(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.safeMessage(),
					correlationId, ApiPaths.applicationPath(request));
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			response.setHeader(CorrelationId.HEADER, correlationId);
			this.jsonMapper.writeValue(response.getOutputStream(), body);
			return;
		}
		response.sendRedirect(request.getContextPath() + "/access-denied");
	}

}
