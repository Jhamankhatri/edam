package com.edam.expenseconfiguration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.expenseconfiguration.model.ExpenseSubcategory;

import org.springframework.data.jpa.repository.JpaRepository;

/** Expense subcategories (Module E). */
public interface ExpenseSubcategoryRepository extends JpaRepository<ExpenseSubcategory, UUID> {

	List<ExpenseSubcategory> findByCategoryIdAndActiveTrueOrderByNameAsc(UUID categoryId);

	List<ExpenseSubcategory> findByCategoryIdOrderByNameAsc(UUID categoryId);

	Optional<ExpenseSubcategory> findByCategoryIdAndCodeIgnoreCase(UUID categoryId, String code);

	boolean existsByCategoryIdAndCodeIgnoreCase(UUID categoryId, String code);

	List<ExpenseSubcategory> findByActiveTrueOrderByNameAsc();

}
