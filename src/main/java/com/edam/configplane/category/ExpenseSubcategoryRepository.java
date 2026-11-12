package com.edam.configplane.category;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Sub-category persistence, always tenant-scoped. */
public interface ExpenseSubcategoryRepository extends JpaRepository<ExpenseSubcategory, Long> {

	List<ExpenseSubcategory> findByInstitutionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(Long institutionId,
			Long categoryId);

	List<ExpenseSubcategory> findByInstitutionIdAndActiveTrueOrderByDisplayOrderAscNameAsc(Long institutionId);

	Optional<ExpenseSubcategory> findByIdAndInstitutionId(Long id, Long institutionId);

	long countByInstitutionIdAndCategoryId(Long institutionId, Long categoryId);

	boolean existsByInstitutionIdAndCategoryIdAndNameIgnoreCaseAndActiveTrue(Long institutionId, Long categoryId,
			String name);

	boolean existsByInstitutionIdAndCategoryIdAndNameIgnoreCaseAndActiveTrueAndIdNot(Long institutionId,
			Long categoryId, String name, Long id);

	@Query("select s from ExpenseSubcategory s where s.institutionId = :institutionId and s.active = true "
			+ "and s.category.id = :categoryId and lower(s.name) = lower(:name)")
	Optional<ExpenseSubcategory> findActiveByNameInCategory(@Param("institutionId") Long institutionId,
			@Param("categoryId") Long categoryId, @Param("name") String name);

}
