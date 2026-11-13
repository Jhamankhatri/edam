package com.edam.config;

import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.EdamUserDetailsService;
import com.edam.common.security.JsonApiAccessDeniedHandler;
import com.edam.common.security.JsonApiAuthenticationEntryPoint;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Security foundation.
 *
 * <h2>Separation of concerns</h2>
 * <ul>
 * <li><strong>Authentication</strong> — delegated to {@link EdamUserDetailsService}. This
 * configuration ships form login and, in the {@code local} profile only, an in-memory fixture store.
 * No SSO/AD/OIDC/SAML decision is made here; that remains an open business decision, and the seam is
 * what makes swapping it a bean change rather than a rewrite.</li>
 * <li><strong>Authorization</strong> — server-side only, through URL rules here and
 * {@link com.edam.common.security.EdamPermission} on every controller method. Hiding a control in the
 * React client is never a control.</li>
 * <li><strong>Organizational scope</strong> — resolved per user from
 * {@link com.edam.common.security.DataScope}. There is no tenant to select: one installation serves one
 * bank.</li>
 * </ul>
 *
 * <h2>What is public, and why that list is short</h2>
 * The error dispatch, the OpenAPI schema, the Swagger UI assets and the health probes. Everything else
 * requires authentication. Note that being authenticated is <em>not</em> being authorized: every
 * controller method also carries a permission, enforced by {@code RequirePermissionAspect} and proven
 * by {@code EndpointAuthorizationArchitectureTest}.
 *
 * <h2>CSRF is deliberately still enabled</h2>
 * A JSON API is not automatically exempt. The session cookie is attached by the browser regardless of
 * who initiated the request, so without a token a cross-site form post could approve a request as the
 * signed-in approver. A React client forwards the token from the {@code XSRF-TOKEN} cookie in the
 * {@code X-XSRF-TOKEN} header; command-line callers fetch a token first.
 *
 * <h2>Content Security Policy</h2>
 * {@code default-src 'none'} rather than a page-friendly policy: EDAM serves JSON and the React client
 * is deployed separately. A JSON response never needs to load a script, style or font, so if a browser
 * is ever induced to render one as HTML, nothing in it can execute.
 */

