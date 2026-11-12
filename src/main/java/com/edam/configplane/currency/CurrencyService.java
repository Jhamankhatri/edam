package com.edam.configplane.currency;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;
import com.edam.institution.Institution;
import com.edam.institution.InstitutionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Currency administration (BRD FM-11).
 *
 * <h2>Invariants this service owns</h2>
 * <ul>
 *   <li>A currency code is unique per institution and <strong>immutable</strong> after creation.
 *       Recorded amounts reference the code, so letting it change would silently relabel history.</li>
 *   <li>The rate has at most four decimals (the BRD's "up to 4 decimal places"). Validated here for
 *       a usable message and by a database CHECK so an import cannot bypass it.</li>
 *   <li>The institution's base currency always has a rate of exactly 1. Its rate is not editable —
 *       a base currency that is not 1 would make every converted amount wrong.</li>
 *   <li>A currency with recorded requests cannot be deleted; it can be deactivated.</li>
 * </ul>
 *
 * <p>No conversion arithmetic lives here on purpose: the BRD does not define rounding or the
 * conversion point (OD-31), so implementing a formula would be inventing a banking policy.
 */
@Service
public class CurrencyService {

	private final CurrencyRepository currencies;

	private final InstitutionContext institutionContext;

	private final InstitutionRepository institutions;

	private final AuditRecorder audit;

	public CurrencyService(CurrencyRepository currencies, InstitutionContext institutionContext,
			InstitutionRepository institutions, AuditRecorder audit) {
		this.currencies = currencies;
		this.institutionContext = institutionContext;
		this.institutions = institutions;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<Currency> list(boolean activeOnly) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return activeOnly ? this.currencies.findByInstitutionIdAndActiveTrueOrderByCodeAsc(institutionId)
				: this.currencies.findByInstitutionIdOrderByCodeAsc(institutionId);
	}

	@Transactional(readOnly = true)
	public Currency get(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.currencies.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Currency", id));
	}

	@Transactional
	public Currency create(String code, String name, BigDecimal rateToBase) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		String normalised = code == null ? null : code.trim().toUpperCase(java.util.Locale.ROOT);
		if (normalised == null || !normalised.matches("[A-Z]{3}")) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Currency code must be a three-letter ISO-4217 code.");
		}
		if (this.currencies.existsByInstitutionIdAndCode(institutionId, normalised)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"A currency with that code already exists for this institution.");
		}
		requireValidRate(rateToBase);
		Currency currency = new Currency(normalised, name.trim(), rateToBase);
		currency.assignInstitution(institutionId);
		Currency saved = this.currencies.save(currency);
		this.audit.recordChange(AuditAction.CREATE, "Currency", saved.getId(), "Currency created", null,
				snapshot(saved));
		return saved;
	}

	@Transactional
	public Currency update(Long id, String name, BigDecimal rateToBase) {
		Currency currency = get(id);
		requireValidRate(rateToBase);
		if (isBaseCurrency(currency)) {
			if (rateToBase.compareTo(BigDecimal.ONE) != 0) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"The institution's base currency must keep an exchange rate of exactly 1.");
			}
		}
		Map<String, Object> before = snapshot(currency);
		currency.update(name.trim(), rateToBase);
		this.audit.recordChange(AuditAction.UPDATE, "Currency", id, "Currency updated", before, snapshot(currency));
		return currency;
	}

	@Transactional
	public Currency setActive(Long id, boolean active) {
		Currency currency = get(id);
		if (isBaseCurrency(currency) && !active) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The base currency cannot be deactivated.");
		}
		Map<String, Object> before = snapshot(currency);
		if (active) {
			currency.activate();
		}
		else {
			currency.deactivate();
		}
		this.audit.recordChange(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "Currency", id,
				active ? "Currency activated" : "Currency deactivated", before, snapshot(currency));
		return currency;
	}

	/**
	 * The base currency is identified by the institution's own {@code base_currency} code rather
	 * than a flag on the row, so there is exactly one source of truth and no flag to get out of
	 * step with the currency list.
	 */
	private boolean isBaseCurrency(Currency currency) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.institutions.findById(institutionId)
			.map(Institution::getBaseCurrency)
			.filter(base -> base.equalsIgnoreCase(currency.getCode()))
			.isPresent();
	}

	private static void requireValidRate(BigDecimal rateToBase) {
		if (rateToBase == null || rateToBase.signum() <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Exchange rate must be greater than zero.");
		}
		if (rateToBase.stripTrailingZeros().scale() > 4) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Exchange rate may have at most four decimal places.");
		}
	}

	private static Map<String, Object> snapshot(Currency currency) {
		Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
		snapshot.put("code", currency.getCode());
		snapshot.put("name", currency.getName());
		snapshot.put("rateToBase", currency.getRateToBase().toPlainString());
		snapshot.put("active", currency.isActive());
		return snapshot;
	}

}
