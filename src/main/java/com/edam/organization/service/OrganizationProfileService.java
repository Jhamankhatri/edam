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
import com.edam.organization.dto.OrganizationDtos.CreateOrganizationProfileRequest;
import com.edam.organization.dto.OrganizationDtos.OrganizationProfileResponse;
import com.edam.organization.dto.OrganizationDtos.UpdateOrganizationProfileRequest;
import com.edam.organization.model.OrganizationProfile;
import com.edam.organization.repository.OrganizationProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads, creates and updates the singleton organization profile (Phase 4B Module A).
 *
 * <p>Exactly one profile row exists per installation; partial unique index
 * {@code uk_organization_profile_active} enforces this at the database level. The service
 * validates timezone, currency and contact fields before writing, and records every
 * successful mutation as an {@link AuditAction#UPDATE} (or CREATE for the bootstrap call).
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
	public OrganizationProfileResponse create(CreateOrganizationProfileRequest request) {
		if (this.profiles.findFirstByActiveTrue().isPresent()) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"An organization profile already exists. Use PATCH /organization/profile to update it.");
		}
		OrganizationProfile p = new OrganizationProfile();
		p.setOrganizationName(requireNonBlank("organizationName", request.organizationName()));
		applyCreateFields(p, request);
		OrganizationProfile saved = this.profiles.save(p);
		this.audit.record(AuditAction.CREATE, "OrganizationProfile", saved.getId());
		return toResponse(saved);
	}

	@Transactional
	public OrganizationProfileResponse update(UpdateOrganizationProfileRequest request) {
		OrganizationProfile profile = requireProfile();
		Map<String, Object> before = snapshot(profile);
		applyUpdateFields(profile, request);
		OrganizationProfile saved = this.profiles.save(profile);
		Map<String, Object> after = snapshot(saved);
		this.audit.recordChange(AuditAction.UPDATE, "OrganizationProfile", saved.getId(), before, after);
		return toResponse(saved);
	}

	private OrganizationProfile requireProfile() {
		return this.profiles.findFirstByActiveTrue()
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
					"The organization profile has not been configured. An administrator must create it first."));
	}

	private void applyCreateFields(OrganizationProfile p, CreateOrganizationProfileRequest r) {
		p.setDisplayName(trimToNull(r.displayName()));
		p.setLegalName(trimToNull(r.legalName()));
		p.setOrganizationCode(trimToNull(r.organizationCode()));
		p.setContactEmail(validateEmail(trimToNull(r.contactEmail())));
		p.setContactPhone(trimToNull(r.contactPhone()));
		p.setWebsite(trimToNull(r.website()));
		p.setSupportEmail(validateEmail(trimToNull(r.supportEmail())));
		p.setAddressLine1(trimToNull(r.addressLine1()));
		p.setAddressLine2(trimToNull(r.addressLine2()));
		p.setAddressCity(trimToNull(r.addressCity()));
		p.setAddressState(trimToNull(r.addressState()));
		p.setAddressPostalCode(trimToNull(r.addressPostalCode()));
		p.setAddressCountry(trimToNull(r.addressCountry()));
		p.setLogoAssetId(r.logoAssetId());
		p.setFaviconAssetId(r.faviconAssetId());
		p.setTimezone(r.timezone() == null ? "Asia/Karachi" : requireValidZone(r.timezone()));
		p.setDateFormat(r.dateFormat() == null ? "dd MMM yyyy" : r.dateFormat().trim());
		if (r.defaultCurrencyId() != null) p.setDefaultCurrencyId(requireCurrency(r.defaultCurrencyId()));
	}

	private void applyUpdateFields(OrganizationProfile p, UpdateOrganizationProfileRequest r) {
		if (r.organizationName() != null) p.setOrganizationName(requireNonBlank("organizationName", r.organizationName()));
		if (r.displayName() != null) p.setDisplayName(trimToNull(r.displayName()));
		if (r.legalName() != null) p.setLegalName(trimToNull(r.legalName()));
		if (r.organizationCode() != null) p.setOrganizationCode(trimToNull(r.organizationCode()));
		if (r.contactEmail() != null) p.setContactEmail(validateEmail(trimToNull(r.contactEmail())));
		if (r.contactPhone() != null) p.setContactPhone(trimToNull(r.contactPhone()));
		if (r.website() != null) p.setWebsite(trimToNull(r.website()));
		if (r.supportEmail() != null) p.setSupportEmail(validateEmail(trimToNull(r.supportEmail())));
		if (r.addressLine1() != null) p.setAddressLine1(trimToNull(r.addressLine1()));
		if (r.addressLine2() != null) p.setAddressLine2(trimToNull(r.addressLine2()));
		if (r.addressCity() != null) p.setAddressCity(trimToNull(r.addressCity()));
		if (r.addressState() != null) p.setAddressState(trimToNull(r.addressState()));
		if (r.addressPostalCode() != null) p.setAddressPostalCode(trimToNull(r.addressPostalCode()));
		if (r.addressCountry() != null) p.setAddressCountry(trimToNull(r.addressCountry()));
		if (r.logoAssetId() != null) p.setLogoAssetId(r.logoAssetId());
		if (r.faviconAssetId() != null) p.setFaviconAssetId(r.faviconAssetId());
		if (r.timezone() != null) p.setTimezone(requireValidZone(r.timezone()));
		if (r.dateFormat() != null) p.setDateFormat(r.dateFormat().trim());
		if (r.defaultCurrencyId() != null) p.setDefaultCurrencyId(requireCurrency(r.defaultCurrencyId()));
	}

	private static String trimToNull(String s) {
		if (s == null) return null;
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static String requireNonBlank(String field, String raw) {
		if (raw == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " is required.");
		String v = raw.trim();
		if (v.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " cannot be blank.");
		return v;
	}

	private static String validateEmail(String email) {
		if (email == null) return null;
		if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid email address: " + email);
		}
		return email;
	}

	private static String requireValidZone(String raw) {
		String zone = raw.trim();
		try {
			ZoneId.of(zone);
			return zone;
		}
		catch (RuntimeException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"'" + zone + "' is not a recognised IANA time zone identifier (for example Asia/Karachi).");
		}
	}

	private UUID requireCurrency(UUID currencyId) {
		Currency c = this.currencies.findById(currencyId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "That currency does not exist."));
		if (!c.isActive()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"That currency is inactive and cannot be the default.");
		}
		return c.getId();
	}

	private Map<String, Object> snapshot(OrganizationProfile p) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("organizationName", p.getOrganizationName());
		m.put("displayName", p.getDisplayName());
		m.put("legalName", p.getLegalName());
		m.put("organizationCode", p.getOrganizationCode());
		m.put("contactEmail", p.getContactEmail());
		m.put("timezone", p.getTimezone());
		m.put("dateFormat", p.getDateFormat());
		m.put("defaultCurrencyId", p.getDefaultCurrencyId());
		return m;
	}

	private OrganizationProfileResponse toResponse(OrganizationProfile p) {
		String currencyCode = p.getDefaultCurrencyId() == null ? null
				: this.currencies.findById(p.getDefaultCurrencyId()).map(Currency::getCode).orElse(null);
		return new OrganizationProfileResponse(
				p.getId(), p.getOrganizationName(), p.getDisplayName(), p.getLegalName(), p.getOrganizationCode(),
				p.getContactEmail(), p.getContactPhone(), p.getWebsite(), p.getSupportEmail(),
				p.getAddressLine1(), p.getAddressLine2(), p.getAddressCity(), p.getAddressState(),
				p.getAddressPostalCode(), p.getAddressCountry(),
				p.getLogoAssetId(), p.getFaviconAssetId(), p.getTimezone(), p.getDateFormat(),
				p.getDefaultCurrencyId(), currencyCode, p.getCreatedAt(), p.getUpdatedAt());
	}

}
