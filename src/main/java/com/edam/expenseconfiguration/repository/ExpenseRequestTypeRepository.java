package com.edam.expenseconfiguration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.expenseconfiguration.model.ExpenseRequestType;

import org.springframework.data.jpa.repository.JpaRepository;

/** Administrative request types (Module E). */
public interface ExpenseRequestTypeRepository extends JpaRepository<ExpenseRequestType, UUID> {

	Optional<ExpenseRequestType> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	List<ExpenseRequestType> findByActiveTrueOrderByNameAsc();

}
