package com.edam.common.security;

import java.util.Set;

/**
 * Immutable active institution and actor context bound to the current request thread.
 */
public record InstitutionContext(
        Long institutionId,
        String institutionCode,
        Long userId,
        String username,
        Set<String> permissions) {

    public InstitutionContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.contains(permissionCode);
    }
}
