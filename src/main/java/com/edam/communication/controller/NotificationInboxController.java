package com.edam.communication.controller;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.PageResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.communication.dto.NotificationResponse;
import com.edam.communication.service.NotificationInboxService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = com.edam.common.api.ApiPaths.V1 + "/notifications",
		produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Notification Inbox", description = "Per-user in-app notification inbox.")
public class NotificationInboxController {

	private final NotificationInboxService service;

	public NotificationInboxController(NotificationInboxService service) { this.service = service; }

	@GetMapping
	@EdamPermission(Permissions.NOTIFICATION_VIEW)
	@Operation(summary = "List current user's notifications")
	public ApiResponse<PageResponse<NotificationResponse>> list(
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size,
			HttpServletRequest request) {
		return ApiResponse.success(service.list(page, size), CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/unread-count")
	@EdamPermission(Permissions.NOTIFICATION_VIEW)
	@Operation(summary = "Unread count for the current user")
	public ApiResponse<Map<String, Long>> unreadCount(HttpServletRequest request) {
		return ApiResponse.success(Map.of("count", service.unreadCount()),
				CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/read")
	@EdamPermission(Permissions.NOTIFICATION_VIEW)
	@Operation(summary = "Mark a notification as read (user-scoped)")
	public ApiResponse<Void> markRead(@PathVariable UUID id, HttpServletRequest request) {
		service.markRead(id);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/read-all")
	@EdamPermission(Permissions.NOTIFICATION_VIEW)
	@Operation(summary = "Mark all notifications as read for the current user")
	public ApiResponse<Map<String, Integer>> markAllRead(HttpServletRequest request) {
		int count = service.markAllRead();
		return ApiResponse.success(Map.of("marked", count), CorrelationId.current(), request.getRequestURI());
	}
}
