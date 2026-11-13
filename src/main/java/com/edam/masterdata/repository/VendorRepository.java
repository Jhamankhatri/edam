package com.edam.masterdata.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.masterdata.model.Vendor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Vendors (Module D). */
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

	Optional<Vendor> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	List<Vendor> findByActiveTrueOrderByNameAsc();

	@Query("""
			select v from Vendor v
			where :search = ''
			   or lower(v.name) like lower(concat('%', :search, '%'))
			   or lower(v.code) like lower(concat('%', :search, '%'))
			order by v.name
			""")
	Page<Vendor> search(@Param("search") String search, Pageable pageable);

}
