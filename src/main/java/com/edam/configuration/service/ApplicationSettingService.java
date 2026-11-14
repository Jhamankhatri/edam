package com.edam.configuration.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.configuration.dto.ConfigurationDtos;
import com.edam.configuration.dto.ConfigurationDtos.BulkSettingsUpdateRequest;
import com.edam.configuration.dto.ConfigurationDtos.SaveSettingRequest;
import com.edam.configuration.dto.ConfigurationDtos.SettingResponse;
import com.edam.configuration.dto.ConfigurationDtos.SettingType;
import com.edam.configuration.model.ApplicationSetting;
import com.edam.configuration.repository.ApplicationSettingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Typed application settings service (Phase 4B Module C).
 *
 * <p>Secrets, credentials and security-sensitive switches are explicitly out of scope:
 * the service refuses any key matching a blocklist prefix, and non-editable settings
 * (seeded as {@code editable=false}) reject writes with a clear error.
 */
@Service
public class ApplicationSettingService {

	/** Keys/prefixes that this service will never write. These live in infrastructure config. */
	private static final Set<String> BLOCKED_KEY_PREFIXES = Set.of(
			"db.", "datasource.", "spring.datasource", "security.", "server.ssl",
			"edam.signing", "edam.secret", "jwt.", "oauth.", "ldap.", "password",
			"credentials", "private-key", "secret");

	private final ApplicationSettingRepository repo;
	private final AuditRecorder audit;
	private final JsonMapper jsonMapper;
	private final InstitutionContext institutionContext;

	public ApplicationSettingService(ApplicationSettingRepository repo, AuditRecorder audit, JsonMapper jsonMapper,
			InstitutionContext institutionContext) {
		this.repo = repo;
		this.audit = audit;
		this.jsonMapper = jsonMapper;
		this.institutionContext = institutionContext;
	}

	@Transactional(readOnly = true)
	public List<SettingResponse> listAll() {
		return this.repo.findByActiveTrueOrderByCategoryAscKeyAsc().stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<SettingResponse> listByCategory(String category) {
		return this.repo.findByCategoryAndActiveTrueOrderByKeyAsc(category).stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public SettingResponse get(String key) {
		return toResponse(requireKey(key));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> publicSafeValues() {
		// Only a curated whitelist is exposed to unauthenticated callers.
		Set<String> allowed = Set.of(
				"app.displayName", "app.defaultLocale", "app.defaultCurrency",
				"app.dateFormat", "app.timeZone", "notifications.email.enabled");
		Map<String, Object> out = new LinkedHashMap<>();
		for (ApplicationSetting s : this.repo.findByActiveTrueOrderByCategoryAscKeyAsc()) {
			if (allowed.contains(s.getKey())) out.put(s.getKey(), effectiveValue(s));
		}
		return out;
	}

	@Transactional
	public SettingResponse createOrUpdate(SaveSettingRequest req) {
		String actor = currentActor();
		if (isBlockedKey(req.key())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED,
					"That setting key is reserved and cannot be modified through the API.");
		}
		ApplicationSetting s = this.repo.findByKey(req.key()).orElseGet(ApplicationSetting::new);
		Map<String, Object> before = snapshot(s);
		boolean isNew = s.getId() == null;
		if (isNew) {
			s.setKey(req.key());
			s.markCreated(actor);
		}
		if (req.displayName() != null) s.setDisplayName(req.displayName());
		if (req.description() != null) s.setDescription(req.description());
		if (req.category() != null) s.setCategory(req.category().trim());
		if (req.type() != null) s.setType(req.type().name());
		if (req.active() != null) s.setActive(req.active());
		if (req.editable() != null) s.setEditable(req.editable());

		Object def = coerce(req.defaultValue(), typeOf(s.getType()), "defaultValue");
		s.setDefaultValueJson(toJson(def));
		Object val = req.value() == null ? def : coerce(req.value(), typeOf(s.getType()), "value");
		if (!s.isEditable() && !isNew) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"Setting '" + req.key() + "' is not editable.");
		}
		s.setValueJson(toJson(val));
		s.touch(actor);
		ApplicationSetting saved = this.repo.save(s);
		this.audit.recordChange(isNew ? AuditAction.CREATE : AuditAction.UPDATE,
				"ApplicationSetting", saved.getId(), before, snapshot(saved));
		return toResponse(saved);
	}

