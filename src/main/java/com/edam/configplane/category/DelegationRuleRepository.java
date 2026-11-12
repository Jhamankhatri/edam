package com.edam.configplane.category;

import java.util.List;
import java.util.Optional;

import com.edam.configplane.ExpenseNature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Delegation matrix persistence.
 *
 * <p>The routing lookup is a single tenant-scoped query: active rules for an exact
 * category/sub-category/nature, ordered by lower bound, so the router can pick the band that covers
 * the amount without loading the whole matrix.
 */
public interface DelegationRuleRepository extends JpaRepository<DelegationRule, Long> {

	List<DelegationRule> findByInstitutionIdOrderByAmountFromAsc(Long institutionId);

	List<DelegationRule> findByInstitutionIdAndActiveTrueOrderByAmountFromAsc(Long institutionId);

	Optional<DelegationRule> findByIdAndInstitutionId(Long id, Long institutionId);

	@Query("""
			select distinct r from DelegationRule r
			left join fetch r.approverTypes
			left join fetch r.recommendingAuthorities
			where r.institutionId = :institutionId
			  and r.active = true
			  and r.expenseNature = :nature
			  and r.category.id = :categoryId
			  and (r.subcategory.id = :subcategoryId or r.subcategory is null)
			order by r.amountFrom asc
			""")
	List<DelegationRule> findCandidateRules(@Param("institutionId") Long institutionId,
			@Param("nature") ExpenseNature nature, @Param("categoryId") Long categoryId,
			@Param("subcategoryId") Long subcategoryId);

	/** Active rules that overlap the given band for the same classification, used by validation. */
	@Query("""
			select r from DelegationRule r
			where r.institutionId = :institutionId
			  and r.active = true
			  and r.expenseNature = :nature
			  and r.category.id = :categoryId
			  and ((:subcategoryId is null and r.subcategory is null) or r.subcategory.id = :subcategoryId)
			  and (:excludeId is null or r.id <> :excludeId)
			""")
	List<DelegationRule> findSameClassification(@Param("institutionId") Long institutionId,
			@Param("nature") ExpenseNature nature, @Param("categoryId") Long categoryId,
			@Param("subcategoryId") Long subcategoryId, @Param("excludeId") Long excludeId);

	boolean existsByInstitutionIdAndRecommendingAuthoritiesId(Long institutionId, Long authorityId);

}
