package com.edam.configplane.category;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Category persistence. Every finder takes {@code institutionId} as its first parameter: there is
 * no tenant-agnostic finder, so a cross-tenant read cannot be written by accident.
 */
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

	List<ExpenseCategory> findByInstitutionIdOrderByDisplayOrderAscNameAsc(Long institutionId);

	List<ExpenseCategory> findByInstitutionIdAndActiveTrueOrderByDisplayOrderAscNameAsc(Long institutionId);

	Optional<ExpenseCategory> findByIdAndInstitutionId(Long id, Long institutionId);

	boolean existsByInstitutionIdAndNameIgnoreCaseAndActiveTrue(Long institutionId, String name);

	boolean existsByInstitutionIdAndNameIgnoreCaseAndActiveTrueAndIdNot(Long institutionId, String name, Long id);

	@Query("select c from ExpenseCategory c where c.institutionId = :institutionId and c.active = true "
			+ "and lower(c.name) = lower(:name)")
	Optional<ExpenseCategory> findActiveByName(@Param("institutionId") Long institutionId, @Param("name") String name);

}
