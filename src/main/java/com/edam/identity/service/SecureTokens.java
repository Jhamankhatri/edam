package com.edam.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Generates and hashes the opaque tokens used for password reset (Module C).
 *
 * <h2>Generation</h2>
 * {@link SecureRandom} with 32 bytes of entropy, URL-safe Base64 encoded. {@code java.util.Random} would
 * be predictable and {@code Math.random} worse; both are common mistakes in reset-token code.
 *
 * <h2>Hashing</h2>
 * SHA-256, not BCrypt. This is deliberate and is not a weakening: the token is 256 bits of
 * cryptographically random data, so there is no guessable input space for a slow hash to defend. BCrypt's
 * cost factor exists to slow down guessing a <em>low-entropy</em> secret like a human password. Using it
 * here would only make every reset lookup expensive. What matters is that the stored value is a one-way
 * function of the token, so a database dump yields no usable links.
 */
@Component
public class SecureTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int TOKEN_BYTES = 32;

	/** A fresh, URL-safe token. The raw value is returned to the caller exactly once. */
	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** The stored form of a token. Deterministic, so lookup by hash is possible. */
	public String hash(String rawToken) {
		if (rawToken == null) {
			throw new IllegalArgumentException("Token must not be null");
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			// SHA-256 is mandated by the JLS for every JRE; absence is unrecoverable.
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	/**
	 * Constant-time comparison, used where two secrets are compared directly.
	 *
	 * <p>Not needed for database lookups (which are indexed on the hash), but required anywhere a
	 * token is compared in memory, so that response timing cannot reveal a near-match.
	 */
	public boolean matches(String rawToken, String expectedHash) {
		if (rawToken == null || expectedHash == null) {
			return false;
		}
		return MessageDigest.isEqual(hash(rawToken).getBytes(StandardCharsets.UTF_8),
				expectedHash.getBytes(StandardCharsets.UTF_8));
	}

}
