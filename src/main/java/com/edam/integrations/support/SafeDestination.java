package com.edam.integrations.support;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;

/**
 * SSRF guard: rejects destination URLs that resolve to loopback, link-local, private,
 * or cloud-metadata ranges.
 *
 * <p>This guard runs whenever an adapter accepts a user-supplied URL (e.g. generic HTTP
 * webhooks in a later provider). In Phase 4H no shipped adapter exposes an arbitrary HTTP
 * endpoint config, but the guard is here so future adapters cannot accidentally forget it.
 */
public final class SafeDestination {

	private SafeDestination() {}

	/**
	 * Validate that {@code url} is a well-formed HTTPS URL that does not resolve to a
	 * forbidden IP range. Throws {@link BusinessException} with {@code VALIDATION_FAILED}
	 * if unsafe.
	 */
	public static void requireSafeHttps(String url) {
		if (url == null || url.isBlank()) return;
		URI u;
		try {
			u = URI.create(url.trim());
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Endpoint URL is not a valid URI.");
		}
		String scheme = u.getScheme();
		if (scheme == null || (!"https".equalsIgnoreCase(scheme))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Endpoint URL must use https://");
		}
		String host = u.getHost();
		if (host == null || host.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Endpoint URL must include a host.");
		}
		if (u.getRawUserInfo() != null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
				"Credentials may not be embedded in the endpoint URL. Use the dedicated credential fields.");
		}
		InetAddress[] addrs;
		try {
			addrs = InetAddress.getAllByName(host);
		} catch (UnknownHostException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Endpoint host could not be resolved: " + host);
		}
		for (InetAddress a : addrs) {
			if (isForbidden(a)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Endpoint resolves to a forbidden (private/loopback/link-local) address.");
			}
		}
	}

	private static boolean isForbidden(InetAddress a) {
		return a.isLoopbackAddress()
			|| a.isLinkLocalAddress()
			|| a.isSiteLocalAddress()
			|| a.isAnyLocalAddress()
			|| a.isMulticastAddress()
			|| a.isLinkLocalAddress()
			|| a.getHostAddress() == null
			|| isCloudMetadata(a);
	}

	private static boolean isCloudMetadata(InetAddress a) {
		// 169.254.169.254 (AWS/GCP/Azure IMDS) and fd00:ec2::254 (AWS IMDSv6).
		byte[] b = a.getAddress();
		if (b.length == 4) {
			return (b[0] & 0xff) == 169 && (b[1] & 0xff) == 254
				&& (b[2] & 0xff) == 169 && (b[3] & 0xff) == 254;
		}
		return false;
	}
}
