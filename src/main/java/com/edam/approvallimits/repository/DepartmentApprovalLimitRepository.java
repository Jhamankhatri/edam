package com.edam.approvallimits.repository;

import java.util.List;
import java.util.UUID;

import com.edam.approvallimits.model.DepartmentApprovalLimit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Approval limits (Module D).
 *
 * <p>Resolution is a read-only, hot-path query: it runs on every submission to decide whether anyone is
 * permitted to approve the amount at all. It is therefore scoped by department and currency in SQL rather
 * than filtered in Java, so an installation with thousands of historical limit rows still answers in one
 * indexed read.
 */
public interface DepartmentApprovalLimitRepository extends JpaRepository<DepartmentApprovalLimit, UUID> {

	@Query("""
			select l from DepartmentApprovalLimit l
			where l.departmentId = :departmentId
			  and l.currencyId = :currencyId
			  and l.active = true
			order by l.effectiveFrom desc
			""")
	List<DepartmentApprovalLimit> findActiveForDepartmentAndCurrency(@Param("departmentId") UUID departmentId,
			@Param("currencyId") UUID currencyId);

	List<DepartmentApprovalLimit> findByDepartmentIdOrderByEffectiveFromDesc(UUID departmentId);

	List<DepartmentApprovalLimit> findAllByOrderByEffectiveFromDesc();

}
