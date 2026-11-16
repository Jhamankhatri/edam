package com.edam.communication.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.communication.dto.EventConfigRequest;
import com.edam.communication.dto.EventConfigResponse;
import com.edam.communication.service.NotificationEventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = com.edam.common.api.ApiPaths.V1 + "/communication/events",
		produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Notification Event Config", description = "Routing configuration for supported workflow events.")
public class NotificationEventConfigController {

	private final NotificationEventService service;

	public NotificationEventConfigController(NotificationEventService service) { this.service = service; }

	@GetMapping
	@EdamPermission(Permissions.NOTIFICATION_TEMPLATE_MANAGE)
	@Operation(summary = "List supported events and their routing configuration")
	public ApiResponse<List<EventConfigResponse>> list(HttpServletRequest request) {
		return ApiResponse.success(service.list(), CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping("/{eventCode}")
	@EdamPermission(Permissions.NOTIFICATION_TEMPLATE_MANAGE)
	@Operation(summary = "Update channel/template/group routing for an event")
	public ApiResponse<EventConfigResponse> update(@PathVariable String eventCode,
			@Valid @RequestBody EventConfigRequest body, HttpServletRequest request) {
		return ApiResponse.success(service.update(eventCode, body), CorrelationId.current(), request.getRequestURI());
	}
}
