package com.edam.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Small operational endpoints that prove the platform plumbing (envelope, correlation ID,
 * JSON serialization) without exposing business data.
 *
 * <p>The Actuator health endpoint remains the authoritative liveness/readiness probe;
 * {@code /api/v1/system/info} exists so integrators have a stable, versioned endpoint for
 * application metadata and so the API error/response contract is exercised by tests.
 */
@RestController
@RequestMapping(path = "/api/v1/system", produces = MediaType.APPLICATION_JSON_VALUE)
public class SystemController {

	private final String applicationName;

	private final String applicationVersion;

	public SystemController(@Value("${spring.application.name:edam}") String applicationName,
			@Value("${edam.application.version:unknown}") String applicationVersion) {
		this.applicationName = applicationName;
		this.applicationVersion = applicationVersion;
	}

	@GetMapping("/info")
	public ResponseEntity<ApiResponse<Map<String, Object>>> info(HttpServletRequest request) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("application", this.applicationName);
		data.put("version", this.applicationVersion);
		data.put("status", "UP");
		return ResponseEntity.ok(ApiResponse.success(data, CorrelationId.current(), request.getRequestURI()));
	}

}
