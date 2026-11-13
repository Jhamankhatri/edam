package com.edam.rolemanagement.repository;

import java.util.List;
import java.util.UUID;

import com.edam.rolemanagement.model.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Role assignments (Module C).
 *
 * <p>{@link #findByUserId} returns every grant including expired ones, because the administration
 * screen must show history. Authorization never uses this finder — it uses
 * {@code RoleRepository#findActiveRolesForUser}, which applies the expiry predicate in SQL.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

	List<UserRole> findByUserId(UUID userId);

	boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

	void deleteByUserId(UUID userId);

	/**
	 * Active holders of a role, as user ids.
	 *
	 * <p>This exists so approval assignment does not have to scan every user and re-check their roles one at
	 * a time: on a bank with thousands of employees that pattern is a full-table scan plus N queries for a
	 * single assignment. The expiry predicate is repeated here rather than delegated, because this query is
	 * the one that decides whether someone can approve — it must be self-evidently correct on its own.
	 */
	@Query("""
			select ur.userId from UserRole ur
			join User u on u.id = ur.userId
			where ur.roleId = :roleId
			  and u.active = true
			  and (ur.expiresAt is null or ur.expiresAt > current_timestamp)
			""")
	List<UUID> findActiveHolderIds(@Param("roleId") UUID roleId);

}
