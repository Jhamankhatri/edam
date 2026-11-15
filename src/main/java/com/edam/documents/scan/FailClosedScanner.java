package com.edam.documents.scan;

import java.io.InputStream;

/**
 * Default fail-closed scanner returned when no other {@link MalwareScanner} is configured.
 * Returns {@code SCAN_FAILED} for every scan so new documents remain quarantined until a
 * real scanner is wired in. This class is intentionally NOT annotated {@code @Component}:
 * registration is handled by {@link ScannerFallbackConfiguration} with an explicit
 * {@code @ConditionalOnMissingBean}, which is the only Spring ordering that reliably
 * respects {@code @Primary} test fakes.
 */
public class FailClosedScanner implements MalwareScanner {

	public static final String NAME = "none-fail-closed";

	@Override public String name() { return NAME; }
	@Override public boolean isAvailable() { return false; }

	@Override
	public ScanResult scan(InputStream content) {
		return new ScanResult("SCAN_FAILED", null, null,
				"No approved malware scanner is configured; fail-closed quarantine is in effect.");
	}
}
