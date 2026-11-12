package com.edam.configplane.currency;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Currency persistence; all finders are tenant-scoped. */
public interface CurrencyRepository extends JpaRepository<Currency, Long> {

	List<Currency> findByInstitutionIdOrderByCodeAsc(Long institutionId);

	List<Currency> findByInstitutionIdAndActiveTrueOrderByCodeAsc(Long institutionId);

	Optional<Currency> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<Currency> findByInstitutionIdAndCode(Long institutionId, String code);

	boolean existsByInstitutionIdAndCode(Long institutionId, String code);

	boolean existsByInstitutionIdAndCodeAndIdNot(Long institutionId, String code, Long id);

}
