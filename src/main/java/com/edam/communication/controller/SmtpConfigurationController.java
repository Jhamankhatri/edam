package com.edam.communication.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.communication.dto.SmtpConfigurationRequest;
import com.edam.communication.dto.SmtpConfigurationResponse;
import com.edam.communication.service.SmtpConfigurationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = com.edam.common.api.ApiPaths.V1 + "/communication/smtp",
		produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "SMTP Configuration", description = "Runtime-reloadable SMTP transport configuration (admin only).")
public class SmtpConfigurationController {

	private final SmtpConfigurationService service;

	public SmtpConfigurationController(SmtpConfigurationService service) { this.service = service; }

	@GetMapping
	@EdamPermission(Permissions.EMAIL_CONFIGURATION_MANAGE)
	@Operation(summary = "Return the active SMTP configuration (password masked)")
	public ApiResponse<SmtpConfigurationResponse> get(HttpServletRequest request) {
		return ApiResponse.success(
				service.getActive().orElse(null),
				CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping
	@EdamPermission(Permissions.EMAIL_CONFIGURATION_MANAGE)
	@Operation(summary = "Create or replace the active SMTP configuration")
	public ResponseEntity<ApiResponse<SmtpConfigurationResponse>> save(
			@Valid @RequestBody SmtpConfigurationRequest body, HttpServletRequest request) {
		SmtpConfigurationResponse saved = service.save(body);
		return ResponseEntity.status(HttpStatus.OK)
				.body(ApiResponse.success(saved, CorrelationId.current(), request.getRequestURI()));
	}

	@PostMapping("/test")
	@EdamPermission(Permissions.EMAIL_CONFIGURATION_MANAGE)
	@Operation(summary = "Send a test email; include body for pre-save validation, omit to test the saved config")
	public ApiResponse<Map<String, Object>> test(
			@RequestParam("recipient") String recipient,
			@RequestBody(required = false) SmtpConfigurationRequest candidate,
			HttpServletRequest request) {
		boolean ok = service.sendTestEmail(recipient, candidate);
		return ApiResponse.success(Map.of("success", ok, "recipient", recipient),
				CorrelationId.current(), request.getRequestURI());
	}
}
