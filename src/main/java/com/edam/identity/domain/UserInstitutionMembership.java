package com.edam.identity.domain;

import com.edam.common.audit.AuditableEntity;
import com.edam.institution.domain.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;

/**
 * Links a {@link UserAccount} to an {@link Institution} with institution-specific
 * department/employee metadata and assigned roles. Enables one user to participate
 * in multiple financial institutions with distinct permissions in each.
 */
@Entity
@Table(
        name = "user_institution_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_inst_membership",
                columnNames = {"user_id", "institution_id"}))
public class UserInstitutionMembership extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "employee_code", length = 64)
    private String employeeCode;

    @Column(name = "department_code", length = 64)
    private String departmentCode;

    @Column(name = "is_default", nullable = false)
    private boolean defaultMembership;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_institution_roles",
            joinColumns = @JoinColumn(name = "membership_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected UserInstitutionMembership() {
    }

    public UserInstitutionMembership(
            UserAccount user,
            Institution institution,
            String employeeCode,
            String departmentCode,
            boolean defaultMembership,
            MembershipStatus status) {
        this.user = user;
        this.institution = institution;
        this.employeeCode = employeeCode;
        this.departmentCode = departmentCode;
        this.defaultMembership = defaultMembership;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public Institution getInstitution() {
        return institution;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public boolean isDefaultMembership() {
        return defaultMembership;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public void assignRole(Role role) {
        this.roles.add(role);
    }
}
