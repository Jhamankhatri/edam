package com.edam.documents.scan;

import java.io.InputStream;

import org.springframework.stereotype.Component;

/**
 * Placeholder scanner that fails closed when no production scanner is configured.
 *
 * <p>The BRD is explicit: "Never automatically mark a document clean because a scanner is
 * unavailable." This component returns SCAN_FAILED for every upload, which keeps the
 * document quarantined (downloads are rejected) until an operator integrates a real
 * scanner and an explicit rescan is performed. No attempt is made to scan the bytes here.
 */
@Component
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
