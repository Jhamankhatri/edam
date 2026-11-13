package com.edam.delegation.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.delegation.model.DelegationRule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The delegation matrix (Module E).
 *
 * <h2>The resolution query is the routing engine's entry point</h2>
 * {@link #findCandidates} returns every active rule that could apply to a classification at an instant,
 * ordered so the most specific match wins: a subcategory-specific rule beats a category-wide one. The
 * amount band and currency are applied in SQL as well, because a bank with thousands of rules must not
 * have to load them all into memory to route one request.
 *
 * <p>The query deliberately does not decide the winner. Ranking is business logic (and is tested
 * separately); the database's job is to narrow the set cheaply and deterministically.
 */
public interface DelegationRuleRepository extends JpaRepository<DelegationRule, UUID> {

	@Query("""
			select distinct r from DelegationRule r
			left join fetch r.steps
			where r.active = true
			  and r.categoryId = :categoryId
			  and (r.subcategoryId is null or r.subcategoryId = :subcategoryId)
			  and r.natureId = :natureId
			  and (r.requestTypeId is null or r.requestTypeId = :requestTypeId)
			  and r.currencyId = :currencyId
			  and r.effectiveFrom <= :at
			  and (r.effectiveTo is null or r.effectiveTo > :at)
			  and r.minimumAmount <= :amount
			  and (r.maximumAmount is null or r.maximumAmount > :amount)
			order by r.subcategoryId nulls last, r.minimumAmount desc
			""")
	List<DelegationRule> findCandidates(@Param("categoryId") UUID categoryId,
			@Param("subcategoryId") UUID subcategoryId, @Param("natureId") UUID natureId,
			@Param("requestTypeId") UUID requestTypeId, @Param("currencyId") UUID currencyId,
			@Param("amount") java.math.BigDecimal amount, @Param("at") Instant at);

	@Query("""
			select r from DelegationRule r
			where (:categoryId is null or r.categoryId = :categoryId)
			  and (:includeInactive = true or r.active = true)
			order by r.categoryId, r.minimumAmount
			""")
	List<DelegationRule> list(@Param("categoryId") UUID categoryId, @Param("includeInactive") boolean includeInactive);

	boolean existsByCategoryIdAndActiveTrue(UUID categoryId);

	boolean existsByNatureIdAndActiveTrue(UUID natureId);

	long countByActiveTrue();

}
