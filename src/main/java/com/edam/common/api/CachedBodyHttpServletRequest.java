package com.edam.common.api;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Request wrapper that buffers the entire body on construction so that the input stream can be
 * read repeatedly — once by the idempotency interceptor for fingerprinting and again by Spring
 * MVC's argument resolvers. Content length is bounded by the server's configured
 * {@code max-http-form-post-size} / {@code spring.servlet.multipart.max-request-size} and by
 * the fact that idempotency endpoints accept small JSON payloads; this wrapper is not intended
 * for multipart or streaming uploads.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

	private final byte[] cachedBody;
	private final Charset charset;

	CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
		super(request);
		this.charset = resolveCharset(request);
		this.cachedBody = request.getInputStream().readAllBytes();
	}

	byte[] cachedBytes() {
		return this.cachedBody;
	}

	String cachedBodyAsString() {
		return new String(this.cachedBody, this.charset);
	}

	@Override
	public ServletInputStream getInputStream() {
		ByteArrayInputStream delegate = new ByteArrayInputStream(this.cachedBody);
		return new ServletInputStream() {
			@Override
			public boolean isFinished() {
				return delegate.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener readListener) {
				throw new UnsupportedOperationException("Cached body does not support async read listeners.");
			}

			@Override
			public int read() {
				return delegate.read();
			}

			@Override
			public int read(byte[] b, int off, int len) {
				return delegate.read(b, off, len);
			}
		};
	}

	@Override
	public BufferedReader getReader() {
		return new BufferedReader(new InputStreamReader(getInputStream(), this.charset));
	}

	@Override
	public int getContentLength() {
		return this.cachedBody.length;
	}

	@Override
	public long getContentLengthLong() {
		return this.cachedBody.length;
	}

	private static Charset resolveCharset(HttpServletRequest request) {
		String enc = request.getCharacterEncoding();
		if (enc == null || enc.isBlank()) {
			return StandardCharsets.UTF_8;
		}
		try {
			return Charset.forName(enc);
		}
		catch (Exception ex) {
			return StandardCharsets.UTF_8;
		}
	}

}
