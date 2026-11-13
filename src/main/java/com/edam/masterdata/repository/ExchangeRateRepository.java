package com.edam.masterdata.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.masterdata.model.ExchangeRate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Effective-dated exchange rates (Module D). */
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

	/**
	 * The rate that applies at an instant.
	 *
	 * <p>The window predicate is expressed as "covers :at", and the query refuses to guess: if two rows
	 * matched, the EXCLUDE constraint would already have rejected the second insert, so at most one row
	 * can come back. {@code order by effectiveFrom desc limit 1} is belt-and-braces for a database whose
	 * constraint was dropped by hand.
	 */
	@Query("""
			select r from ExchangeRate r
			where r.fromCurrencyId = :fromId
			  and r.toCurrencyId = :toId
			  and r.effectiveFrom <= :at
			  and (r.effectiveTo is null or r.effectiveTo > :at)
			order by r.effectiveFrom desc
			""")
	List<ExchangeRate> findApplicable(@Param("fromId") UUID fromId, @Param("toId") UUID toId,
			@Param("at") Instant at);

	@Query("""
			select r from ExchangeRate r
			where (:fromId is null or r.fromCurrencyId = :fromId)
			  and (:toId is null or r.toCurrencyId = :toId)
			order by r.effectiveFrom desc
			""")
	List<ExchangeRate> findByPair(@Param("fromId") UUID fromId, @Param("toId") UUID toId);

	@Query("""
			select count(r) > 0 from ExchangeRate r
			where r.fromCurrencyId = :fromId
			  and r.toCurrencyId = :toId
			  and r.effectiveFrom < :to
			  and (r.effectiveTo is null or r.effectiveTo > :from)
			""")
	boolean existsOverlapping(@Param("fromId") UUID fromId, @Param("toId") UUID toId, @Param("from") Instant from,
			@Param("to") Instant to);

	Optional<ExchangeRate> findFirstByFromCurrencyIdAndToCurrencyIdOrderByEffectiveFromDesc(UUID fromId, UUID toId);

}
