package com.edam.communication.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.edam.common.api.PageResponse;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;
import com.edam.communication.dto.NotificationResponse;
import com.edam.communication.model.Notification;
import com.edam.communication.repository.NotificationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notification inbox. Strictly scoped to the currently-authenticated user;
 * there is no admin "read someone else's notifications" endpoint.
 */
@Service
public class NotificationInboxService {

	private final NotificationRepository notifications;
	private final InstitutionContext context;
	private final Clock clock;

	public NotificationInboxService(NotificationRepository notifications, InstitutionContext context, Clock clock) {
		this.notifications = notifications;
		this.context = context;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(int page, int size) {
		UUID userId = context.principal().userId();
		Page<Notification> pageData = notifications.findByUserIdOrderByCreatedAtDesc(userId,
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")));
		return PageResponse.of(pageData, this::toResponse);
	}

	@Transactional(readOnly = true)
	public long unreadCount() {
		return notifications.countByUserIdAndReadFalse(context.principal().userId());
	}

	@Transactional
	public void markRead(UUID id) {
		Notification n = notifications.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("notification", id));
		if (!n.getUserId().equals(context.principal().userId())) {
			throw new ResourceNotFoundException("notification", id);
		}
		if (!n.isRead()) {
			n.setRead(true);
			n.setReadAt(clock.instant());
			notifications.save(n);
		}
	}

	@Transactional
	public int markAllRead() {
		UUID userId = context.principal().userId();
		int count = notifications.markAllRead(userId, clock.instant());
		return count;
	}

	private NotificationResponse toResponse(Notification n) {
		return new NotificationResponse(n.getId(), n.getEventCode(), n.getTitle(), n.getBody(),
				n.getPayloadJson(), n.isRead(), n.getReadAt(), n.getCreatedAt());
	}
}
