package com.edam.config;

import com.edam.common.validation.ValidHexColor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized, validated platform configuration properties for EDAM.
 * Ensures default institution presentation and security behavior come from configuration
 * rather than hardcoded constants in business services.
 */
@Validated
@ConfigurationProperties(prefix = "edam")
public class EdamProperties {

    @Valid
    private DefaultInstitution defaultInstitution = new DefaultInstitution();

    @Valid
    private Security security = new Security();

    public DefaultInstitution getDefaultInstitution() {
        return defaultInstitution;
    }

    public void setDefaultInstitution(DefaultInstitution defaultInstitution) {
        this.defaultInstitution = defaultInstitution;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public static class DefaultInstitution {

        @NotBlank
        @Size(max = 32)
        private String code = "PLATFORM";

        @NotBlank
        @Size(max = 150)
        private String name = "EDAM Enterprise Platform";

        @NotBlank
        @Size(max = 50)
        private String shortName = "EDAM";

        @NotBlank
        @Size(max = 120)
        private String productTitle = "Expense Delegation & Approval Management";

        @NotBlank
        @Size(max = 255)
        private String logoPath = "/assets/images/edam-logo.svg";

        @NotBlank
        @Size(max = 255)
        private String faviconPath = "/assets/images/edam-logo.svg";

        @NotBlank
        @ValidHexColor
        private String primaryColor = "#1B365D";

        @NotBlank
        @ValidHexColor
        private String secondaryColor = "#334E68";

        @NotBlank
        @ValidHexColor
        private String accentColor = "#0F766E";

        @NotBlank
        private String themeMode = "LIGHT";

        @NotBlank
        @Size(max = 250)
        private String footerText = "EDAM Enterprise Platform — Configurable Delegation & Approval Foundation";

        @NotBlank
        @Size(min = 3, max = 3)
        private String defaultCurrencyCode = "USD";

        @NotBlank
        @Size(max = 20)
        private String locale = "en-US";

        @NotBlank
        @Size(max = 64)
        private String timeZone = "UTC";

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getShortName() {
            return shortName;
        }

        public void setShortName(String shortName) {
            this.shortName = shortName;
        }

        public String getProductTitle() {
            return productTitle;
        }

        public void setProductTitle(String productTitle) {
            this.productTitle = productTitle;
        }

        public String getLogoPath() {
            return logoPath;
        }

        public void setLogoPath(String logoPath) {
            this.logoPath = logoPath;
        }

        public String getFaviconPath() {
            return faviconPath;
        }

        public void setFaviconPath(String faviconPath) {
            this.faviconPath = faviconPath;
        }

        public String getPrimaryColor() {
            return primaryColor;
        }

        public void setPrimaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
        }

        public String getSecondaryColor() {
            return secondaryColor;
        }

        public void setSecondaryColor(String secondaryColor) {
            this.secondaryColor = secondaryColor;
        }

        public String getAccentColor() {
            return accentColor;
        }

        public void setAccentColor(String accentColor) {
            this.accentColor = accentColor;
        }

        public String getThemeMode() {
            return themeMode;
        }

        public void setThemeMode(String themeMode) {
            this.themeMode = themeMode;
        }

        public String getFooterText() {
            return footerText;
        }

        public void setFooterText(String footerText) {
            this.footerText = footerText;
        }

        public String getDefaultCurrencyCode() {
            return defaultCurrencyCode;
        }

        public void setDefaultCurrencyCode(String defaultCurrencyCode) {
            this.defaultCurrencyCode = defaultCurrencyCode;
        }

        public String getLocale() {
            return locale;
        }

        public void setLocale(String locale) {
            this.locale = locale;
        }

        public String getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }
    }

    public static class Security {

        @NotBlank
        private String institutionHeaderName = "X-Institution-Code";

        public String getInstitutionHeaderName() {
            return institutionHeaderName;
        }

        public void setInstitutionHeaderName(String institutionHeaderName) {
            this.institutionHeaderName = institutionHeaderName;
        }
    }
}
