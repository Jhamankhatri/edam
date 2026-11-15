package com.edam.documents.scan;

import java.io.InputStream;

/**
 * Placeholder scanner that fails closed when no production scanner is configured.
 *
 * <p>This class is deliberately NOT a Spring bean: production deployments register exactly
 * one {@link MalwareScanner} via configuration (ClamAV, cloud scanner, etc.) and the
 * {@link com.edam.config.ScanAutoConfiguration} adds this fail-closed fallback only when
 * no other scanner is present. The tests register their own {@code @Primary} fake scanner.
 */
public class FailClosedScanner implements MalwareScanner {

	public static final String NAME = "none-fail-closed";

	@Override public String name() { return NAME; }
	@Override public boolean isAvailable() { return false; }

	@Override
	public ScanResult scan(InputStream content) {
		return new ScanResult("SCAN_FAILED", null, null,
				"No approved malware scanner is configured; fail-closed quarantine is in effect. "
				+ "Integrate a production scanner before enabling document downloads.");
	}
}
