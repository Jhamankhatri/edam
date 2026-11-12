package com.edam.identity.internal;

import com.edam.identity.domain.MembershipStatus;
import com.edam.identity.domain.UserInstitutionMembership;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserInstitutionMembershipRepository extends JpaRepository<UserInstitutionMembership, Long> {

    @Query("""
            SELECT DISTINCT m
            FROM UserInstitutionMembership m
            JOIN FETCH m.institution inst
            LEFT JOIN FETCH m.roles r
            LEFT JOIN FETCH r.permissions p
            WHERE m.user.id = :userId
              AND m.status = :status
            """)
    List<UserInstitutionMembership> findActiveMembershipsWithPermissions(
            @Param("userId") Long userId,
            @Param("status") MembershipStatus status);
}
