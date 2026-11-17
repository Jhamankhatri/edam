package com.edam.integrations.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption/decryption for integration credentials.
 *
 * <p>The master key is loaded from {@code EDAM_INTEGRATION_MASTER_KEY} (hex-encoded, 32 bytes
 * = 256 bits); if not set, falls back to {@code EDAM_MAIL_MASTER_KEY} only so that existing
 * single-key deployments continue to work, but integration keys are domain-separated from
 * mail keys when an operator sets both. The key is never written to the database, logs,
 * exceptions, or audit payloads. If neither variable is set, encryption refuses to operate
 * (fail-closed) rather than storing plaintext.
 *
 * <p>Ciphertext format (Base64): {@code 12-byte IV || 16-byte GCM tag || ciphertext}.
 * A random IV per encryption is mandatory for GCM safety.
 */
@Component
public class IntegrationSecretCrypto {

	private static final String ALGO = "AES/GCM/NoPadding";
	private static final int GCM_TAG_BITS = 128;
	private static final int IV_BYTES = 12;

	private final SecureRandom random = new SecureRandom();
	private final SecretKeySpec key;

	public IntegrationSecretCrypto(
			@Value("${edam.integrations.master-key:${EDAM_INTEGRATION_MASTER_KEY:${EDAM_MAIL_MASTER_KEY:}}}") String masterKeyHex) {
		if (masterKeyHex == null || masterKeyHex.isBlank()) {
			this.key = null;
			return;
		}
		byte[] keyBytes = hexDecode(masterKeyHex.trim());
		if (keyBytes.length != 32) {
			throw new IllegalStateException(
				"EDAM_INTEGRATION_MASTER_KEY (or EDAM_MAIL_MASTER_KEY fallback) must be 64 hex characters (32 bytes for AES-256).");
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public boolean isAvailable() { return this.key != null; }

	public String encrypt(String plaintext) {
		if (plaintext == null) return null;
		if (!isAvailable()) {
			throw new IllegalStateException(
				"EDAM_INTEGRATION_MASTER_KEY is not configured; cannot encrypt integration credentials.");
		}
		try {
			byte[] iv = new byte[IV_BYTES]; this.random.nextBytes(iv);
			Cipher c = Cipher.getInstance(ALGO);
			c.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
			buf.put(iv).put(ct);
			return Base64.getEncoder().encodeToString(buf.array());
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to encrypt integration credential", ex);
		}
	}

	public String decrypt(String ciphertextB64) {
		if (ciphertextB64 == null) return null;
		if (!isAvailable()) {
			throw new IllegalStateException(
				"EDAM_INTEGRATION_MASTER_KEY is not configured; cannot decrypt integration credentials.");
		}
		try {
			byte[] bytes = Base64.getDecoder().decode(ciphertextB64);
			if (bytes.length < IV_BYTES + 16) {
				throw new IllegalStateException("Ciphertext is malformed.");
			}
			ByteBuffer buf = ByteBuffer.wrap(bytes);
			byte[] iv = new byte[IV_BYTES]; buf.get(iv);
			byte[] ct = new byte[bytes.length - IV_BYTES]; buf.get(ct);
			Cipher c = Cipher.getInstance(ALGO);
			c.init(Cipher.DECRYPT_MODE, this.key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] pt = c.doFinal(ct);
			return new String(pt, StandardCharsets.UTF_8);
		} catch (Exception ex) {
			// Do not leak raw crypto details or ciphertext — mask.
			throw new IllegalStateException("Failed to decrypt integration credential (check master key or ciphertext).");
		}
	}

	/** Deterministic version that changes whenever key bytes change, supporting key-rotation detection. */
	public int keyVersion() {
		return this.key == null ? 0 : this.key.hashCode();
	}

	private static byte[] hexDecode(String s) {
		if ((s.length() & 1) == 1) throw new IllegalArgumentException("Hex string must be even-length");
		byte[] out = new byte[s.length() / 2];
		for (int i = 0; i < out.length; i++) {
			out[i] = (byte) Integer.parseInt(s.substring(i*2, i*2+2), 16);
		}
		return out;
	}
}
