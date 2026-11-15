package com.edam.documents.scan;

import java.io.InputStream;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Default fail-closed scanner used when no production scanner is wired. Returns
 * SCAN_FAILED for every scan so that new documents remain quarantined until a real
 * scanner is configured and run. Tests override this with a {@code @Primary} fake.
 */
@Component
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
