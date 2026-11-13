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
 * <p>Every unauthenticated request receives the standard JSON envelope with {@code 401}. A redirect to
 * a login page was correct when EDAM shipped server-rendered views; now that it is an API with a
 * separately deployed React client, a redirect would break the client's error handling and, worse, hide
 * an authentication failure inside a navigation that a fetch wrapper may not even follow.
 */
public class JsonApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final JsonMapper jsonMapper;

	public JsonApiAuthenticationEntryPoint(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		// Every caller gets the same 401 envelope. There is no HTML sign-in page to redirect to,
		// and a 302 to one would hide an authentication failure behind a navigation.
		writeEnvelope(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
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
