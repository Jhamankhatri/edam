package com.edam.institution.domain;

import com.edam.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Institution-specific branding, colors, and UI presentation settings,
 * kept isolated from expense and workflow business rules.
 */
@Entity
@Table(name = "institution_branding")
public class InstitutionBranding extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false, unique = true)
    private Institution institution;

    @Column(name = "product_title", nullable = false, length = 120)
    private String productTitle;

    @Column(name = "logo_path", nullable = false, length = 255)
    private String logoPath;

    @Column(name = "favicon_path", nullable = false, length = 255)
    private String faviconPath;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", nullable = false, length = 7)
    private String secondaryColor;

    @Column(name = "accent_color", nullable = false, length = 7)
    private String accentColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 16)
    private ThemeMode themeMode = ThemeMode.LIGHT;

    @Column(name = "footer_text", nullable = false, length = 250)
    private String footerText;

    protected InstitutionBranding() {
    }

    public InstitutionBranding(
            Institution institution,
            String productTitle,
            String logoPath,
            String faviconPath,
            String primaryColor,
            String secondaryColor,
            String accentColor,
            ThemeMode themeMode,
            String footerText) {
        this.institution = institution;
        this.productTitle = productTitle;
        this.logoPath = logoPath;
        this.faviconPath = faviconPath;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.accentColor = accentColor;
        this.themeMode = themeMode;
        this.footerText = footerText;
    }

    public Long getId() {
        return id;
    }

    public Institution getInstitution() {
        return institution;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public String getFaviconPath() {
        return faviconPath;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public ThemeMode getThemeMode() {
        return themeMode;
    }

    public String getFooterText() {
        return footerText;
    }
}
