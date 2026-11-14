package com.edam.configuration.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A named theme with colour tokens (Phase 4B Module B).
 *
 * <p>Each theme has a set of base colour columns (kept denormalised for the common tokens
 * so that the public config endpoint can answer with a single-row SELECT) plus a
 * one-to-many set of extended {@link ThemeToken} entries for values that don't fit the
 * fixed columns (font family, radius, spacing, etc.). Adding a new token name never
 * requires a schema change — that is one of the explicit Phase 4B requirements.
 *
 * <p>Exactly one active theme may carry {@code isDefault = TRUE}, enforced by a partial
 * unique index. The service activates/deactivates themes transactionally so no window
 * exists where zero or two defaults are visible.
 */
@Entity
@Table(name = "branding_themes")
public class BrandingTheme extends AuditableEntity {

	public enum ThemeType { LIGHT, DARK }

	@Column(name = "theme_name", nullable = false, length = 100, unique = true)
	private String themeName;

	@Column(name = "description")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "theme_type", nullable = false, length = 10)
	private ThemeType themeType = ThemeType.LIGHT;

	@Column(name = "is_default", nullable = false)
	private boolean defaultTheme = false;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "primary_color", length = 9)    private String primaryColor;
	@Column(name = "secondary_color", length = 9)  private String secondaryColor;
	@Column(name = "accent_color", length = 9)     private String accentColor;
	@Column(name = "background_color", length = 9) private String backgroundColor;
	@Column(name = "surface_color", length = 9)    private String surfaceColor;
	@Column(name = "text_color", length = 9)       private String textColor;
	@Column(name = "heading_color", length = 9)    private String headingColor;
	@Column(name = "nav_bg_color", length = 9)     private String navBgColor;
	@Column(name = "nav_text_color", length = 9)   private String navTextColor;
	@Column(name = "button_primary_color", length = 9) private String buttonPrimaryColor;
	@Column(name = "button_primary_text", length = 9)  private String buttonPrimaryText;
	@Column(name = "success_color", length = 9)    private String successColor;
	@Column(name = "warning_color", length = 9)    private String warningColor;
	@Column(name = "error_color", length = 9)      private String errorColor;
	@Column(name = "info_color", length = 9)       private String infoColor;

	@OneToMany(mappedBy = "theme", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@OrderBy("tokenKey ASC")
	private List<ThemeToken> tokens = new ArrayList<>();

	public String getThemeName() { return this.themeName; }
	public void setThemeName(String themeName) { this.themeName = themeName; }
	public String getDescription() { return this.description; }
	public void setDescription(String description) { this.description = description; }
	public ThemeType getThemeType() { return this.themeType; }
	public void setThemeType(ThemeType themeType) { this.themeType = themeType; }
	public boolean isDefaultTheme() { return this.defaultTheme; }
	public void setDefaultTheme(boolean defaultTheme) { this.defaultTheme = defaultTheme; }
	public boolean isActive() { return this.active; }
	public void setActive(boolean active) { this.active = active; }

	public String getPrimaryColor() { return primaryColor; }
	public void setPrimaryColor(String v) { this.primaryColor = v; }
	public String getSecondaryColor() { return secondaryColor; }
	public void setSecondaryColor(String v) { this.secondaryColor = v; }
	public String getAccentColor() { return accentColor; }
	public void setAccentColor(String v) { this.accentColor = v; }
	public String getBackgroundColor() { return backgroundColor; }
	public void setBackgroundColor(String v) { this.backgroundColor = v; }
	public String getSurfaceColor() { return surfaceColor; }
	public void setSurfaceColor(String v) { this.surfaceColor = v; }
	public String getTextColor() { return textColor; }
	public void setTextColor(String v) { this.textColor = v; }
	public String getHeadingColor() { return headingColor; }
	public void setHeadingColor(String v) { this.headingColor = v; }
	public String getNavBgColor() { return navBgColor; }
	public void setNavBgColor(String v) { this.navBgColor = v; }
	public String getNavTextColor() { return navTextColor; }
	public void setNavTextColor(String v) { this.navTextColor = v; }
	public String getButtonPrimaryColor() { return buttonPrimaryColor; }
	public void setButtonPrimaryColor(String v) { this.buttonPrimaryColor = v; }
	public String getButtonPrimaryText() { return buttonPrimaryText; }
	public void setButtonPrimaryText(String v) { this.buttonPrimaryText = v; }
	public String getSuccessColor() { return successColor; }
	public void setSuccessColor(String v) { this.successColor = v; }
	public String getWarningColor() { return warningColor; }
	public void setWarningColor(String v) { this.warningColor = v; }
	public String getErrorColor() { return errorColor; }
	public void setErrorColor(String v) { this.errorColor = v; }
	public String getInfoColor() { return infoColor; }
	public void setInfoColor(String v) { this.infoColor = v; }

	public List<ThemeToken> getTokens() { return this.tokens; }
	public void setTokens(List<ThemeToken> tokens) { this.tokens = tokens; }

	public UUID getDefaultTokenId() { return null; } // convenience hook
}
