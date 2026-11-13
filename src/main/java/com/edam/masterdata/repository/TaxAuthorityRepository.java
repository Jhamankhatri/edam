package com.edam.masterdata.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.masterdata.model.TaxAuthority;

import org.springframework.data.jpa.repository.JpaRepository;

/** Tax authorities (Module D). */
public interface TaxAuthorityRepository extends JpaRepository<TaxAuthority, UUID> {

	Optional<TaxAuthority> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	List<TaxAuthority> findByActiveTrueOrderByNameAsc();

}
