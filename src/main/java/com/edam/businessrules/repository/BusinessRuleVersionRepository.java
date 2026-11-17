package com.edam.businessrules.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.businessrules.model.BusinessRuleSet;
import com.edam.businessrules.model.BusinessRuleVersion;
import com.edam.businessrules.model.RuleVersionStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface BusinessRuleVersionRepository extends JpaRepository<BusinessRuleVersion, UUID> {

	@Query("select v from BusinessRuleVersion v left join fetch v.rules join fetch v.ruleSet "
			+ "where v.id = ?1")
	Optional<BusinessRuleVersion> findByIdWithRules(UUID id);

	@Query("select v from BusinessRuleVersion v left join fetch v.rules join fetch v.ruleSet "
			+ "where v.ruleSet.id = ?1 and v.status = ?2")
	Optional<BusinessRuleVersion> findByRuleSetAndStatusWithRules(UUID ruleSetId, RuleVersionStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from BusinessRuleVersion v where v.id = ?1")
	Optional<BusinessRuleVersion> lockById(UUID id);

	@Query("select count(v) > 0 from BusinessRuleVersion v where v.ruleSet = ?1 and v.status = ?2 and v.id <> ?3")
	boolean existsOtherInStatus(BusinessRuleSet ruleSet, RuleVersionStatus status, UUID excludingId);

	List<BusinessRuleVersion> findByRuleSetOrderByVersionNumberDesc(BusinessRuleSet ruleSet);
}
