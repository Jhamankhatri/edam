package com.edam.communication.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.communication.dto.TemplateResponse;
import com.edam.communication.dto.TemplateUpsertRequest;
import com.edam.communication.dto.TemplateVersionResponse;
import com.edam.communication.service.EmailTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = com.edam.common.api.ApiPaths.V1 + "/communication/email-templates",
		produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Email Templates", description = "Versioned email templates with placeholder whitelisting and HTML sanitization.")
public class EmailTemplateController {

	private final EmailTemplateService service;

	public EmailTemplateController(EmailTemplateService service) { this.service = service; }

	@GetMapping
	@EdamPermission(Permissions.NOTIFICATION_TEMPLATE_MANAGE)
	@Operation(summary = "List all templates")
	public ApiResponse<List<TemplateResponse>> list(HttpServletRequest request) {
		return ApiResponse.success(service.list(), CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.NOTIFICATION_TEMPLATE_MANAGE)
	@Operation(summary = "Template details (current version)")
	public ApiResponse<TemplateResponse> get(@PathVariable UUID id, HttpServletRequest request) {
		return ApiResponse.success(service.get(id), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping
	@EdamPermission(Permissions.NOTIFICATION_TEMPLATE_MANAGE)
	@Operation(summary = "Create a new template (initial version v1)")
	public ResponseEntity<ApiResponse<TemplateResponse>> create(@Valid @RequestBody TemplateUpsertRequest body,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(service.create(body), CorrelationId.current(), request.getRequestURI()));
	}

	@PostMapping("/{id}/versions")
	@EdamPermission(Permissions.NOTIFICATION_TEMPLATE_MANAGE)
	@Operation(summary = "Create a new immutable version of a template")
	public ResponseEntity<ApiResponse<TemplateVersionResponse>> newVersion(
			@PathVariable UUID id, @Valid @RequestBody TemplateUpsertRequest body, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(service.createVersion(id, body),
						CorrelationId.current(), request.getRequestURI()));
	}

	@GetMapping("/{id}/versions")
	@EdamPermission(Permissions.NOTIFICATION_TEMPLATE_MANAGE)
	@Operation(summary = "List version history of a template (most recent first)")
	public ApiResponse<List<TemplateVersionResponse>> history(@PathVariable UUID id, HttpServletRequest request) {
		return ApiResponse.success(service.history(id), CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping("/{id}/active")
	@EdamPermission(Permissions.NOTIFICATION_TEMPLATE_MANAGE)
	@Operation(summary = "Activate or deactivate a template")
	public ApiResponse<TemplateResponse> setActive(@PathVariable UUID id, @RequestParam("active") boolean active,
			HttpServletRequest request) {
		return ApiResponse.success(service.activate(id, active), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/preview")
	@EdamPermission(Permissions.NOTIFICATION_TEMPLATE_MANAGE)
	@Operation(summary = "Preview a rendered version of the template with sample placeholder values")
	public ApiResponse<Map<String, String>> preview(@PathVariable UUID id,
			@RequestParam(value = "version", required = false) Integer version,
			@RequestBody(required = false) Map<String, String> sampleValues, HttpServletRequest request) {
		return ApiResponse.success(service.preview(id, version, sampleValues),
				CorrelationId.current(), request.getRequestURI());
	}
}
