package com.edam.institution;

import java.util.LinkedHashMap;
import java.util.Map;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Institution settings administration (BRD INS-01..03).
 *
 * <h2>What is editable and what is not</h2>
 * Branding, naming, locale formats and the CAPEX restriction switch are editable. The institution
 * <em>code</em> is not: it is the tenant key referenced by audit records, exports and integration
 * configuration, so renaming it would orphan history. There is also no create or delete endpoint
 * here — onboarding a new institution is a platform operation requiring provisioning (database
 * rows, roles, currency), which is a separate capability (BRD §21) and not a settings update.
 *
 * <h2>Validation that belongs to this service</h2>
 * The base currency must already exist in the institution's currency list. Otherwise an institution
 * could declare a base currency it cannot convert from, and every report would be nominally correct
 * and arithmetically wrong.
 */
@Service
public class InstitutionSettingsService {

	private final InstitutionRepository institutions;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	private final com.edam.configplane.currency.CurrencyRepository currencies;

	public InstitutionSettingsService(InstitutionRepository institutions, InstitutionContext institutionContext,
			AuditRecorder audit, com.edam.configplane.currency.CurrencyRepository currencies) {
		this.institutions = institutions;
		this.institutionContext = institutionContext;
		this.audit = audit;
		this.currencies = currencies;
	}

	@Transactional(readOnly = true)
	public Institution current() {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.institutions.findById(institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Institution", institutionId));
	}

	@Transactional
	public Institution update(String name, String shortName, String displayName, String baseCurrency,
			String primaryColor, String secondaryColor, String accentColor, String languageTag, String timeZone,
			String supportEmail, String dateFormat, String numberFormat) {
		Institution institution = current();
		if (baseCurrency != null && !baseCurrency.equalsIgnoreCase(institution.getBaseCurrency())
				&& !this.currencies.existsByInstitutionIdAndCode(institution.getId(),
						baseCurrency.toUpperCase(java.util.Locale.ROOT))) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The base currency must exist in the institution's currency list before it can be selected.");
		}
		Map<String, Object> before = snapshot(institution);
		institution.setName(name.trim());
		institution.setShortName(shortName.trim());
		institution.setDisplayName(displayName);
		if (baseCurrency != null) {
			institution.setBaseCurrency(baseCurrency.toUpperCase(java.util.Locale.ROOT));
		}
		institution.setPrimaryColor(primaryColor);
		institution.setSecondaryColor(secondaryColor);
		institution.setAccentColor(accentColor);
		institution.setLanguageTag(languageTag == null ? institution.getLanguageTag() : languageTag);
		institution.setTimeZone(timeZone == null ? institution.getTimeZone() : timeZone);
		institution.setSupportEmail(supportEmail);
		institution.setDateFormat(dateFormat == null ? institution.getDateFormat() : dateFormat);
		institution.setNumberFormat(numberFormat == null ? institution.getNumberFormat() : numberFormat);
		this.audit.recordChange(AuditAction.UPDATE, "Institution", institution.getId(), "Institution settings updated",
				before, snapshot(institution));
		return institution;
	}

	private static Map<String, Object> snapshot(Institution institution) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("name", institution.getName());
		snapshot.put("shortName", institution.getShortName());
		snapshot.put("baseCurrency", institution.getBaseCurrency());
		snapshot.put("primaryColor", institution.getPrimaryColor());
		snapshot.put("secondaryColor", institution.getSecondaryColor());
		snapshot.put("accentColor", institution.getAccentColor());
		snapshot.put("languageTag", institution.getLanguageTag());
		snapshot.put("timeZone", institution.getTimeZone());
		snapshot.put("dateFormat", institution.getDateFormat());
		snapshot.put("numberFormat", institution.getNumberFormat());
		return snapshot;
	}

}