	@Transactional
	public List<SettingResponse> bulkUpdate(BulkSettingsUpdateRequest req) {
		if (req.values() == null || req.values().isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "No settings supplied.");
		}
		List<SettingResponse> out = new ArrayList<>();
		// Validate all first, then apply (transactional atomicity).
		List<Map.Entry<String, Object>> toApply = new ArrayList<>(req.values().entrySet());
		for (var e : toApply) {
			ApplicationSetting s = this.repo.findByKey(e.getKey())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"Setting '" + e.getKey() + "' does not exist. Use POST to create it first."));
			if (!s.isEditable()) {
				throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
						"Setting '" + e.getKey() + "' is not editable.");
			}
			if (isBlockedKey(e.getKey())) {
				throw new BusinessException(ErrorCode.PERMISSION_DENIED,
						"Setting '" + e.getKey() + "' is reserved.");
			}
			coerce(e.getValue(), typeOf(s.getType()), e.getKey());
		}
		String actor = currentActor();
		for (var e : toApply) {
			ApplicationSetting s = this.repo.findByKey(e.getKey()).orElseThrow();
			Map<String, Object> before = snapshot(s);
			s.setValueJson(toJson(coerce(e.getValue(), typeOf(s.getType()), e.getKey())));
			s.touch(actor);
			this.repo.save(s);
			this.audit.recordChange(AuditAction.UPDATE, "ApplicationSetting", s.getId(), before, snapshot(s));
			out.add(toResponse(s));
		}
		return out;
	}

	private ApplicationSetting requireKey(String key) {
		return this.repo.findByKey(key)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Setting '" + key + "' not found."));
	}

	private Object effectiveValue(ApplicationSetting s) {
		String raw = s.getValueJson() != null ? s.getValueJson() : s.getDefaultValueJson();
		return fromJson(raw, typeOf(s.getType()));
	}

	private static SettingType typeOf(String name) {
		try { return SettingType.valueOf(name); }
		catch (Exception ex) { return SettingType.STRING; }
	}

	private Object coerce(Object raw, SettingType typeStr, String field) {
		if (raw == null) return null;
		try {
			switch (type) {
				case STRING -> {
					if (!(raw instanceof String s)) {
						throw new BusinessException(ErrorCode.VALIDATION_FAILED,
								field + " must be a string.");
					}
					return s;
				}
				case BOOLEAN -> {
					if (raw instanceof Boolean b) return b;
					if (raw instanceof String s) {
						if ("true".equalsIgnoreCase(s)) return true;
						if ("false".equalsIgnoreCase(s)) return false;
					}
					throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " must be a boolean.");
				}
				case INTEGER -> {
					if (raw instanceof Number n) return n.longValue();
					if (raw instanceof String s) return Long.parseLong(s.trim());
					throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " must be an integer.");
				}
				case DECIMAL -> {
					if (raw instanceof Number n) return new BigDecimal(n.toString());
					if (raw instanceof String s) return new BigDecimal(s.trim());
					throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " must be a decimal number.");
				}
				case JSON -> {
					// Must be a JSON object or array parseable by Jackson; reject arbitrary executable strings.
					String json = (raw instanceof String s) ? s : this.jsonMapper.writeValueAsString(raw);
					Object parsed = this.jsonMapper.readValue(json, Object.class);
					if (!(parsed instanceof java.util.Map || parsed instanceof java.util.List)) {
						throw new BusinessException(ErrorCode.VALIDATION_FAILED,
								field + " JSON must be an object or array.");
					}
					return this.jsonMapper.writeValueAsString(parsed);
				}
				case DATE -> {
					if (raw instanceof String s) {
						LocalDate.parse(s.trim());
						return s.trim();
					}
					throw new BusinessException(ErrorCode.VALIDATION_FAILED,
							field + " must be an ISO-8601 date (YYYY-MM-DD).");
				}
			}
		}
		catch (NumberFormatException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " is not a valid number.");
		}
		catch (BusinessException be) {
			throw be;
		}
		catch (Exception ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					field + " could not be parsed as " + type + ": " + ex.getMessage());
		}
		throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + ": unsupported type " + type);
	}

	private String toJson(Object v) {
		if (v == null) return null;
		try {
			return this.jsonMapper.writeValueAsString(v);
		}
		catch (Exception ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Could not serialise value: " + ex.getMessage());
		}
	}

	private Object fromJson(String json, SettingType type) {
		if (json == null) return null;
		try {
			Object raw = this.jsonMapper.readValue(json, Object.class);
			return switch (type) {
				case STRING -> raw instanceof String s ? s : raw.toString();
				case BOOLEAN -> raw instanceof Boolean b ? b : Boolean.valueOf(raw.toString());
				case INTEGER -> raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString());
				case DECIMAL -> new BigDecimal(raw.toString());
				case JSON -> raw;
				case DATE -> raw;
			};
		}
		catch (Exception ex) {
			return null;
		}
	}

	private static boolean isBlockedKey(String k) {
		if (k == null) return true;
		String lower = k.toLowerCase();
		for (String p : BLOCKED_KEY_PREFIXES) {
			if (lower.startsWith(p)) return true;
		}
		return false;
	}

	private Map<String, Object> snapshot(ApplicationSetting s) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("key", s.getKey());
		m.put("value", s.getValueJson());
		m.put("active", s.isActive());
		return m;
	}

	private String currentActor() {
		try {
			EdamPrincipal p = this.institutionContext.principal();
			return p == null ? "system" : p.userId().toString();
		}
		catch (Exception ex) {
			return "system";
		}
	}

	public boolean isNewKey(String key) { return this.repo.findByKey(key).isEmpty(); }

	private SettingResponse toResponse(ApplicationSetting s) {
		return new SettingResponse(s.getId(), s.getKey(), s.getDisplayName(), s.getDescription(),
				s.getCategory(), s.getType(), effectiveValue(s),
				fromJson(s.getDefaultValueJson(), typeOf(s.getType())),
				s.isActive(), s.isEditable(), s.getUpdatedAt());
	}
}
