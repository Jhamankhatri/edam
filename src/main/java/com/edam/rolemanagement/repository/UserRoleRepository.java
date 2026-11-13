package com.edam.rolemanagement.repository;

import java.util.List;
import java.util.UUID;

import com.edam.rolemanagement.model.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;

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

}
