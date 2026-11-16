package com.edam.communication;

import java.util.HexFormat;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import com.edam.communication.crypto.MailCredentialCrypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailCredentialCryptoTest {

	@Test
	void roundTripSucceeds() {
		String key = generateKeyHex();
		MailCredentialCrypto crypto = new MailCredentialCrypto(key);
		String ciphertext = crypto.encrypt("s3cr3t-P@ss!");
		assertThat(ciphertext).isNotEqualTo("s3cr3t-P@ss!");
		assertThat(crypto.decrypt(ciphertext)).isEqualTo("s3cr3t-P@ss!");
	}

	@Test
	void differentCiphertextForSamePlaintext() {
		String key = generateKeyHex();
		MailCredentialCrypto crypto = new MailCredentialCrypto(key);
		String a = crypto.encrypt("s3cr3t");
		String b = crypto.encrypt("s3cr3t");
		assertThat(a).isNotEqualTo(b);
		assertThat(crypto.decrypt(a)).isEqualTo("s3cr3t");
		assertThat(crypto.decrypt(b)).isEqualTo("s3cr3t");
	}

	private static String generateKeyHex() {
		try {
			KeyGenerator g = KeyGenerator.getInstance("AES");
			g.init(256);
			SecretKey k = g.generateKey();
			return HexFormat.of().formatHex(k.getEncoded());
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
