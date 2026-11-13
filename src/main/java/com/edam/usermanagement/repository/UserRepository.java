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


	/**
	 * Data-scoped search, with the visibility predicate applied inside the query.
	 *
	 * <h2>Why the predicate is here and not in the service</h2>
	 * Filtering after the query would break paging: the page would contain only the visible subset while
	 * the count described every user, so a client would paginate onto empty pages and display an inflated
	 * total. The count and the rows must describe the same set.
	 *
	 * <p>The "installation-wide" boolean short-circuits the scope predicates. That is a deliberate
	 * simplification of an OR-chain: a caller scoped everywhere should not pay for three
	 * {@code IN} lists, and the alternative — a dynamic Specification — would hide the predicate from
	 * anyone reading this interface.
	 *
	 * @param installationWide when true, no scope narrowing is applied
	 * @param departmentIds the caller's department scope; empty means none
	 * @param branchIds the caller's branch scope; empty means none
	 * @param callerId always visible to themselves, so they can see their own record
	 */
	@Query("""
			select u from User u
			where (:search is null
			       or lower(u.username) like lower(concat('%', :search, '%'))
			       or lower(u.firstName) like lower(concat('%', :search, '%'))
			       or lower(u.lastName) like lower(concat('%', :search, '%'))
			       or lower(u.email) like lower(concat('%', :search, '%'))
			       or lower(u.employeeCode) like lower(concat('%', :search, '%')))
			  and (:departmentId is null or u.departmentId = :departmentId)
			  and (:active is null or u.active = :active)
			  and (:installationWide = true
			       or (u.departmentId in :departmentIds)
			       or (u.branchId in :branchIds)
			       or u.id = :callerId)
			""")
	Page<User> searchScoped(@Param("search") String search, @Param("departmentId") UUID departmentId,
			@Param("active") Boolean active, @Param("installationWide") boolean installationWide,
			@Param("departmentIds") List<UUID> departmentIds, @Param("branchIds") List<UUID> branchIds,
			@Param("callerId") UUID callerId, Pageable pageable);

}
