package com.edam.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated user principal supporting memberships across multiple financial institutions.
 */
public class EdamUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final String defaultInstitutionCode;
    private final Map<String, Long> institutionIdsByCode;
    private final Map<String, Set<String>> permissionsByInstitutionCode;
    private final List<GrantedAuthority> authorities;

    public EdamUserPrincipal(
            Long userId,
            String username,
            String displayName,
            String passwordHash,
            boolean enabled,
            boolean accountNonLocked,
            String defaultInstitutionCode,
            Map<String, Long> institutionIdsByCode,
            Map<String, Set<String>> permissionsByInstitutionCode,
            Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.defaultInstitutionCode = defaultInstitutionCode;
        this.institutionIdsByCode = institutionIdsByCode == null ? Map.of() : Map.copyOf(institutionIdsByCode);
        this.permissionsByInstitutionCode = permissionsByInstitutionCode == null
                ? Map.of()
                : Map.copyOf(permissionsByInstitutionCode);
        this.authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultInstitutionCode() {
        return defaultInstitutionCode;
    }

    public Set<String> getAccessibleInstitutionCodes() {
        return institutionIdsByCode.keySet();
    }

    public Long getInstitutionIdForCode(String institutionCode) {
        return institutionIdsByCode.get(institutionCode);
    }

    public Set<String> getPermissionsForInstitution(String institutionCode) {
        return permissionsByInstitutionCode.getOrDefault(institutionCode, Set.of());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
