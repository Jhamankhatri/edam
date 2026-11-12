package com.edam.common.audit;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Supplies the authenticated actor username to Spring Data JPA auditing,
 * falling back to {@code SYSTEM} for migrations or background lifecycle tasks.
 */
@Component("springSecurityAuditorAware")
public class SpringSecurityAuditorAware implements AuditorAware<String> {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of(SYSTEM_ACTOR);
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return Optional.of(SYSTEM_ACTOR);
        }
        return Optional.of(name.length() > 120 ? name.substring(0, 120) : name);
    }
}
