package com.edam.institution.domain;

import com.edam.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Key-value configurable setting scoped to a single financial institution
 * (e.g., eligible CAPEX departments, fallback thresholds, feature toggles).
 */
@Entity
@Table(
        name = "institution_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_institution_settings_inst_key",
                columnNames = {"institution_id", "setting_key"}))
public class InstitutionSetting extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String settingValue;

    @Column(name = "value_type", nullable = false, length = 30)
    private String valueType;

    @Column(name = "description", length = 300)
    private String description;

    protected InstitutionSetting() {
    }

    public InstitutionSetting(
            Institution institution,
            String settingKey,
            String settingValue,
            String valueType,
            String description) {
        this.institution = institution;
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.valueType = valueType;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Institution getInstitution() {
        return institution;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public String getValueType() {
        return valueType;
    }

    public String getDescription() {
        return description;
    }
}
