package com.edam.usermanagement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.usermanagement.model.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * User persistence (Module C).
 *
 * <h2>Case-insensitive lookups</h2>
 * {@code findByUsernameIgnoreCase} is used for authentication because usernames are typed by humans,
 * and a login that fails because of a capital letter is a support ticket, not a security control.
 * Uniqueness is still case-sensitive at the database level; the service normalises on write.
 *
 * <h2>Active-only finders</h2>
 * Deactivated users must never authenticate, so the identity adapter uses
 * {@link #findByUsernameIgnoreCase(String)} and checks {@code active}, or the active-only variant.
 * There is no finder that returns a credential hash to a controller: hashes never leave the service.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByUsernameIgnoreCase(String username);

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByUsernameIgnoreCase(String username);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

	List<User> findByDepartmentIdAndActiveTrue(UUID departmentId);

	Page<User> findByActiveTrue(Pageable pageable);

	@Query("""
			select u from User u
			where (:search is null
			       or lower(u.username) like lower(concat('%', :search, '%'))
			       or lower(u.firstName) like lower(concat('%', :search, '%'))
			       or lower(u.lastName) like lower(concat('%', :search, '%'))
			       or lower(u.email) like lower(concat('%', :search, '%'))
			       or lower(u.employeeCode) like lower(concat('%', :search, '%')))
			  and (:departmentId is null or u.departmentId = :departmentId)
			order by u.lastName, u.firstName
			""")
	Page<User> search(@Param("search") String search, @Param("departmentId") UUID departmentId,
			Pageable pageable);

}
