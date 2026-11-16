package com.edam.communication;

import java.util.Map;
import java.util.Set;

import com.edam.common.exception.BusinessException;
import com.edam.communication.support.TemplateRenderer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRendererTest {

	private final TemplateRenderer renderer = new TemplateRenderer();

	@Test
	void substitutesAllowedPlaceholdersInText() {
		String out = renderer.renderText("Hello {{name}}, your request {{id}} was approved.",
				Map.of("name", "Alice", "id", "R-17"), Set.of("name", "id"));
		assertThat(out).isEqualTo("Hello Alice, your request R-17 was approved.");
	}

	@Test
	void htmlEscapesUserValues() {
		String out = renderer.renderHtml("<p>Hi {{name}}</p>",
				Map.of("name", "<script>alert(1)</script>"), Set.of("name"));
		assertThat(out).doesNotContain("<script>");
		assertThat(out).contains("&lt;script&gt;");
	}

	@Test
	void rejectsUnknownPlaceholders() {
		assertThatThrownBy(() -> renderer.renderHtml("{{evil}}", Map.of(), Set.of("name")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("disallowed");
	}

	@Test
	void stripsScriptTagsViaSanitizer() {
		String html = "<p>Hi</p><script>alert(1)</script><a onclick='xss()' href='javascript:bad()'>x</a>";
		String out = renderer.renderHtml(html, Map.of(), Set.of());
		assertThat(out).doesNotContain("<script>", "onclick", "javascript:");
	}

	@Test
	void extractReturnsAllPlaceholders() {
		Set<String> ph = renderer.extractPlaceholders("Dear {{requester}}, ref {{requestId}}.");
		assertThat(ph).containsExactlyInAnyOrder("requester", "requestId");
	}
}
