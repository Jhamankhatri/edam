package com.edam.expenseconfiguration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.expenseconfiguration.model.ExpenseCategory;

import org.springframework.data.jpa.repository.JpaRepository;

/** Expense categories (Module E). */
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

	Optional<ExpenseCategory> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	List<ExpenseCategory> findByActiveTrueOrderByNameAsc();

	List<ExpenseCategory> findAllByOrderByNameAsc();

}
