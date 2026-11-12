package com.edam.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routing errors to a JSON envelope or an HTML page is an acceptance concern: an operator
 * must never receive JSON in the browser, and an API client must never receive HTML.
 */
class ApiPathsTest {

	@Test
	@DisplayName("API URIs always receive JSON, regardless of Accept")
	void apiPrefixWins() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/expense/requests");
		request.addHeader("Accept", "text/html");

		assertThat(ApiPaths.wantsApiResponse(request)).isTrue();
	}

	@Test
	@DisplayName("a JSP form post keeps the HTML flow even though browsers accept JSON")
	void formPostKeepsHtml() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/opex/save");
		request.addHeader("Accept", "text/html,application/xhtml+xml,application/json;q=0.9");
		request.setContentType("application/x-www-form-urlencoded");

		assertThat(ApiPaths.wantsApiResponse(request)).isFalse();
	}

	@Test
	@DisplayName("a JSON request body selects the API envelope")
	void jsonContentTypeSelectsApi() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/opex/save");
		request.setContentType("application/json");

		assertThat(ApiPaths.wantsApiResponse(request)).isTrue();
	}

	@Test
	@DisplayName("a pure JSON Accept header from a non-browser client selects the API envelope")
	void jsonAcceptSelectsApi() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/opex/register");
		request.addHeader("Accept", "application/json");

		assertThat(ApiPaths.wantsApiResponse(request)).isTrue();
	}

	@Test
	@DisplayName("the context path is stripped from the reported path")
	void stripsContextPath() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/edam/dashboard");
		request.setContextPath("/edam");

		assertThat(ApiPaths.applicationPath(request)).isEqualTo("/dashboard");
	}

	@Test
	@DisplayName("browser navigation to a page is not an API request")
	void browserNavigationIsHtml() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
		request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

		assertThat(ApiPaths.wantsApiResponse(request)).isFalse();
	}

	@Test
	@DisplayName("a null URI degrades to the root path instead of throwing")
	void nullUriIsSafe() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		assertThat(ApiPaths.applicationPath(request)).isEqualTo("/");
	}

}
