package com.edam.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edam.common.security.EdamUserPrincipal;
import com.edam.common.security.PermissionConstants;
import com.edam.identity.domain.MembershipStatus;
import com.edam.identity.domain.Permission;
import com.edam.identity.domain.Role;
import com.edam.identity.domain.UserAccount;
import com.edam.identity.domain.UserInstitutionMembership;
import com.edam.identity.domain.UserStatus;
import com.edam.institution.domain.Institution;
import com.edam.institution.domain.InstitutionStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

class EdamUserDetailsServiceTest {

    private UserAccountRepository userAccountRepository;
    private UserInstitutionMembershipRepository membershipRepository;
    private EdamUserDetailsService service;

    @BeforeEach
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        membershipRepository = mock(UserInstitutionMembershipRepository.class);
        service = new EdamUserDetailsService(userAccountRepository, membershipRepository);
    }

    @Test
    void loadsMultiInstitutionMembershipsWithInstitutionScopedPermissions() {
        UserAccount user = new UserAccount(
                "j.khatri",
                "j.khatri@example.org",
                "J. Khatri",
                "{bcrypt}hash",
                "LOCAL",
                UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 10L);

        Institution instA = institution(1L, "INST_A", InstitutionStatus.ACTIVE);
        Institution instB = institution(2L, "INST_B", InstitutionStatus.ACTIVE);

        Permission configView = new Permission(PermissionConstants.INSTITUTION_CONFIG_VIEW, "INSTITUTION", "View");
        Permission auditView = new Permission(PermissionConstants.AUDIT_LOG_VIEW, "AUDIT", "Audit");

        Role roleA = new Role(instA, "CONFIG_READER", "Config Reader", "Desc", true);
        roleA.addPermission(configView);
        Role roleB = new Role(instB, "AUDITOR", "Auditor", "Desc", true);
        roleB.addPermission(auditView);

        UserInstitutionMembership membershipA = new UserInstitutionMembership(
                user, instA, "EMP-1", "OPS", true, MembershipStatus.ACTIVE);
        membershipA.assignRole(roleA);

        UserInstitutionMembership membershipB = new UserInstitutionMembership(
                user, instB, "EMP-2", "FIN", false, MembershipStatus.ACTIVE);
        membershipB.assignRole(roleB);

        when(userAccountRepository.findByUsernameIgnoreCase("j.khatri")).thenReturn(Optional.of(user));
        when(membershipRepository.findActiveMembershipsWithPermissions(10L, MembershipStatus.ACTIVE))
                .thenReturn(List.of(membershipA, membershipB));

        UserDetails details = service.loadUserByUsername("j.khatri");
        assertThat(details).isInstanceOf(EdamUserPrincipal.class);

        EdamUserPrincipal principal = (EdamUserPrincipal) details;
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getDefaultInstitutionCode()).isEqualTo("INST_A");
        assertThat(principal.getAccessibleInstitutionCodes()).containsExactlyInAnyOrder("INST_A", "INST_B");
        assertThat(principal.getPermissionsForInstitution("INST_A"))
                .containsExactly(PermissionConstants.INSTITUTION_CONFIG_VIEW);
        assertThat(principal.getPermissionsForInstitution("INST_B"))
                .containsExactly(PermissionConstants.AUDIT_LOG_VIEW);
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("AUTHENTICATED_USER");
    }

    @Test
    void ignoresMismatchedCrossInstitutionRoleWhenResolvingPermissions() {
        UserAccount user = new UserAccount(
                "alice",
                "alice@example.org",
                "Alice",
                "{bcrypt}hash",
                "LOCAL",
                UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 11L);

        Institution instA = institution(1L, "INST_A", InstitutionStatus.ACTIVE);
        Institution instOther = institution(99L, "INST_OTHER", InstitutionStatus.ACTIVE);

        Permission auditView = new Permission(PermissionConstants.AUDIT_LOG_VIEW, "AUDIT", "Audit");
        Role mismatchedRole = new Role(instOther, "FOREIGN_ROLE", "Foreign", "Desc", true);
        mismatchedRole.addPermission(auditView);

        UserInstitutionMembership membershipA = new UserInstitutionMembership(
                user, instA, "EMP-9", "FIN", true, MembershipStatus.ACTIVE);
        membershipA.assignRole(mismatchedRole);

        when(userAccountRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(membershipRepository.findActiveMembershipsWithPermissions(11L, MembershipStatus.ACTIVE))
                .thenReturn(List.of(membershipA));

        EdamUserPrincipal principal = (EdamUserPrincipal) service.loadUserByUsername("alice");
        assertThat(principal.getPermissionsForInstitution("INST_A")).isEmpty();
    }

    @Test
    void throwsUsernameNotFoundExceptionWhenUserMissing() {
        when(userAccountRepository.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private Institution institution(Long id, String code, InstitutionStatus status) {
        Institution institution = new Institution(
                code,
                code + " Bank",
                code,
                code + " Ltd",
                status,
                "USD",
                "en",
                "UTC");
        ReflectionTestUtils.setField(institution, "id", id);
        return institution;
    }
}
