package com.edam.communication.crypto;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption/decryption for SMTP passwords. The master key is loaded from the
 * {@code EDAM_MAIL_MASTER_KEY} environment variable (hex-encoded, 32 bytes = 256 bits) and
 * is never written to the database or logs. If the variable is not set, encryption refuses
 * to operate rather than falling back to a hardcoded key.
 *
 * <p>Ciphertext format (Base64): {@code 12-byte IV || 16-byte GCM tag || ciphertext}.
 * A random IV per encryption is mandatory for GCM safety.
 */
@Component
public class MailCredentialCrypto {

	private static final String ALGO = "AES/GCM/NoPadding";
	private static final int GCM_TAG_BITS = 128;
	private static final int IV_BYTES = 12;

	private final SecureRandom random = new SecureRandom();
	private final SecretKeySpec key;

	public MailCredentialCrypto(
			@Value("${edam.mail.master-key:${EDAM_MAIL_MASTER_KEY:}}") String masterKeyHex) {
		if (masterKeyHex == null || masterKeyHex.isBlank()) {
			// Refuse to start rather than storing plaintext.
			this.key = null;
			return;
		}
		byte[] keyBytes = hexDecode(masterKeyHex.trim());
		if (keyBytes.length != 32) {
			throw new IllegalStateException("EDAM_MAIL_MASTER_KEY must be 64 hex characters (32 bytes for AES-256).");
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public boolean isAvailable() { return this.key != null; }

	public String encrypt(String plaintext) {
		if (plaintext == null) return null;
		if (!isAvailable()) throw new IllegalStateException("EDAM_MAIL_MASTER_KEY is not configured; cannot encrypt credentials.");
		try {
			byte[] iv = new byte[IV_BYTES]; this.random.nextBytes(iv);
			Cipher c = Cipher.getInstance(ALGO);
			c.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] ct = c.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
			buf.put(iv).put(ct);
			return Base64.getEncoder().encodeToString(buf.array());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to encrypt credential", ex);
		}
	}

	public String decrypt(String ciphertextB64) {
		if (ciphertextB64 == null) return null;
		if (!isAvailable()) throw new IllegalStateException("EDAM_MAIL_MASTER_KEY is not configured; cannot decrypt credentials.");
		try {
			byte[] bytes = Base64.getDecoder().decode(ciphertextB64);
			ByteBuffer buf = ByteBuffer.wrap(bytes);
			byte[] iv = new byte[IV_BYTES]; buf.get(iv);
			byte[] ct = new byte[bytes.length - IV_BYTES]; buf.get(ct);
			Cipher c = Cipher.getInstance(ALGO);
			c.init(Cipher.DECRYPT_MODE, this.key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] pt = c.doFinal(ct);
			return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (Exception ex) {
			// Do not leak raw crypto details — mask.
			throw new IllegalStateException("Failed to decrypt credential (check master key or ciphertext).");
		}
	}

	/** Deterministic "version" that changes whenever the key bytes change, allowing rotation detection. */
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
