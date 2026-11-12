package com.edam.configplane.limit;

import java.util.List;
import java.util.Optional;

import com.edam.configplane.ExpenseNature;

import org.springframework.data.jpa.repository.JpaRepository;

/** Exceeding-limit persistence; one row per institution and nature. */
public interface ExceedingLimitRepository extends JpaRepository<ExceedingLimit, Long> {

	List<ExceedingLimit> findByInstitutionIdOrderByExpenseNatureAsc(Long institutionId);

	Optional<ExceedingLimit> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<ExceedingLimit> findByInstitutionIdAndExpenseNature(Long institutionId, ExpenseNature nature);

}
