package com.edam.organization.service;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.organization.dto.OrganizationDtos.OrganizationProfileResponse;
import com.edam.organization.dto.OrganizationDtos.UpdateOrganizationProfileRequest;
import com.edam.organization.model.OrganizationProfile;
import com.edam.organization.repository.OrganizationProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the singleton organization profile (Module A).
 *
 * <h2>What this service refuses to do</h2>
 * <ul>
 *   <li><strong>Create a profile implicitly.</strong> If no profile row exists, reading fails with a
 *       precondition error rather than returning an invented "EDAM" organization. A blank install means
 *       setup is incomplete, and pretending otherwise would let requests be raised against a bank that
 *       was never named.</li>
 *   <li><strong>Accept an unresolvable timezone.</strong> A syntactically plausible but unknown zone would
 *       only fail much later, when a timestamp is rendered. Validation here keeps the failure next to the
 *       mistake.</li>
 *   <li><strong>Accept a currency that does not exist.</strong> The default currency is a foreign key, and
 *       checking first turns a constraint violation into a readable message.</li>
 * </ul>
 */
@Service
public class OrganizationProfileService {

	private final OrganizationProfileRepository profiles;

	private final CurrencyRepository currencies;

	private final AuditRecorder audit;

	public OrganizationProfileService(OrganizationProfileRepository profiles, CurrencyRepository currencies,
			AuditRecorder audit) {
		this.profiles = profiles;
		this.currencies = currencies;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public OrganizationProfileResponse current() {
		return toResponse(requireProfile());
	}

	@Transactional
	public OrganizationProfileResponse update(UpdateOrganizationProfileRequest request) {
		OrganizationProfile profile = requireProfile();
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("organizationName", profile.getOrganizationName());
		before.put("timezone", profile.getTimezone());
		before.put("dateFormat", profile.getDateFormat());
		before.put("defaultCurrencyId", profile.getDefaultCurrencyId());

		if (request.organizationName() != null) {
			String name = request.organizationName().trim();
			if (name.isEmpty()) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The organization name cannot be blank.");
			}
			profile.setOrganizationName(name);
		}
		if (request.legalName() != null) {
			profile.setLegalName(request.legalName().trim());
		}
		if (request.logoAssetId() != null) {
			profile.setLogoAssetId(request.logoAssetId());
		}
		if (request.faviconAssetId() != null) {
			profile.setFaviconAssetId(request.faviconAssetId());
		}
		if (request.timezone() != null) {
			profile.setTimezone(requireValidZone(request.timezone()));
		}
		if (request.dateFormat() != null) {
			profile.setDateFormat(request.dateFormat().trim());
		}
		if (request.defaultCurrencyId() != null) {
			profile.setDefaultCurrencyId(requireCurrency(request.defaultCurrencyId()));
		}

		OrganizationProfile saved = this.profiles.save(profile);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("organizationName", saved.getOrganizationName());
		after.put("timezone", saved.getTimezone());
		after.put("dateFormat", saved.getDateFormat());
		after.put("defaultCurrencyId", saved.getDefaultCurrencyId());
		this.audit.recordChange(AuditAction.UPDATE, "OrganizationProfile", saved.getId(), before, after);
		return toResponse(saved);
	}

	private OrganizationProfile requireProfile() {
		return this.profiles.findFirstByActiveTrue()
			.orElseThrow(() -> new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"The organization profile has not been configured. Complete installation setup first."));
	}

	private static String requireValidZone(String raw) {
		String zone = raw.trim();
		try {
			ZoneId.of(zone);
			return zone;
		}
		catch (RuntimeException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"'" + zone + "' is not a recognised time zone identifier (for example Asia/Karachi).");
		}
	}

	private UUID requireCurrency(UUID currencyId) {
		Currency currency = this.currencies.findById(currencyId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "That currency does not exist."));
		if (!currency.isActive()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"That currency is inactive and cannot be the default.");
		}
		return currency.getId();
	}

	private OrganizationProfileResponse toResponse(OrganizationProfile profile) {
		String currencyCode = (profile.getDefaultCurrencyId() == null) ? null
				: this.currencies.findById(profile.getDefaultCurrencyId()).map(Currency::getCode).orElse(null);
		return new OrganizationProfileResponse(profile.getId(), profile.getOrganizationName(),
				profile.getLegalName(), profile.getLogoAssetId(), profile.getFaviconAssetId(), profile.getTimezone(),
				profile.getDateFormat(), profile.getDefaultCurrencyId(), currencyCode, profile.getUpdatedAt());
	}

}
