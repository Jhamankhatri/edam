package com.edam.masterdata.controller;

import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.masterdata.dto.MasterDataDtos.CurrencyResponse;
import com.edam.masterdata.dto.MasterDataDtos.ExchangeRateResponse;
import com.edam.masterdata.dto.MasterDataDtos.SaveCurrencyRequest;
import com.edam.masterdata.dto.MasterDataDtos.SaveExchangeRateRequest;
import com.edam.masterdata.dto.MasterDataDtos.SaveTaxAuthorityRequest;
import com.edam.masterdata.dto.MasterDataDtos.SaveVendorRequest;
import com.edam.masterdata.dto.MasterDataDtos.TaxAuthorityResponse;
import com.edam.masterdata.dto.MasterDataDtos.VendorResponse;
import com.edam.masterdata.service.CurrencyService;
import com.edam.masterdata.service.VendorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Financial master data: currencies, exchange rates, vendors and tax authorities (Module D).
 *
 * <h2>Reading vs maintaining</h2>
 * Every read requires {@code currency:view} or {@code vendor:view}; every write requires the matching
 * {@code :manage}. The split matters because requesters must be able to see the currencies and rates
 * available to them without being able to change a rate — a rate is a treasury decision, and a requester
 * who could edit one could approve their own request at a favourable conversion.
 *
 * <h2>No delete endpoints</h2>
 * Currencies and vendors are referenced by historical requests and snapshots. Deactivation is offered
 * instead, which removes them from the choices for new work without breaking the record of old work.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/master-data", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Master data", description = "Currencies, effective-dated exchange rates, vendors and tax authorities")
public class MasterDataController {

	private final CurrencyService currencies;

	private final VendorService vendors;

	public MasterDataController(CurrencyService currencies, VendorService vendors) {
		this.currencies = currencies;
		this.vendors = vendors;
	}

	// ------------------------------------------------------------------
	// Currencies
	// ------------------------------------------------------------------

	@GetMapping("/currencies")
	@EdamPermission(Permissions.CURRENCY_VIEW)
	@Operation(summary = "List currencies", description = "Inactive currencies are included only on request.")
	public ApiResponse<List<CurrencyResponse>> currencies(
			@RequestParam(defaultValue = "false") boolean includeInactive, HttpServletRequest request) {
		return ok(this.currencies.listCurrencies(includeInactive), request);
	}

	@PostMapping("/currencies")
	@EdamPermission(Permissions.CURRENCY_MANAGE)
	@Operation(summary = "Add a currency")
	public ResponseEntity<ApiResponse<CurrencyResponse>> createCurrency(
			@Valid @RequestBody SaveCurrencyRequest body, HttpServletRequest request) {
		return created(this.currencies.createCurrency(body), request);
	}

	@PutMapping("/currencies/{id}")
	@EdamPermission(Permissions.CURRENCY_MANAGE)
	@Operation(summary = "Update a currency's name, symbol or precision",
			description = "The ISO code is immutable: historical rates and snapshots reference it.")
	public ApiResponse<CurrencyResponse> updateCurrency(@PathVariable UUID id,
			@Valid @RequestBody SaveCurrencyRequest body, HttpServletRequest request) {
		return ok(this.currencies.updateCurrency(id, body), request);
	}

	@PostMapping("/currencies/{id}/activation")
	@EdamPermission(Permissions.CURRENCY_MANAGE)
	@Operation(summary = "Activate or deactivate a currency")
	public ApiResponse<CurrencyResponse> setCurrencyActive(@PathVariable UUID id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ok(this.currencies.setCurrencyActive(id, active), request);
	}

	// ------------------------------------------------------------------
	// Exchange rates
	// ------------------------------------------------------------------

	@GetMapping("/exchange-rates")
	@EdamPermission(Permissions.CURRENCY_VIEW)
	@Operation(summary = "List exchange-rate periods, newest first")
	public ApiResponse<List<ExchangeRateResponse>> exchangeRates(@RequestParam(required = false) UUID fromCurrencyId,
			@RequestParam(required = false) UUID toCurrencyId, HttpServletRequest request) {
		return ok(this.currencies.listRates(fromCurrencyId, toCurrencyId), request);
	}

	@PostMapping("/exchange-rates")
	@EdamPermission(Permissions.CURRENCY_MANAGE)
	@Operation(summary = "Record an effective period for a currency pair",
			description = "Overlapping periods are refused, so exactly one rate can apply at any instant.")
	public ResponseEntity<ApiResponse<ExchangeRateResponse>> createRate(
			@Valid @RequestBody SaveExchangeRateRequest body, HttpServletRequest request) {
		return created(this.currencies.createRate(body), request);
	}

	// ------------------------------------------------------------------
	// Vendors
	// ------------------------------------------------------------------

	@GetMapping("/vendors")
	@EdamPermission(Permissions.VENDOR_VIEW)
	@Operation(summary = "Search vendors, paged")
	public ApiResponse<PageResponse<VendorResponse>> vendors(@RequestParam(required = false) String search,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort, HttpServletRequest request) {
		return ok(this.vendors.search(search, PageQuery.of(page, size, sort)), request);
	}

	@GetMapping("/vendors/{id}")
	@EdamPermission(Permissions.VENDOR_VIEW)
	@Operation(summary = "Read one vendor")
	public ApiResponse<VendorResponse> vendor(@PathVariable UUID id, HttpServletRequest request) {
		return ok(this.vendors.get(id), request);
	}

	@PostMapping("/vendors")
	@EdamPermission(Permissions.VENDOR_MANAGE)
	@Operation(summary = "Create a vendor")
	public ResponseEntity<ApiResponse<VendorResponse>> createVendor(@Valid @RequestBody SaveVendorRequest body,
			HttpServletRequest request) {
		return created(this.vendors.create(body), request);
	}

	@PutMapping("/vendors/{id}")
	@EdamPermission(Permissions.VENDOR_MANAGE)
	@Operation(summary = "Update a vendor")
	public ApiResponse<VendorResponse> updateVendor(@PathVariable UUID id, @Valid @RequestBody SaveVendorRequest body,
			HttpServletRequest request) {
		return ok(this.vendors.update(id, body), request);
	}

	@PostMapping("/vendors/{id}/activation")
	@EdamPermission(Permissions.VENDOR_MANAGE)
	@Operation(summary = "Activate or deactivate a vendor")
	public ApiResponse<VendorResponse> setVendorActive(@PathVariable UUID id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ok(this.vendors.setActive(id, active), request);
	}

	// ------------------------------------------------------------------
	// Tax authorities
	// ------------------------------------------------------------------

	@GetMapping("/tax-authorities")
	@EdamPermission(Permissions.CURRENCY_VIEW)
	@Operation(summary = "List tax authorities")
	public ApiResponse<List<TaxAuthorityResponse>> taxAuthorities(HttpServletRequest request) {
		return ok(this.vendors.listTaxAuthorities(), request);
	}

	@PostMapping("/tax-authorities")
	@EdamPermission(Permissions.TAX_AUTHORITY_MANAGE)
	@Operation(summary = "Create a tax authority")
	public ResponseEntity<ApiResponse<TaxAuthorityResponse>> createTaxAuthority(
			@Valid @RequestBody SaveTaxAuthorityRequest body, HttpServletRequest request) {
		return created(this.vendors.createTaxAuthority(body), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

	private static <T> ResponseEntity<ApiResponse<T>> created(T data, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(data, CorrelationId.current(), request.getRequestURI()));
	}

}
