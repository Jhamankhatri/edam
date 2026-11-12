package com.edam.common.security;

import java.io.IOException;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.ApiPaths;
import com.edam.common.api.ErrorCode;
import com.edam.common.logging.CorrelationId;

import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Authentication failure at the filter level.
 *
 * <p>API callers receive the standard JSON envelope; browser navigations are redirected to
 * the login page. The two audiences are distinguished explicitly so that an unauthenticated
 * AJAX call never receives a redirect to an HTML page.
 */
public class JsonApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final JsonMapper jsonMapper;

	public JsonApiAuthenticationEntryPoint(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		if (ApiPaths.wantsApiResponse(request)) {
			writeEnvelope(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
			return;
		}
		response.sendRedirect(request.getContextPath() + "/login");
	}

	private void writeEnvelope(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
			ErrorCode errorCode) throws IOException {
		String correlationId = CorrelationId.current() != null ? CorrelationId.current()
				: CorrelationId.generate();
		ApiResponse<Void> body = ApiResponse.failure(errorCode, errorCode.safeMessage(), correlationId,
				ApiPaths.applicationPath(request));
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.setHeader(CorrelationId.HEADER, correlationId);
		response.setHeader("Cache-Control", "no-store");
		this.jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
