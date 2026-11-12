package com.edam.config;

import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.logging.LogSanitizer;
import com.edam.common.security.EdamAccessDeniedHandler;
import com.edam.common.security.EdamUserPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.common.security.InstitutionContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves and validates the active institution context for each authenticated request.
 * Registered explicitly inside {@link SecurityConfig} after authentication processing
 * (not as a standalone servlet filter) so it runs once per request with an established principal.
 */
public class InstitutionContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InstitutionContextFilter.class);

    private final EdamProperties edamProperties;
    private final EdamAccessDeniedHandler accessDeniedHandler;

    public InstitutionContextFilter(
            EdamProperties edamProperties,
            EdamAccessDeniedHandler accessDeniedHandler) {
        this.edamProperties = edamProperties;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)) {

                String requestedHeader = request.getHeader(edamProperties.getSecurity().getInstitutionHeaderName());
                String requestedCode = (requestedHeader == null || requestedHeader.isBlank())
                        ? null
                        : LogSanitizer.sanitize(requestedHeader).toUpperCase(Locale.ROOT);

                InstitutionContext context;
                if (authentication.getPrincipal() instanceof EdamUserPrincipal principal) {
                    String resolvedCode = requestedCode != null
                            ? requestedCode
                            : principal.getDefaultInstitutionCode();

                    if (resolvedCode == null || !principal.getAccessibleInstitutionCodes().contains(resolvedCode)) {
                        log.warn("Rejected institution context switch requestedCode={} actor={}",
                                resolvedCode, LogSanitizer.sanitize(principal.getUsername()));
                        accessDeniedHandler.handle(
                                request,
                                response,
                                new AccessDeniedException("User does not belong to the requested institution."));
                        return;
                    }

                    context = new InstitutionContext(
                            principal.getInstitutionIdForCode(resolvedCode),
                            resolvedCode,
                            principal.getUserId(),
                            principal.getUsername(),
                            principal.getPermissionsForInstitution(resolvedCode));
                } else {
                    String fallbackCode = requestedCode != null
                            ? requestedCode
                            : edamProperties.getDefaultInstitution().getCode();
                    Set<String> authorities = authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toUnmodifiableSet());
                    context = new InstitutionContext(
                            null,
                            fallbackCode,
                            null,
                            authentication.getName(),
                            authorities);
                }

                InstitutionContextHolder.set(context);
                MDC.put(CorrelationIdProvider.MDC_INSTITUTION_CODE, LogSanitizer.sanitize(context.institutionCode()));
                MDC.put(CorrelationIdProvider.MDC_ACTOR, LogSanitizer.sanitize(context.username()));
            }

            filterChain.doFilter(request, response);
        } finally {
            InstitutionContextHolder.clear();
            MDC.remove(CorrelationIdProvider.MDC_INSTITUTION_CODE);
            MDC.remove(CorrelationIdProvider.MDC_ACTOR);
        }
    }
}
