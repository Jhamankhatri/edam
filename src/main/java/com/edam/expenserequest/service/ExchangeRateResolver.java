package com.edam.expenserequest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.masterdata.model.ExchangeRate;
import com.edam.masterdata.repository.ExchangeRateRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converts a request amount into the installation's base currency and remembers how (Module D, G).
 *
 * <h2>Why this exists instead of calling {@code CurrencyService.requireRate}</h2>
 * {@code CurrencyService} answers "what is the rate" and returns a {@link BigDecimal}. The request header
 * needs something else as well: {@code exchange_rate_id}, the identity of the row that was used. Without
 * it, a conversion cannot be reproduced — the rate table is effective-dated and a later period would give
 * a different answer for the same question. Reproducibility is the whole point of the column, so this
 * resolver returns the <em>rate row</em>, not just the number, and the caller stores both.
 *
 * <p>The lookup rules, including the fail-closed behaviour when no rate is effective, are the same as
 * {@code CurrencyService}: the repository query is reused rather than duplicated, so the two can never
 * disagree about which row applies.
 *
 * <h2>Rounding</h2>
 * The BRD says the PKR equivalent must be "calculated using the configured PKR rate" but does not state
 * the direction of the multiplication or the rounding mode (lines 1361–1363). That is recorded as
 * <strong>OD-P2-01, still open</strong>. The implementation below multiplies the amount by the stored rate
 * and rounds to four decimal places with {@link RoundingMode#HALF_UP}, which matches the column's scale
 * and the convention used by the line-item arithmetic. It is isolated in one method precisely so that an
 * approved decision can change it without touching a caller.
 */
@Service
public class ExchangeRateResolver {

	private static final Logger log = LoggerFactory.getLogger(ExchangeRateResolver.class);

	/** The base-currency amount together with the rate row that produced it. */
	public record Conversion(BigDecimal baseAmount, UUID exchangeRateId, BigDecimal rate) {
	}

	private final ExchangeRateRepository rates;

	public ExchangeRateResolver(ExchangeRateRepository rates) {
		this.rates = rates;
	}

	/**
	 * Converts {@code amount} from {@code currencyId} into {@code baseCurrencyId}.
	 *
	 * <p>When the two currencies are the same the identity rate applies and no rate row exists or is
	 * needed; {@code exchangeRateId} is then {@code null}, which the schema permits for exactly that case.
	 *
	 * @throws BusinessException when no rate is effective, naming both currencies — never a guess
	 */
	@Transactional(readOnly = true)
	public Conversion convert(BigDecimal amount, UUID currencyId, UUID baseCurrencyId, Instant at) {
		if (amount == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "An amount is required to convert.");
		}
		if (currencyId.equals(baseCurrencyId)) {
			return new Conversion(scale(amount), null, BigDecimal.ONE);
		}
		List<ExchangeRate> applicable = this.rates.findApplicable(currencyId, baseCurrencyId, at);
		if (applicable.isEmpty()) {
			// Deliberately not falling back to the inverse pair: if the bank configures only PKR→USD, using
			// it inverted would be an assumption about rate direction the BRD leaves open (OD-P2-01).
			log.warn("request.exchange_rate_missing from={} to={} at={}", currencyId, baseCurrencyId, at);
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"No exchange rate is effective on the request date for the selected currency, so the "
							+ "base-currency amount cannot be calculated. Configure a rate or choose the base currency.");
		}
		ExchangeRate rate = applicable.get(0);
		return new Conversion(scale(amount.multiply(rate.getRate())), rate.getId(), rate.getRate());
	}

	/** The rate row that would apply, without converting. Used to explain a future failure. */
	@Transactional(readOnly = true)
	public Optional<ExchangeRate> applicableRate(UUID currencyId, UUID baseCurrencyId, Instant at) {
		if (currencyId.equals(baseCurrencyId)) {
			return Optional.empty();
		}
		return this.rates.findApplicable(currencyId, baseCurrencyId, at).stream().findFirst();
	}

	/** Four decimal places, matching {@code NUMERIC(19,4)} and PostgreSQL's rounding. */
	static BigDecimal scale(BigDecimal value) {
		return value.setScale(4, RoundingMode.HALF_UP);
	}

}
