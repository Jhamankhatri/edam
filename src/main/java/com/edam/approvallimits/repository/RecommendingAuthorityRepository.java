package com.edam.approvallimits.repository;

import java.util.List;
import java.util.UUID;

import com.edam.approvallimits.model.RecommendingAuthority;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Recommending authorities (Module D).
 *
 * <p>{@link #findActiveForDepartment} is the query the workflow resolver uses. It is scoped in SQL
 * because it runs each time a workflow step of type {@code RECOMMENDING_AUTHORITY} opens.
 */
public interface RecommendingAuthorityRepository extends JpaRepository<RecommendingAuthority, UUID> {

	@Query("""
			select a from RecommendingAuthority a
			where a.departmentId = :departmentId
			  and a.active = true
			order by a.effectiveFrom desc
			""")
	List<RecommendingAuthority> findActiveForDepartment(@Param("departmentId") UUID departmentId);

	List<RecommendingAuthority> findAllByOrderByEffectiveFromDesc();

	List<RecommendingAuthority> findByUserId(UUID userId);

}
