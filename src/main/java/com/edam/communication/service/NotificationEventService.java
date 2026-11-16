package com.edam.communication.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;
import com.edam.communication.dto.EventConfigRequest;
import com.edam.communication.dto.EventConfigResponse;
import com.edam.communication.model.CommunicationEvent;
import com.edam.communication.model.NotificationEventConfig;
import com.edam.communication.model.RecipientGroup;
import com.edam.communication.repository.EmailTemplateRepository;
import com.edam.communication.repository.NotificationEventConfigRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Admin-managed mapping between {@link CommunicationEvent} business events and notification
 * delivery channels (email/in-app), template selection, and recipient groups. The event
 * catalogue is closed — admins may enable/disable channels but cannot invent new event codes.
 */
@Service
public class NotificationEventService {

	private static final Logger log = LoggerFactory.getLogger(NotificationEventService.class);
	private static final TypeReference<List<String>> LIST_STRING = new TypeReference<>() {};

	private final NotificationEventConfigRepository repository;
	private final EmailTemplateRepository templates;
	private final AuditRecorder audit;
	private final InstitutionContext context;
	private final ObjectMapper json;
	private final java.time.Clock clock;

	public NotificationEventService(NotificationEventConfigRepository repository, EmailTemplateRepository templates,
			AuditRecorder audit, InstitutionContext context, ObjectMapper json, java.time.Clock clock) {
		this.repository = repository;
		this.templates = templates;
		this.audit = audit;
		this.context = context;
		this.json = json;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<EventConfigResponse> list() {
		Map<String, NotificationEventConfig> byCode = repository.findAll().stream()
				.collect(Collectors.toMap(NotificationEventConfig::getEventCode, c -> c));
		return Arrays.stream(CommunicationEvent.values()).map(e -> {
			NotificationEventConfig c = byCode.get(e.name());
			if (c == null) return new EventConfigResponse(null, e.name(), e.description(), e.isMandatory(),
					false, true, null, List.of(), false);
			return new EventConfigResponse(c.getId(), c.getEventCode(), e.description(), e.isMandatory(),
					c.isEmailEnabled(), c.isInAppEnabled(), c.getEmailTemplateId(), parseGroups(c.getRecipientGroups()),
					c.isActive());
		}).toList();
	}

	@Transactional
	public EventConfigResponse update(String eventCode, EventConfigRequest req) {
		CommunicationEvent event;
		try { event = CommunicationEvent.valueOf(eventCode); }
		catch (IllegalArgumentException ex) { throw new BusinessException("edam.event.unknown: " + eventCode); }
		NotificationEventConfig c = repository.findByEventCode(eventCode)
				.orElseThrow(() -> new ResourceNotFoundException("notification_event_config", eventCode));
		// Validate recipient groups against closed set
		Set<String> allowed = Arrays.stream(RecipientGroup.values()).map(Enum::name).collect(Collectors.toSet());
		for (String g : req.recipientGroups()) {
			if (!allowed.contains(g)) throw new BusinessException("edam.event.bad_group: " + g);
		}
		if (req.emailTemplateId() != null && templates.findById(req.emailTemplateId()).isEmpty()) {
			throw new ResourceNotFoundException("email_template", req.emailTemplateId());
		}
		Map<String, Object> before = snapshot(c);
		c.setEmailEnabled(Boolean.TRUE.equals(req.emailEnabled()));
		c.setInAppEnabled(Boolean.TRUE.equals(req.inAppEnabled()));
		c.setEmailTemplateId(req.emailTemplateId());
		c.setRecipientGroups(writeGroups(req.recipientGroups()));
		c.setActive(Boolean.TRUE.equals(req.active()));
		c.setUpdatedBy(context.principal().userId());
		c.setUpdatedAt(clock.instant());
		NotificationEventConfig saved = repository.save(c);
		audit.recordChange(AuditAction.UPDATE, "notification_event_config", eventCode, before, snapshot(saved));
		log.info("edam.event.updated event={} user={}", eventCode, context.principal().userId());
		return new EventConfigResponse(saved.getId(), saved.getEventCode(), event.description(), event.isMandatory(),
				saved.isEmailEnabled(), saved.isInAppEnabled(), saved.getEmailTemplateId(),
				parseGroups(saved.getRecipientGroups()), saved.isActive());
	}

	private List<String> parseGroups(String jsonText) {
		if (jsonText == null || jsonText.isBlank()) return List.of();
		try { return List.copyOf(json.readValue(jsonText, LIST_STRING)); }
		catch (Exception ex) { return List.of(); }
	}

	private String writeGroups(Set<String> groups) {
		try { return json.writeValueAsString(groups == null ? List.of() : List.copyOf(groups)); }
		catch (Exception ex) { return "[]"; }
	}

	private Map<String, Object> snapshot(NotificationEventConfig c) {
		return Map.of(
				"emailEnabled", c.isEmailEnabled(),
				"inAppEnabled", c.isInAppEnabled(),
				"active", c.isActive(),
				"emailTemplateId", c.getEmailTemplateId() == null ? "" : c.getEmailTemplateId().toString(),
				"recipientGroups", c.getRecipientGroups() == null ? "[]" : c.getRecipientGroups()
		);
	}
}
