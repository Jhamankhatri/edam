package com.edam.documents.scan;

import java.io.InputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default fail-closed scanner registered only when no other {@link MalwareScanner} is
 * present. Returns SCAN_FAILED for every scan so new documents remain quarantined until a
 * real scanner is configured and run.
 */
@Component
@ConditionalOnMissingBean(MalwareScanner.class)
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
