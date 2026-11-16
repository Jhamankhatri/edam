package com.edam.communication.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;
import com.edam.communication.dto.TemplateResponse;
import com.edam.communication.dto.TemplateUpsertRequest;
import com.edam.communication.dto.TemplateVersionResponse;
import com.edam.communication.model.CommunicationEvent;
import com.edam.communication.model.EmailTemplate;
import com.edam.communication.model.EmailTemplateVersion;
import com.edam.communication.repository.EmailTemplateRepository;
import com.edam.communication.repository.EmailTemplateVersionRepository;
import com.edam.communication.support.TemplateRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD and version history for email templates.
 *
 * <p>Templates cannot execute scripts — placeholder substitution is simple {@code {{key}}}
 * replacement on a whitelist, with HTML escaping and Jsoup sanitization in the renderer.
 * A new version row is created for every content change; older versions are immutable.
 */
@Service
public class EmailTemplateService {

	private static final Logger log = LoggerFactory.getLogger(EmailTemplateService.class);

	private final EmailTemplateRepository templates;
	private final EmailTemplateVersionRepository versions;
	private final TemplateRenderer renderer;
	private final AuditRecorder audit;
	private final InstitutionContext context;
	private final Clock clock;

	public EmailTemplateService(EmailTemplateRepository templates, EmailTemplateVersionRepository versions,
			TemplateRenderer renderer, AuditRecorder audit, InstitutionContext context, Clock clock) {
		this.templates = templates;
		this.versions = versions;
		this.renderer = renderer;
		this.audit = audit;
		this.context = context;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<TemplateResponse> list() {
		return templates.findAllByOrderByCodeAsc().stream()
				.map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public TemplateResponse get(UUID id) {
		return toResponse(load(id));
	}

	@Transactional(readOnly = true)
	public TemplateResponse getByCode(String code) {
		return toResponse(templates.findByCode(code)
				.orElseThrow(() -> new ResourceNotFoundException("email_template", code)));
	}

	@Transactional
	public TemplateResponse create(TemplateUpsertRequest req) {
		if (templates.existsByCode(req.code())) {
			throw new BusinessException("edam.template.code_exists: " + req.code());
		}
		validateEvent(req.eventCode());
		// Allowed placeholders are extracted from the templates themselves — a placeholder must appear
		// in one of subject/html/text to be considered allowed. This prevents an admin from
		// accidentally declaring a placeholder they never use or opening up substitution for a
		// name they do not.
		Set<String> placeholders = renderer.extractPlaceholders(req.htmlBody());
		placeholders.addAll(renderer.extractPlaceholders(req.subject()));
		if (req.textBody() != null) placeholders.addAll(renderer.extractPlaceholders(req.textBody()));
		renderer.validateTemplate(req.htmlBody(), placeholders);
		if (req.textBody() != null) renderer.validateTemplate(req.textBody(), placeholders);
		renderer.validateTemplate(req.subject(), placeholders);

		Instant now = clock.instant();
		UUID userId = context.principal().userId();
		EmailTemplate t = new EmailTemplate();
		t.setCode(req.code());
		t.setDisplayName(req.displayName());
		t.setDescription(req.description());
		t.setEventCode(req.eventCode());
		t.setSubject(req.subject());
		t.setHtmlBody(req.htmlBody());
		t.setTextBody(req.textBody());
		t.setLocale(req.locale() == null ? "en" : req.locale());
		t.setActive(Boolean.TRUE.equals(req.active()));
		t.setCurrentVersion(1);
		t.setCreatedBy(userId);
		t.setUpdatedBy(userId);
		EmailTemplate saved = templates.save(t);
		templates.flush();

		EmailTemplateVersion v = new EmailTemplateVersion();
		v.setTemplateId(saved.getId());
		v.setVersionNumber(1);
		v.setSubject(req.subject());
		v.setHtmlBody(req.htmlBody());
		v.setTextBody(req.textBody());
		v.setLocale(saved.getLocale());
		v.setChangeNote(req.changeNote() == null ? "Initial version" : req.changeNote());
		v.setCreatedBy(userId);
		versions.save(v);
		versions.flush();

		audit.recordChange(AuditAction.CREATE, "email_template", saved.getId(),
				Map.of(), auditSnapshot(saved));
		log.info("edam.template.created code={} id={} user={}", saved.getCode(), saved.getId(), userId);
		return toResponse(saved);
	}

	/** Create a new version of an existing template (immutable history). */
	@Transactional
	public TemplateVersionResponse createVersion(UUID templateId, TemplateUpsertRequest req) {
		EmailTemplate t = load(templateId);
		Set<String> placeholders = renderer.extractPlaceholders(req.htmlBody());
		placeholders.addAll(renderer.extractPlaceholders(req.subject()));
		if (req.textBody() != null) placeholders.addAll(renderer.extractPlaceholders(req.textBody()));
		renderer.validateTemplate(req.htmlBody(), placeholders);
		if (req.textBody() != null) renderer.validateTemplate(req.textBody(), placeholders);
		renderer.validateTemplate(req.subject(), placeholders);
		int nextVersion = t.getCurrentVersion() + 1;
		Instant now = clock.instant();
		UUID userId = context.principal().userId();
		EmailTemplateVersion v = new EmailTemplateVersion();
		v.setTemplateId(t.getId());
		v.setVersionNumber(nextVersion);
		v.setSubject(req.subject());
		v.setHtmlBody(req.htmlBody());
		v.setTextBody(req.textBody());
		v.setLocale(req.locale() == null ? t.getLocale() : req.locale());
		v.setChangeNote(req.changeNote());
		v.setCreatedBy(userId);
		versions.save(v);
		t.setSubject(req.subject());
		t.setHtmlBody(req.htmlBody());
		t.setTextBody(req.textBody());
		t.setLocale(v.getLocale());
		t.setCurrentVersion(nextVersion);
		t.setUpdatedAt(now);
		t.setUpdatedBy(userId);
		templates.save(t);

		audit.recordChange(AuditAction.UPDATE, "email_template", t.getId(),
				Map.of("currentVersion", nextVersion - 1), Map.of("currentVersion", nextVersion));
		return toVersionResponse(v);
	}

	@Transactional
	public TemplateResponse activate(UUID id, boolean active) {
		EmailTemplate t = load(id);
		boolean previous = t.isActive();
		t.setActive(active);
		t.setUpdatedAt(clock.instant());
		t.setUpdatedBy(context.principal().userId());
		templates.save(t);
		audit.recordChange(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
				"email_template", t.getId(), Map.of("active", previous), Map.of("active", active));
		return toResponse(t);
	}

	@Transactional(readOnly = true)
	public List<TemplateVersionResponse> history(UUID templateId) {
		load(templateId); // ensure exists
		return versions.findByTemplateIdOrderByVersionNumberDesc(templateId).stream()
				.map(this::toVersionResponse).toList();
	}

	/** Render a preview for a version with sample placeholder data (no mail sent). */
	@Transactional(readOnly = true)
	public Map<String, String> preview(UUID templateId, Integer versionNumber, Map<String, String> sampleValues) {
		EmailTemplateVersion v = resolveVersion(templateId, versionNumber);
		Set<String> allowed = TemplateRenderer.parsePlaceholders(/* placeholders not stored on version; extract from template */
				v.getChangeNote() == null ? "" : "");
		// Allowed placeholders are those that appear in the templates themselves — for preview, permit
		// whatever the sample supplies so admins can experiment; renderer still escapes/sanitises.
		Set<String> effectiveAllowed = renderer.extractPlaceholders(v.getHtmlBody());
		effectiveAllowed.addAll(renderer.extractPlaceholders(v.getSubject()));
		if (v.getTextBody() != null) effectiveAllowed.addAll(renderer.extractPlaceholders(v.getTextBody()));
		Map<String, String> vals = sampleValues == null ? Map.of() : sampleValues;
		return Map.of(
				"subject", renderer.renderText(v.getSubject(), vals, effectiveAllowed),
				"html", renderer.renderHtml(v.getHtmlBody(), vals, effectiveAllowed),
				"text", renderer.renderText(v.getTextBody() == null ? "" : v.getTextBody(), vals, effectiveAllowed)
		);
	}

	private EmailTemplate load(UUID id) {
		return templates.findById(id).orElseThrow(() -> new ResourceNotFoundException("email_template", id));
	}

	private EmailTemplateVersion resolveVersion(UUID templateId, Integer versionNumber) {
		if (versionNumber == null) {
			return versions.findFirstByTemplateIdOrderByVersionNumberDesc(templateId)
					.orElseThrow(() -> new ResourceNotFoundException("email_template_version", templateId));
		}
		return versions.findByTemplateIdOrderByVersionNumberDesc(templateId).stream()
				.filter(x -> x.getVersionNumber() == versionNumber).findFirst()
				.orElseThrow(() -> new ResourceNotFoundException("email_template_version", templateId + ":v" + versionNumber));
	}

	private static void validateEvent(String code) {
		try {
			CommunicationEvent.valueOf(code);
		} catch (IllegalArgumentException ex) {
			throw new BusinessException("edam.template.unknown_event: " + code);
		}
	}

	private TemplateResponse toResponse(EmailTemplate t) {
		Set<String> placeholders = renderer.extractPlaceholders(t.getHtmlBody());
		placeholders.addAll(renderer.extractPlaceholders(t.getSubject()));
		if (t.getTextBody() != null) placeholders.addAll(renderer.extractPlaceholders(t.getTextBody()));
		return new TemplateResponse(t.getId(), t.getCode(), t.getDisplayName(), t.getDescription(), t.getEventCode(),
				t.getSubject(), t.getHtmlBody(), t.getTextBody(), t.getLocale(), t.isActive(), t.getCurrentVersion(),
				placeholders, t.getCreatedBy(), t.getUpdatedBy(), t.getCreatedAt(), t.getUpdatedAt());
	}

	private TemplateVersionResponse toVersionResponse(EmailTemplateVersion v) {
		Set<String> ph = renderer.extractPlaceholders(v.getHtmlBody());
		ph.addAll(renderer.extractPlaceholders(v.getSubject()));
		if (v.getTextBody() != null) ph.addAll(renderer.extractPlaceholders(v.getTextBody()));
		return new TemplateVersionResponse(v.getId(), v.getTemplateId(), v.getVersionNumber(),
				v.getSubject(), v.getHtmlBody(), v.getTextBody(), v.getLocale(), v.getChangeNote(),
				ph, v.getCreatedBy(), v.getCreatedAt());
	}

	private Map<String, Object> auditSnapshot(EmailTemplate t) {
		return Map.of("code", t.getCode(), "eventCode", t.getEventCode(), "active", t.isActive(),
				"version", t.getCurrentVersion());
	}
}
