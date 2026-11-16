package com.edam.masterdata.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * An effective-dated conversion rate between two currencies (Module D).
 *
 * <h2>Effective dating, not a current-value column</h2>
 * A rate is a fact about a period. Overwriting yesterday's rate would silently change the local-currency
 * value of every request approved yesterday — an audit finding, and a reconciliation break with the
 * general ledger. Each rate therefore has {@code effectiveFrom} and, once superseded,
 * {@code effectiveTo}. A request that pins a rate keeps that value forever in
 * {@code expense_request_currency_snapshots}; this table is only the source consulted at the moment of
 * conversion.
 *
 * <h2>Overlap is impossible in the database</h2>
 * Migration V7 installs a {@code btree_gist} EXCLUDE constraint on the {@code tstzrange} of each
 * currency pair, so two rates for the same pair cannot cover the same instant. Without it, "which rate
 * applied" would depend on row order — the kind of ambiguity that only surfaces during an audit.
 *
 * <h2>No inverse rows are generated</h2>
 * EUR→USD does not imply USD→EUR. A bank quoting a both-ways spread would not accept the reciprocal, and
 * deriving one would invent a rate the treasury never published.
 *
 * <h2>Why the entity has no {@code @Version}</h2>
 * Rates are append-only by policy: correcting one means closing its period and inserting a new row. A
 * mutable rate would let a historical conversion be retroactively changed with no evidence.
 */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "from_currency_id", nullable = false, updatable = false)
	private UUID fromCurrencyId;

	@Column(name = "to_currency_id", nullable = false, updatable = false)
	private UUID toCurrencyId;

	@Column(name = "exchange_rate", nullable = false, updatable = false, precision = 19, scale = 8)
	private BigDecimal rate;

	@Column(name = "effective_from", nullable = false, updatable = false)
	private Instant effectiveFrom;

	@Column(name = "effective_to")
	private Instant effectiveTo;

	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ExchangeRate() {
		// JPA
	}

	public ExchangeRate(UUID fromCurrencyId, UUID toCurrencyId, BigDecimal rate, Instant effectiveFrom,
			Instant effectiveTo, UUID createdBy) {
		if (rate == null || rate.signum() <= 0) {
			throw new IllegalArgumentException("An exchange rate must be positive");
		}
		if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
			throw new IllegalArgumentException("The end of a rate period must follow its start");
		}
		this.fromCurrencyId = fromCurrencyId;
		this.toCurrencyId = toCurrencyId;
		this.rate = rate;
		this.effectiveFrom = effectiveFrom;
		this.effectiveTo = effectiveTo;
		this.createdBy = createdBy;
		this.createdAt = Instant.now();
	}

	/** True when this rate is the one that applies at the given instant. */
	public boolean covers(Instant instant) {
		return !instant.isBefore(this.effectiveFrom) && (this.effectiveTo == null || instant.isBefore(this.effectiveTo));
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getFromCurrencyId() {
		return this.fromCurrencyId;
	}

	public UUID getToCurrencyId() {
		return this.toCurrencyId;
	}

	public BigDecimal getRate() {
		return this.rate;
	}

	public Instant getEffectiveFrom() {
		return this.effectiveFrom;
	}

	public Instant getEffectiveTo() {
		return this.effectiveTo;
	}

	/**
	 * Closes an open-ended rate period. Only {@code effectiveTo} is mutable, and only to a value after
	 * {@code effectiveFrom}; all other columns remain immutable so history is not rewritten.
	 */
	public void endPeriod(Instant end) {
		if (end == null) {
			throw new IllegalArgumentException("The end of a rate period is required when closing it.");
		}
		if (!end.isAfter(this.effectiveFrom)) {
			throw new IllegalArgumentException("The end of a rate period must follow its start.");
		}
		this.effectiveTo = end;
	}

	public UUID getCreatedBy() {
		return this.createdBy;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
