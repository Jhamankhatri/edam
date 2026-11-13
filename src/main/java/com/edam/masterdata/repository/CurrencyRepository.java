package com.edam.masterdata.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.masterdata.model.Currency;

import org.springframework.data.jpa.repository.JpaRepository;

/** Currency master data (Module D). */
public interface CurrencyRepository extends JpaRepository<Currency, UUID> {

	Optional<Currency> findByCode(String code);

	boolean existsByCode(String code);

	List<Currency> findByActiveTrueOrderByCodeAsc();

	List<Currency> findAllByOrderByCodeAsc();

}
