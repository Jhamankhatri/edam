package com.edam.configplane.feature;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes institution feature flags (BRD INS-04).
 *
 * <h2>Runtime behaviour</h2>
 * {@link #isEnabled(FeatureKey)} is the only method business code should call for a decision. It
 * consults the institution's override and falls back to the flag's documented default. It never
 * reads global configuration, which is the difference between this and the foundation's global flag
 * (audit defect D-3).
 *
 * <h2>Why reads are cheap and safe</h2>
 * Each request's flags are loaded once per call and the table has at most one row per institution per
 * flag, so a decision is a map lookup. The method deliberately does not cache across requests: an
 * administrator switching a flag must see the change on their next action, not after an eviction.
 *
 * <h2>Known limitation, stated rather than hidden</h2>
 * A flag read through {@code InstitutionContext} depends on a resolved tenant. Calls made outside a
 * tenant (a background job, for example) therefore get the default. Every current caller is
 * request-scoped; if a job needs a flag it must load it for an explicit institution.
 */
@Service
public class FeatureFlagService {

	private final InstitutionFeatureFlagRepository flags;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	public FeatureFlagService(InstitutionFeatureFlagRepository flags, InstitutionContext institutionContext,
			AuditRecorder audit) {
		this.flags = flags;
		this.institutionContext = institutionContext;
		this.audit = audit;
	}

	/** Effective state of every flag for the current institution, including unset ones. */
	@Transactional(readOnly = true)
	public List<FlagState> list() {
		Long institutionId = this.institutionContext.requireInstitutionId();
		Map<FeatureKey, InstitutionFeatureFlag> overrides = new EnumMap<>(FeatureKey.class);
		this.flags.findByInstitutionId(institutionId)
			.forEach(flag -> overrides.put(flag.getFeatureKey(), flag));
		List<FlagState> states = new ArrayList<>();
		for (FeatureKey key : FeatureKey.values()) {
			InstitutionFeatureFlag override = overrides.get(key);
			states.add(new FlagState(key, override == null ? key.defaultEnabled() : override.isEnabled(),
					override != null, override == null ? null : override.getUpdatedAt()));
		}
		return states;
	}

	/** Decide a feature for the current institution. */
	@Transactional(readOnly = true)
	public boolean isEnabled(FeatureKey key) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.flags.findByInstitutionIdAndFeatureKey(institutionId, key)
			.map(InstitutionFeatureFlag::isEnabled)
			.orElseGet(key::defaultEnabled);
	}

	/** Decide a feature for an explicitly identified institution (jobs, exports, cross-tenant admin). */
	@Transactional(readOnly = true)
	public boolean isEnabled(Long institutionId, FeatureKey key) {
		return this.flags.findByInstitutionIdAndFeatureKey(institutionId, key)
			.map(InstitutionFeatureFlag::isEnabled)
			.orElseGet(key::defaultEnabled);
	}

	@Transactional
	public FlagState update(String key, boolean enabled) {
		FeatureKey featureKey;
		try {
			featureKey = FeatureKey.fromKey(key);
		}
		catch (IllegalArgumentException ex) {
			throw new ResourceNotFoundException("Feature flag", key);
		}
		Long institutionId = this.institutionContext.requireInstitutionId();
		InstitutionFeatureFlag flag = this.flags.findByInstitutionIdAndFeatureKey(institutionId, featureKey)
			.orElseGet(() -> {
				InstitutionFeatureFlag created = new InstitutionFeatureFlag(featureKey, featureKey.defaultEnabled());
				created.assignInstitution(institutionId);
				return created;
			});
		boolean before = flag.isEnabled();
		flag.setEnabled(enabled);
		InstitutionFeatureFlag saved = this.flags.save(flag);
		if (before != enabled) {
			this.audit.recordChange(AuditAction.UPDATE, "InstitutionFeatureFlag", saved.getId(),
					"Feature flag '" + featureKey.name() + "' set to " + enabled, Map.of("enabled", before),
					Map.of("enabled", enabled));
		}
		return new FlagState(featureKey, enabled, true, saved.getUpdatedAt());
	}

	/** Effective value plus whether it was set explicitly, so the client can show provenance. */
	public record FlagState(FeatureKey key, boolean enabled, boolean explicitlySet, java.time.Instant updatedAt) {
	}

}
