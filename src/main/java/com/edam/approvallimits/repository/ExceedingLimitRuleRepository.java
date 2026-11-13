package com.edam.approvallimits.repository;

import java.util.List;
import java.util.UUID;

import com.edam.approvallimits.model.ExceedingLimitRule;

import org.springframework.data.jpa.repository.JpaRepository;

/** Exceeding-limit rules (Module D). */
public interface ExceedingLimitRuleRepository extends JpaRepository<ExceedingLimitRule, UUID> {

	List<ExceedingLimitRule> findByActiveTrueOrderByThresholdAmountAsc();

	List<ExceedingLimitRule> findAllByOrderByThresholdAmountAsc();

	List<ExceedingLimitRule> findByNatureIdAndActiveTrue(UUID natureId);

}
