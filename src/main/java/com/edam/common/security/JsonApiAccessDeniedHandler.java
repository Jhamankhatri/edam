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
 * Authorization failure at the filter level, answered with the JSON envelope for every caller.
 *
 * <p>An earlier revision redirected browsers to an HTML {@code /access-denied} page. With the view
 * layer removed there is no page to redirect to, and a 302 would be actively misleading: the client
 * (React, curl, Postman) needs the 403 and the correlation id, not a navigation.
 *
 * <p>The distinction between {@code ACCESS_DENIED} (EDAM-2002) and the aspect's
 * {@code PERMISSION_DENIED} (EDAM-2005) is intentional. This handler fires when the security chain or a
 * method-security annotation rejects the call before any business code runs; the aspect fires inside the
 * controller. Keeping them distinct lets an operator tell "the URL is protected" from "this endpoint
 * asked for a capability the user lacks", which are different configuration mistakes.
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
		// Non-API path: still answer with a status, never a redirect. There is no HTML surface left.
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		String correlationId = CorrelationId.current() != null ? CorrelationId.current() : CorrelationId.generate();
		response.setHeader(CorrelationId.HEADER, correlationId);
		this.jsonMapper.writeValue(response.getOutputStream(), ApiResponse.failure(ErrorCode.ACCESS_DENIED,
				ErrorCode.ACCESS_DENIED.safeMessage(), correlationId, ApiPaths.applicationPath(request)));
	}

}
