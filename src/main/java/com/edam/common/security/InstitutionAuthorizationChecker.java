package com.edam.common.security;

import org.springframework.stereotype.Component;

/**
 * Institution-aware authorization evaluator usable from Spring Security {@code @PreAuthorize}
 * expressions (e.g. {@code @PreAuthorize("@edamAuth.hasPermission('INSTITUTION_CONFIG_VIEW')")}).
 * Ensures permissions granted in Institution A never authorize actions in Institution B.
 */
@Component("edamAuth")
public class InstitutionAuthorizationChecker {

    private final CurrentUserContext currentUserContext;

    public InstitutionAuthorizationChecker(CurrentUserContext currentUserContext) {
        this.currentUserContext = currentUserContext;
    }

    public boolean hasPermission(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return currentUserContext.getInstitutionContext()
                .map(ctx -> ctx.hasPermission(permissionCode))
                .orElse(false);
    }
}
