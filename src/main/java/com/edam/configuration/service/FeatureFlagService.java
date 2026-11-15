package com.edam.configuration.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.configuration.dto.ConfigurationDtos.FeatureFlagResponse;
import com.edam.configuration.dto.ConfigurationDtos.SaveFeatureFlagRequest;
import com.edam.configuration.model.FeatureFlag;
import com.edam.configuration.repository.FeatureFlagRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Feature flag management (Phase 4B Module D).
 *
 * <p>Simple boolean flag plus optional JSON configuration metadata. Missing flags always
 * resolve to disabled — safe default for partial rollouts. Callers should use
 * {@link #isEnabled(String)} rather than reading the repository directly.
 */
@Service
public class FeatureFlagService {

	private final FeatureFlagRepository repo;
	private final AuditRecorder audit;
	private final InstitutionContext institutionContext;
	private final JsonMapper jsonMapper;

	public FeatureFlagService(FeatureFlagRepository repo, AuditRecorder audit, InstitutionContext ctx, JsonMapper jsonMapper) {
		this.repo = repo;
		this.audit = audit;
		this.institutionContext = ctx;
		this.jsonMapper = jsonMapper;
	}

	public boolean isEnabled(String key) {
		return this.repo.findByFeatureKey(key).map(FeatureFlag::isEnabled).orElse(false);
	}

	@Transactional(readOnly = true)
	public List<FeatureFlagResponse> list() {
		return this.repo.findAllByOrderByFeatureKeyAsc().stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public FeatureFlagResponse get(String key) {
		return toResponse(requireKey(key));
	}

	@Transactional
	public FeatureFlagResponse save(SaveFeatureFlagRequest req) {
		if (req.key() == null || req.key().isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Feature key is required.");
		}
		String actor = currentActor();
		FeatureFlag flag = this.repo.findByFeatureKey(req.key()).orElseGet(FeatureFlag::new);
		Map<String, Object> before = snapshot(flag);
		boolean isNew = flag.getId() == null;
		flag.setFeatureKey(req.key());
		if (req.displayName() != null) flag.setDisplayName(req.displayName());
		else if (isNew) flag.setDisplayName(req.key());
		if (req.description() != null) flag.setDescription(req.description());
		if (req.enabled() != null) flag.setEnabled(req.enabled());
		if (req.configuration() != null) {
			try {
				String json = (req.configuration() instanceof String s) ? s
						: this.jsonMapper.writeValueAsString(req.configuration());
				this.jsonMapper.readTree(json);
				flag.setConfigurationJson(json);
			}
			catch (Exception ex) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"configuration must be valid JSON: " + ex.getMessage());
			}
		}
		FeatureFlag saved = this.repo.save(flag);
		this.audit.recordChange(isNew ? AuditAction.CREATE : AuditAction.UPDATE,
				"FeatureFlag", saved.getId(), before, snapshot(saved));
		return toResponse(saved);
	}

	@Transactional
	public FeatureFlagResponse setEnabled(String key, boolean enabled) {
		FeatureFlag f = requireKey(key);
		Map<String, Object> before = snapshot(f);
		f.setEnabled(enabled);
		FeatureFlag saved = this.repo.save(f);
		this.audit.recordChange(AuditAction.UPDATE, "FeatureFlag", saved.getId(), before, snapshot(saved));
		return toResponse(saved);
	}

	private FeatureFlag requireKey(String key) {
		return this.repo.findByFeatureKey(key)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Feature flag '" + key + "' not found."));
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

	private Map<String, Object> snapshot(FeatureFlag f) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("enabled", f.isEnabled());
		m.put("key", f.getFeatureKey());
		return m;
	}

	public boolean isNewKey(String key) { return this.repo.findByFeatureKey(key).isEmpty(); }

	private FeatureFlagResponse toResponse(FeatureFlag f) {
		Object parsedConfig = null;
		if (f.getConfigurationJson() != null) {
			try { parsedConfig = this.jsonMapper.readValue(f.getConfigurationJson(), Object.class); }
			catch (Exception ignore) { parsedConfig = f.getConfigurationJson(); }
		}
		return new FeatureFlagResponse(f.getId(), f.getFeatureKey(), f.getDisplayName(), f.getDescription(),
				f.isEnabled(), parsedConfig, f.getUpdatedAt());
	}
}
