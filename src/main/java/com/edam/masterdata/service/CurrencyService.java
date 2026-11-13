package com.edam.masterdata.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.masterdata.dto.MasterDataDtos.CurrencyResponse;
import com.edam.masterdata.dto.MasterDataDtos.ExchangeRateResponse;
import com.edam.masterdata.dto.MasterDataDtos.SaveCurrencyRequest;
import com.edam.masterdata.dto.MasterDataDtos.SaveExchangeRateRequest;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.model.ExchangeRate;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.masterdata.repository.ExchangeRateRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Currencies and effective-dated exchange rates (Module D).
 *
 * <h2>Why the clock is injected</h2>
 * "Which rate applies now" is a time question, and a test that cannot control the clock can only be run
 * against whatever the wall clock happens to be. {@link Clock} makes the boundary cases — a rate that ends
 * exactly now, a rate that starts in one millisecond — testable without sleeping.
 *
 * <h2>Overlap is checked twice, on purpose</h2>
 * {@link #createRate} asks the repository whether an overlapping period exists so the caller receives a
 * readable message, and the database EXCLUDE constraint refuses it regardless. The first is for the
 * operator; the second is for correctness when two administrators save at once.
 *
 * <h2>Rates are never edited</h2>
 * There is no update path. A wrong rate is corrected by closing its period and inserting a new one, which
 * preserves what was in force at the moment historical requests were converted. Allowing an edit would
 * silently rewrite the basis of every past conversion.
 */
@Service
public class CurrencyService {

	private final CurrencyRepository currencies;

	private final ExchangeRateRepository rates;

	private final AuditRecorder audit;

	private final Clock clock;

	public CurrencyService(CurrencyRepository currencies, ExchangeRateRepository rates, AuditRecorder audit,
			Clock clock) {
		this.currencies = currencies;
		this.rates = rates;
		this.audit = audit;
		this.clock = clock;
	}

	// ------------------------------------------------------------------
	// Currencies
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<CurrencyResponse> listCurrencies(boolean includeInactive) {
		List<Currency> rows = includeInactive ? this.currencies.findAllByOrderByCodeAsc()
				: this.currencies.findByActiveTrueOrderByCodeAsc();
		return rows.stream().map(CurrencyService::toResponse).toList();
	}

	@Transactional
	public CurrencyResponse createCurrency(SaveCurrencyRequest request) {
		String code = normaliseCurrencyCode(request.code());
		if (this.currencies.existsByCode(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Currency " + code + " already exists.");
		}
		Currency currency = new Currency();
		currency.setCode(code);
		currency.setName(request.name().trim());
		currency.setSymbol(request.symbol());
		currency.setDecimalPlaces(request.decimalPlaces());
		Currency saved = this.currencies.save(currency);
		this.audit.record(AuditAction.CREATE, "Currency", saved.getId());
		return toResponse(saved);
	}

	@Transactional
	public CurrencyResponse updateCurrency(UUID id, SaveCurrencyRequest request) {
		Currency currency = requireCurrency(id);
		// The ISO code is immutable once created: it is the join key historical rates and snapshots
		// reference, and changing it would relabel years of conversions.
		currency.setName(request.name().trim());
		currency.setSymbol(request.symbol());
		currency.setDecimalPlaces(request.decimalPlaces());
		Currency saved = this.currencies.save(currency);
		this.audit.record(AuditAction.UPDATE, "Currency", id);
		return toResponse(saved);
	}

	@Transactional
	public CurrencyResponse setCurrencyActive(UUID id, boolean active) {
		Currency currency = requireCurrency(id);
		currency.setActive(active);
		Currency saved = this.currencies.save(currency);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "Currency", id);
		return toResponse(saved);
	}

	// ------------------------------------------------------------------
	// Exchange rates
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ExchangeRateResponse> listRates(UUID fromCurrencyId, UUID toCurrencyId) {
		return this.rates.findByPair(fromCurrencyId, toCurrencyId).stream().map(this::toResponse).toList();
	}

	/**
	 * Records a new effective period.
	 *
	 * <p>The end of the period is optional: a rate with no end is the current one. It is refused when it
	 * overlaps an existing period for the same pair, because two applicable rates would make the
	 * conversion depend on row order.
	 */
	@Transactional
	public ExchangeRateResponse createRate(SaveExchangeRateRequest request) {
		Currency from = requireCurrency(request.fromCurrencyId());
		Currency to = requireCurrency(request.toCurrencyId());
		if (from.getId().equals(to.getId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A rate needs two different currencies; " + from.getCode() + " to itself is always 1.");
		}
		Instant start = (request.effectiveFrom() == null) ? Instant.now(this.clock) : request.effectiveFrom();
		Instant end = request.effectiveTo();
		if (end != null && !end.isAfter(start)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The end of the period must follow its start.");
		}
		Instant overlapProbeEnd = (end == null) ? Instant.parse("9999-12-31T00:00:00Z") : end;
		if (this.rates.existsOverlapping(from.getId(), to.getId(), start, overlapProbeEnd)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"An exchange rate for " + from.getCode() + " to " + to.getCode()
							+ " already covers that period. Close the existing period first.");
		}
		ExchangeRate saved = this.rates.save(new ExchangeRate(from.getId(), to.getId(), request.rate(), start, end,
				null));
		this.audit.record(AuditAction.CREATE, "ExchangeRate", saved.getId());
		return toResponse(saved);
	}

	/**
	 * The rate to use for a conversion, or a failure explaining the gap.
	 *
	 * <p>Fail-closed by design: a missing rate is never approximated with 1, with "the last one we saw", or
	 * with a rate for a different pair. Converting an amount at an invented rate is a financial error that
	 * no later control detects, because the result looks plausible.
	 */
	@Transactional(readOnly = true)
	public BigDecimal requireRate(UUID fromCurrencyId, UUID toCurrencyId, Instant at) {
		if (fromCurrencyId.equals(toCurrencyId)) {
			return BigDecimal.ONE;
		}
		List<ExchangeRate> applicable = this.rates.findApplicable(fromCurrencyId, toCurrencyId, at);
		if (applicable.isEmpty()) {
			String from = this.currencies.findById(fromCurrencyId).map(Currency::getCode).orElse("?");
			String to = this.currencies.findById(toCurrencyId).map(Currency::getCode).orElse("?");
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"No exchange rate from " + from + " to " + to + " is effective on that date.");
		}
		return applicable.get(0).getRate();
	}

	@Transactional(readOnly = true)
	public Optional<BigDecimal> findRate(UUID fromCurrencyId, UUID toCurrencyId, Instant at) {
		if (fromCurrencyId.equals(toCurrencyId)) {
			return Optional.of(BigDecimal.ONE);
		}
		return this.rates.findApplicable(fromCurrencyId, toCurrencyId, at).stream().findFirst().map(ExchangeRate::getRate);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public Currency requireCurrency(UUID id) {
		return this.currencies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Currency", id));
	}

	private ExchangeRateResponse toResponse(ExchangeRate rate) {
		String from = this.currencies.findById(rate.getFromCurrencyId()).map(Currency::getCode).orElse(null);
		String to = this.currencies.findById(rate.getToCurrencyId()).map(Currency::getCode).orElse(null);
		return new ExchangeRateResponse(rate.getId(), rate.getFromCurrencyId(), from, rate.getToCurrencyId(), to,
				rate.getRate(), rate.getEffectiveFrom(), rate.getEffectiveTo(), rate.getCreatedAt());
	}

	private static CurrencyResponse toResponse(Currency currency) {
		return new CurrencyResponse(currency.getId(), currency.getCode(), currency.getName(), currency.getSymbol(),
				currency.getDecimalPlaces(), currency.isActive());
	}

	static String normaliseCurrencyCode(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A currency code is required.");
		}
		String code = raw.trim().toUpperCase(java.util.Locale.ROOT);
		if (code.length() != 3) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A currency code must be exactly three letters (ISO 4217).");
		}
		return code;
	}

}
