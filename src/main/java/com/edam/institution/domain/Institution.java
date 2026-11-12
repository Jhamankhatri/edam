package com.edam.institution.domain;

import com.edam.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a client financial institution configured in the EDAM platform.
 */
@Entity
@Table(name = "institutions")
public class Institution extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "short_name", nullable = false, length = 50)
    private String shortName;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstitutionStatus status = InstitutionStatus.ACTIVE;

    @Column(name = "default_currency_code", nullable = false, length = 3)
    private String defaultCurrencyCode;

    @Column(name = "locale", nullable = false, length = 20)
    private String locale;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    protected Institution() {
    }

    public Institution(
            String code,
            String name,
            String shortName,
            String legalName,
            InstitutionStatus status,
            String defaultCurrencyCode,
            String locale,
            String timeZone) {
        this.code = code;
        this.name = name;
        this.shortName = shortName;
        this.legalName = legalName;
        this.status = status;
        this.defaultCurrencyCode = defaultCurrencyCode;
        this.locale = locale;
        this.timeZone = timeZone;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLegalName() {
        return legalName;
    }

    public InstitutionStatus getStatus() {
        return status;
    }

    public String getDefaultCurrencyCode() {
        return defaultCurrencyCode;
    }

    public String getLocale() {
        return locale;
    }

    public String getTimeZone() {
        return timeZone;
    }
}
