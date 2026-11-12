package com.edam.common.security;

import com.edam.common.exception.AccessDeniedDomainException;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Clean facade for reading the current authenticated actor and active institution context
 * across application services without coupling directly to servlet APIs.
 */
@Component
public class CurrentUserContext {

    public Optional<InstitutionContext> getInstitutionContext() {
        return InstitutionContextHolder.get();
    }

    public InstitutionContext requireInstitutionContext() {
        return InstitutionContextHolder.get()
                .orElseThrow(() -> new AccessDeniedDomainException(
                        "An active institution context is required to perform this operation."));
    }

    public Optional<String> getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName());
    }
}
