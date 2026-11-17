package com.edam.businessrules.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.businessrules.model.BusinessRuleSet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessRuleSetRepository extends JpaRepository<BusinessRuleSet, UUID> {

	Optional<BusinessRuleSet> findByCode(String code);

	@Query("select distinct s from BusinessRuleSet s left join fetch s.versions v "
			+ "order by s.module, s.code")
	List<BusinessRuleSet> findAllWithVersions();

	@Query("select s from BusinessRuleSet s left join fetch s.versions v "
			+ "where s.id = ?1")
	Optional<BusinessRuleSet> findByIdWithVersions(UUID id);

	@Query("select s from BusinessRuleSet s left join fetch s.versions v "
			+ "where s.code = ?1")
	Optional<BusinessRuleSet> findByCodeWithVersions(String code);

	boolean existsByCode(String code);
}
