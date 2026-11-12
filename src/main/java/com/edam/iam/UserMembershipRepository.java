package com.edam.iam;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Membership persistence.
 *
 * <p>{@link #findActiveByUsername(String)} is the one finder that is deliberately <em>not</em>
 * tenant-scoped: it backs authentication, and at that moment the caller has not yet selected an
 * institution. It therefore returns every membership for the username, and the principal is built
 * from all of them — which is exactly the multi-institution model BRD IAM-04 requires.
 */
public interface UserMembershipRepository extends JpaRepository<UserMembership, Long> {

	List<UserMembership> findByInstitutionIdOrderByUsernameAsc(Long institutionId);

	Optional<UserMembership> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<UserMembership> findByInstitutionIdAndEmployeeId(Long institutionId, String employeeId);

	boolean existsByInstitutionIdAndEmployeeId(Long institutionId, String employeeId);

	@Query("select m from UserMembership m where m.username = :username and m.active = true")
	List<UserMembership> findActiveByUsername(@Param("username") String username);

	@Query("select distinct m.username from UserMembership m where m.active = true")
	List<String> findActiveUsernames();

}
