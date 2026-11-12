package com.edam.configplane.feature;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A per-institution feature-flag override (BRD INS-04).
 *
 * <p>Absence of a row means "use {@link FeatureKey#defaultEnabled() default}". Storing a row only
 * for explicitly-set flags keeps the table small and makes "we have never decided this" a distinct,
 * visible state rather than an accidental {@code false}.
 *
 * <p>This table is the fix for audit defect D-3: the foundation's flag was global, so one
 * institution's choice changed another's behaviour.
 */
@Entity
@Table(name = "institution_feature_flag")
public class InstitutionFeatureFlag extends InstitutionOwnedEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "feature_key", nullable = false, length = 64, updatable = false)
	private FeatureKey featureKey;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	protected InstitutionFeatureFlag() {
		// JPA
	}

	public InstitutionFeatureFlag(FeatureKey featureKey, boolean enabled) {
		this.featureKey = featureKey;
		this.enabled = enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public FeatureKey getFeatureKey() {
		return this.featureKey;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

}
