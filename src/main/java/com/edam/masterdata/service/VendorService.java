package com.edam.masterdata.service;

import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.masterdata.dto.MasterDataDtos.SaveTaxAuthorityRequest;
import com.edam.masterdata.dto.MasterDataDtos.SaveVendorRequest;
import com.edam.masterdata.dto.MasterDataDtos.TaxAuthorityResponse;
import com.edam.masterdata.dto.MasterDataDtos.VendorResponse;
import com.edam.masterdata.model.TaxAuthority;
import com.edam.masterdata.model.Vendor;
import com.edam.masterdata.repository.TaxAuthorityRepository;
import com.edam.masterdata.repository.VendorRepository;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vendors and tax authorities (Module D).
 *
 * <h2>Codes are normalised, names are not</h2>
 * A vendor code is an identifier the bank's own finance staff type into a spreadsheet, so it is trimmed
 * and upper-cased to prevent "ACME-01" and "acme-01" becoming two vendors. A vendor <em>name</em> keeps
 * the casing it was entered with, because "e&" and "E&" are different companies and the bank's legal
 * name is part of the evidence trail.
 */
@Service
public class VendorService {

	private final VendorRepository vendors;

	private final TaxAuthorityRepository taxAuthorities;

	private final AuditRecorder audit;

	public VendorService(VendorRepository vendors, TaxAuthorityRepository taxAuthorities, AuditRecorder audit) {
		this.vendors = vendors;
		this.taxAuthorities = taxAuthorities;
		this.audit = audit;
	}

	// ------------------------------------------------------------------
	// Vendors
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<VendorResponse> search(String search, PageQuery pageQuery) {
		// Only name and code are exposed for sorting: they are the columns the endpoint returns and the
		// only ones the operator can meaningfully order by. An unindexed column would let a client ask
		// the database to sort the whole table on every page.
		Page<Vendor> page = this.vendors.search(blankToEmpty(search),
				pageQuery.toPageable(org.springframework.data.domain.Sort.by("name")));
		return PageResponse.of(page.map(VendorService::toResponse));
	}

	@Transactional(readOnly = true)
	public VendorResponse get(UUID id) {
		return toResponse(requireVendor(id));
	}

	@Transactional
	public VendorResponse create(SaveVendorRequest request) {
		String code = normaliseCode(request.code());
		if (this.vendors.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A vendor with code " + code + " exists.");
		}
		Vendor vendor = new Vendor();
		vendor.setCode(code);
		apply(request, vendor);
		Vendor saved = this.vendors.save(vendor);
		this.audit.record(AuditAction.CREATE, "Vendor", saved.getId());
		return toResponse(saved);
	}

	@Transactional
	public VendorResponse update(UUID id, SaveVendorRequest request) {
		Vendor vendor = requireVendor(id);
		String code = normaliseCode(request.code());
		if (!code.equalsIgnoreCase(vendor.getCode()) && this.vendors.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A vendor with code " + code + " exists.");
		}
		vendor.setCode(code);
		apply(request, vendor);
		Vendor saved = this.vendors.save(vendor);
		this.audit.record(AuditAction.UPDATE, "Vendor", id);
		return toResponse(saved);
	}

	@Transactional
	public VendorResponse setActive(UUID id, boolean active) {
		Vendor vendor = requireVendor(id);
		vendor.setActive(active);
		Vendor saved = this.vendors.save(vendor);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "Vendor", id);
		return toResponse(saved);
	}

	// ------------------------------------------------------------------
	// Tax authorities
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public java.util.List<TaxAuthorityResponse> listTaxAuthorities() {
		return this.taxAuthorities.findByActiveTrueOrderByNameAsc().stream().map(VendorService::toResponse).toList();
	}

	@Transactional
	public TaxAuthorityResponse createTaxAuthority(SaveTaxAuthorityRequest request) {
		String code = normaliseCode(request.code());
		if (this.taxAuthorities.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"A tax authority with code " + code + " exists.");
		}
		TaxAuthority authority = new TaxAuthority();
		authority.setCode(code);
		authority.setName(request.name().trim());
		authority.setDescription(request.description());
		TaxAuthority saved = this.taxAuthorities.save(authority);
		this.audit.record(AuditAction.CREATE, "TaxAuthority", saved.getId());
		return toResponse(saved);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public Vendor requireVendor(UUID id) {
		return this.vendors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vendor", id));
	}

	private static void apply(SaveVendorRequest request, Vendor vendor) {
		vendor.setName(request.name().trim());
		vendor.setTaxNumber(request.taxNumber());
		vendor.setContactPerson(request.contactPerson());
		vendor.setEmail((request.email() == null) ? null : request.email().trim());
		vendor.setPhone(request.phone());
		vendor.setAddress(request.address());
	}

	private static VendorResponse toResponse(Vendor vendor) {
		return new VendorResponse(vendor.getId(), vendor.getCode(), vendor.getName(), vendor.getTaxNumber(),
				vendor.getContactPerson(), vendor.getEmail(), vendor.getPhone(), vendor.getAddress(),
				vendor.isActive());
	}

	private static TaxAuthorityResponse toResponse(TaxAuthority authority) {
		return new TaxAuthorityResponse(authority.getId(), authority.getCode(), authority.getName(),
				authority.getDescription(), authority.isActive());
	}

	/** "No search" is the empty string, never null: see the note on the repository predicate. */
	private static String blankToEmpty(String value) {
		return (value == null || value.isBlank()) ? "" : value.trim();
	}

	static String normaliseCode(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A code is required.");
		}
		return raw.trim().toUpperCase(java.util.Locale.ROOT);
	}

}
