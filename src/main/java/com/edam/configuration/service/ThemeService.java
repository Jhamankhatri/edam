package com.edam.configuration.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.configuration.dto.ConfigurationDtos;
import com.edam.configuration.dto.ConfigurationDtos.SaveThemeRequest;
import com.edam.configuration.dto.ConfigurationDtos.ThemeResponse;
import com.edam.configuration.dto.ConfigurationDtos.ThemeTokenRequest;
import com.edam.configuration.dto.ConfigurationDtos.ThemeTokenType;
import com.edam.configuration.model.BrandingTheme;
import com.edam.configuration.model.BrandingTheme.ThemeType;
import com.edam.configuration.model.ThemeToken;
import com.edam.configuration.model.ThemeToken.TokenType;
import com.edam.configuration.repository.BrandingThemeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD and activation of branding themes (Phase 4B Module B).
 *
 * <p>Tokens for named colours live as columns on the theme entity (primary, secondary, ...)
 * for fast single-row reads in the public config endpoint; extended tokens are stored in
 * the child {@code theme_tokens} table so new tokens can be added without schema changes.
 *
 * <p>The service enforces:
 * <ul>
 *   <li>Hex-colour format ({@code #rgb}/{@code #rrggbb}/{@code #rrggbbaa}) for COLOUR tokens.</li>
 *   <li>No HTML/CSS/JS injection ({@code <}, {@code >}, {@code '}, {@code ",} {@code \} banned).</li>
 *   <li>At most one active default at any time (enforced partly by partial unique index,
 *       partly in service logic for the activate() transition).</li>
 *   <li>At least one active default always exists after the seed migration.</li>
 * </ul>
 */
@Service
public class ThemeService {

	private static final Pattern COLOUR_RE = Pattern.compile("^#[0-9a-fA-F]{3,8}$");
	private static final Pattern UNSAFE_VALUE_RE = Pattern.compile("[<>'\"\\\\]|script|expression", Pattern.CASE_INSENSITIVE);

	private final BrandingThemeRepository themes;
	private final AuditRecorder audit;

	public ThemeService(BrandingThemeRepository themes, AuditRecorder audit) {
		this.themes = themes;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<ThemeResponse> list(boolean includeInactive) {
		return (includeInactive ? this.themes.findAllByOrderByThemeNameAsc()
				: this.themes.findByActiveTrueOrderByThemeNameAsc())
			.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public ThemeResponse get(UUID id) {
		return toResponse(requireTheme(id));
	}

	@Transactional(readOnly = true)
	public ThemeResponse activeDefault() {
		BrandingTheme t = this.themes.findFirstByDefaultThemeTrueAndActiveTrue()
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"No default theme is configured. Activate one before publishing the application shell."));
		return toResponse(t);
	}

	@Transactional
	public ThemeResponse create(SaveThemeRequest req) {
		if (this.themes.findByThemeName(req.name()).isPresent()) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A theme with that name already exists.");
		}
		BrandingTheme t = new BrandingTheme();
		apply(t, req);
		// If this is the first theme ever, promote to default so the invariant holds.
		if (this.themes.findFirstByDefaultThemeTrueAndActiveTrue().isEmpty()) {
			t.setDefaultTheme(true);
			t.setActive(true);
		}
		BrandingTheme saved = this.themes.save(t);
		this.audit.record(AuditAction.CREATE, "BrandingTheme", saved.getId());
		return toResponse(saved);
	}

	@Transactional
	public ThemeResponse update(UUID id, SaveThemeRequest req) {
		BrandingTheme t = requireTheme(id);
		if (this.themes.existsByThemeNameAndIdNot(req.name(), id)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A theme with that name already exists.");
		}
		Map<String, Object> before = snapshot(t);
		apply(t, req);
		BrandingTheme saved = this.themes.save(t);
		this.audit.recordChange(AuditAction.UPDATE, "BrandingTheme", saved.getId(), before, snapshot(saved));
		return toResponse(saved);
	}

	@Transactional
	public ThemeResponse activate(UUID id) {
		BrandingTheme t = requireTheme(id);
		Map<String, Object> before = snapshot(t);
		t.setActive(true);
		this.themes.save(t);
		this.audit.recordChange(AuditAction.UPDATE, "BrandingTheme", t.getId(), before, snapshot(t));
		return toResponse(t);
	}

	@Transactional
	public ThemeResponse deactivate(UUID id) {
		BrandingTheme t = requireTheme(id);
		if (t.isDefaultTheme()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"Cannot deactivate the default theme. Promote another theme to default first.");
		}
		Map<String, Object> before = snapshot(t);
		t.setActive(false);
		this.themes.save(t);
		this.audit.recordChange(AuditAction.UPDATE, "BrandingTheme", t.getId(), before, snapshot(t));
		return toResponse(t);
	}

	@Transactional
	public ThemeResponse setDefault(UUID id) {
		BrandingTheme t = requireTheme(id);
		if (!t.isActive()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"Activate the theme before making it the default.");
		}
		// Demote current default (if different). The DB unique index will enforce only one
		// default, so we must clear the flag on other rows first.
		this.themes.findFirstByDefaultThemeTrueAndActiveTrue().ifPresent(current -> {
			if (!current.getId().equals(id)) {
				current.setDefaultTheme(false);
				this.themes.save(current);
			}
		});
		Map<String, Object> before = snapshot(t);
		t.setDefaultTheme(true);
		t.setActive(true);
		BrandingTheme saved = this.themes.save(t);
		this.audit.recordChange(AuditAction.UPDATE, "BrandingTheme", saved.getId(), before, snapshot(saved));
		return toResponse(saved);
	}

	private BrandingTheme requireTheme(UUID id) {
		return this.themes.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Theme not found."));
	}

	private void apply(BrandingTheme t, SaveThemeRequest r) {
		t.setThemeName(r.name().trim());
		t.setDescription(r.description() == null ? null : r.description().trim());
		t.setThemeType(ThemeType.valueOf(r.themeType().name()));
		t.setPrimaryColor(sanitizeColor(r.primaryColor(), "primaryColor"));
		t.setSecondaryColor(sanitizeColor(r.secondaryColor(), "secondaryColor"));
		t.setAccentColor(sanitizeColor(r.accentColor(), "accentColor"));
		t.setBackgroundColor(sanitizeColor(r.backgroundColor(), "backgroundColor"));
		t.setSurfaceColor(sanitizeColor(r.surfaceColor(), "surfaceColor"));
		t.setTextColor(sanitizeColor(r.textColor(), "textColor"));
		t.setHeadingColor(sanitizeColor(r.headingColor(), "headingColor"));
		t.setNavBgColor(sanitizeColor(r.navBgColor(), "navBgColor"));
		t.setNavTextColor(sanitizeColor(r.navTextColor(), "navTextColor"));
		t.setButtonPrimaryColor(sanitizeColor(r.buttonPrimaryColor(), "buttonPrimaryColor"));
		t.setButtonPrimaryText(sanitizeColor(r.buttonPrimaryText(), "buttonPrimaryText"));
		t.setSuccessColor(sanitizeColor(r.successColor(), "successColor"));
		t.setWarningColor(sanitizeColor(r.warningColor(), "warningColor"));
		t.setErrorColor(sanitizeColor(r.errorColor(), "errorColor"));
		t.setInfoColor(sanitizeColor(r.infoColor(), "infoColor"));
		// Replace tokens wholesale (orphanRemoval = true cleans old rows).
		t.getTokens().clear();
		if (r.tokens() != null) {
			for (ThemeTokenRequest tr : r.tokens()) {
				validateToken(tr);
				t.getTokens().add(new ThemeToken(t, tr.key(), tr.value(), TokenType.valueOf(tr.type().name())));
			}
		}
	}

	private static String sanitizeColor(String raw, String field) {
		if (raw == null) return null;
		String v = raw.trim();
		if (v.isEmpty()) return null;
		if (!COLOUR_RE.matcher(v).matches()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					field + " must be a hex colour (#rgb / #rrggbb / #rrggbbaa).");
		}
		return v.toLowerCase();
	}

	private static void validateToken(ThemeTokenRequest tr) {
		if (tr == null || tr.key() == null) return;
		if (UNSAFE_VALUE_RE.matcher(tr.value() == null ? "" : tr.value()).find()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Token value for '" + tr.key() + "' contains disallowed characters.");
		}
		if (tr.type() == ThemeTokenType.COLOUR && !COLOUR_RE.matcher(tr.value()).matches()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Token '" + tr.key() + "' is declared COLOUR but is not a valid hex colour.");
		}
	}

	private Map<String, Object> snapshot(BrandingTheme t) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("themeName", t.getThemeName());
		m.put("themeType", t.getThemeType());
		m.put("active", t.isActive());
		m.put("isDefault", t.isDefaultTheme());
		m.put("primaryColor", t.getPrimaryColor());
		return m;
	}

	private ThemeResponse toResponse(BrandingTheme t) {
		Map<String, String> tokens = new LinkedHashMap<>();
		for (ThemeToken tk : t.getTokens()) tokens.put(tk.getTokenKey(), tk.getTokenValue());
		return new ThemeResponse(t.getId(), t.getThemeName(), t.getDescription(), t.getThemeType().name(),
				t.isActive(), t.isDefaultTheme(),
				t.getPrimaryColor(), t.getSecondaryColor(), t.getAccentColor(),
				t.getBackgroundColor(), t.getSurfaceColor(), t.getTextColor(), t.getHeadingColor(),
				t.getNavBgColor(), t.getNavTextColor(),
				t.getButtonPrimaryColor(), t.getButtonPrimaryText(),
				t.getSuccessColor(), t.getWarningColor(), t.getErrorColor(), t.getInfoColor(),
				tokens, t.getCreatedAt(), t.getUpdatedAt());
	}
}
