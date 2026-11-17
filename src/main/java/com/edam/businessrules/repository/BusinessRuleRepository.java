package com.edam.businessrules.repository;

import java.util.UUID;

import com.edam.businessrules.model.BusinessRule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessRuleRepository extends JpaRepository<BusinessRule, UUID> {
	@Query("select r.ruleVersion.id from BusinessRule r where r.id = ?1")
	UUID findVersionIdByRuleId(UUID ruleId);
}
