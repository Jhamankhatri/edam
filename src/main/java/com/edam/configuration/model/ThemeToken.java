package com.edam.configuration.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * One extended theme token (font, radius, spacing, future additions).
 *
 * <p>Token keys are restricted to ASCII identifiers via DB CHECK; values are plain strings
 * (colour hex, CSS length, family name) — no CSS/JS/HTML is accepted. The controller
 * validates colour tokens for the base colours and rejects values containing {@code <},
 * {@code >}, {@code '}, {@code "} or script patterns on all tokens.
 */
@Entity
@Table(name = "theme_tokens")
public class ThemeToken {

	public enum TokenType { COLOUR, FONT, SPACING, RADIUS, OTHER }

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "theme_id", nullable = false, updatable = false)
	private BrandingTheme theme;

	@Column(name = "token_key", nullable = false, length = 100)
	private String tokenKey;

	@Column(name = "token_value", nullable = false, length = 500)
	private String tokenValue;

	@Enumerated(EnumType.STRING)
	@Column(name = "token_type", nullable = false, length = 20)
	private TokenType tokenType = TokenType.OTHER;

	protected ThemeToken() {}

	public ThemeToken(BrandingTheme theme, String key, String value, TokenType type) {
		this.theme = theme;
		this.tokenKey = key;
		this.tokenValue = value;
		this.tokenType = type;
	}

	public UUID getId() { return this.id; }
	public String getTokenKey() { return this.tokenKey; }
	public void setTokenKey(String k) { this.tokenKey = k; }
	public String getTokenValue() { return this.tokenValue; }
	public void setTokenValue(String v) { this.tokenValue = v; }
	public TokenType getTokenType() { return this.tokenType; }
	public void setTokenType(TokenType t) { this.tokenType = t; }
}
