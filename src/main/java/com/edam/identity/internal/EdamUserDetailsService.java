package com.edam.identity.internal;

import com.edam.common.logging.LogSanitizer;
import com.edam.common.security.EdamUserPrincipal;
import com.edam.identity.domain.MembershipStatus;
import com.edam.identity.domain.Permission;
import com.edam.identity.domain.Role;
import com.edam.identity.domain.UserAccount;
import com.edam.identity.domain.UserInstitutionMembership;
import com.edam.identity.domain.UserStatus;
import com.edam.institution.domain.Institution;
import com.edam.institution.domain.InstitutionStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads application users along with all active institution memberships and
 * institution-scoped permissions without hardcoding client roles or assuming single-institution membership.
 */
@Service
@Transactional(readOnly = true)
public class EdamUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(EdamUserDetailsService.class);

    private final UserAccountRepository userAccountRepository;
    private final UserInstitutionMembershipRepository membershipRepository;

    public EdamUserDetailsService(
            UserAccountRepository userAccountRepository,
            UserInstitutionMembershipRepository membershipRepository) {
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Username must not be blank.");
        }

        String normalizedUsername = username.trim();
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> {
                    log.warn("Authentication lookup failed for unknown username={}",
                            LogSanitizer.sanitize(normalizedUsername));
                    return new UsernameNotFoundException("Invalid user credentials.");
                });

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            log.warn("Local password authentication attempted for non-local user={}",
                    LogSanitizer.sanitize(user.getUsername()));
            throw new UsernameNotFoundException("Local authentication is not enabled for this account.");
        }

        List<UserInstitutionMembership> memberships = membershipRepository
                .findActiveMembershipsWithPermissions(user.getId(), MembershipStatus.ACTIVE);

        Map<String, Long> institutionIdsByCode = new HashMap<>();
        Map<String, Set<String>> permissionsByInstitutionCode = new HashMap<>();
        String defaultInstitutionCode = null;

        List<UserInstitutionMembership> activeInstitutionMemberships = memberships.stream()
                .filter(m -> m.getInstitution() != null
                        && m.getInstitution().getStatus() == InstitutionStatus.ACTIVE)
                .sorted(Comparator.comparing(m -> m.getInstitution().getCode()))
                .toList();

        for (UserInstitutionMembership membership : activeInstitutionMemberships) {
            Institution inst = membership.getInstitution();
            String instCode = inst.getCode().toUpperCase(Locale.ROOT);
            institutionIdsByCode.put(instCode, inst.getId());

            Set<String> instPermissions = new HashSet<>();
            for (Role role : membership.getRoles()) {
                boolean matchingInstitution = role.getInstitution() == null
                        || role.getInstitution().getId().equals(inst.getId());
                if (role.isActive() && matchingInstitution) {
                    for (Permission permission : role.getPermissions()) {
                        instPermissions.add(permission.getCode());
                    }
                }
            }
            permissionsByInstitutionCode.put(instCode, Set.copyOf(instPermissions));

            if (membership.isDefaultMembership() || defaultInstitutionCode == null) {
                defaultInstitutionCode = instCode;
            }
        }

        // Session-level authorities remain neutral; institution-scoped permissions are evaluated
        // per request via InstitutionContext and InstitutionAuthorizationChecker (@edamAuth).
        Set<GrantedAuthority> sessionAuthorities = Set.of(new SimpleGrantedAuthority("AUTHENTICATED_USER"));

        boolean enabled = user.getStatus() == UserStatus.ACTIVE && !activeInstitutionMemberships.isEmpty();
        boolean nonLocked = user.isAccountCurrentlyUnlocked(Instant.now());

        return new EdamUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPasswordHash(),
                enabled,
                nonLocked,
                defaultInstitutionCode,
                institutionIdsByCode,
                permissionsByInstitutionCode.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)),
                sessionAuthorities);
    }
}
