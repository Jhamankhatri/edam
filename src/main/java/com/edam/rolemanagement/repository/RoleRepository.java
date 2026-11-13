package com.edam.rolemanagement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.rolemanagement.model.Role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Role catalogue (Module C). */
public interface RoleRepository extends JpaRepository<Role, UUID> {

	Optional<Role> findByCode(String code);

	boolean existsByCode(String code);

	List<Role> findByActiveTrueOrderByNameAsc();

	/**
	 * Roles held by a user, excluding expired grants.
	 *
	 * <p>The expiry predicate is part of the query rather than a filter applied afterwards, so it cannot
	 * be forgotten by a caller. {@code left join fetch} keeps the permission set in one statement,
	 * because authentication happens once per request and N+1 here would be N+1 on every single request.
	 */
	@Query("""
			select distinct r from Role r
			left join fetch r.permissions
			where r.active = true
			  and exists (
			      select 1 from UserRole ur
			      where ur.roleId = r.id
			        and ur.userId = :userId
			        and (ur.expiresAt is null or ur.expiresAt > current_timestamp))
			""")
	List<Role> findActiveRolesForUser(@Param("userId") UUID userId);

}
