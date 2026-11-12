package com.edam.configplane.limit;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Department-head limit persistence.
 *
 * <p>There is intentionally no {@code save}/{@code insert} path used by the service beyond the
 * single-record rule: {@code findByInstitutionId} is the entry point, and a unique constraint on
 * {@code institution_id} makes a second row impossible.
 */
public interface DepartmentHeadLimitRepository extends JpaRepository<DepartmentHeadLimit, Long> {

	Optional<DepartmentHeadLimit> findByInstitutionId(Long institutionId);

}
