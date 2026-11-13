package com.edam.expenseconfiguration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.expenseconfiguration.model.ExpenseNature;

import org.springframework.data.jpa.repository.JpaRepository;

/** Expense natures (Module E). */
public interface ExpenseNatureRepository extends JpaRepository<ExpenseNature, UUID> {

	Optional<ExpenseNature> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	List<ExpenseNature> findByActiveTrueOrderByNameAsc();

	List<ExpenseNature> findAllByOrderByNameAsc();

}
