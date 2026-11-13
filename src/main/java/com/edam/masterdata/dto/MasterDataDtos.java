package com.edam.masterdata.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for financial master data (Module D).
 *
 * <p>Money and rates are {@link BigDecimal} throughout. A {@code double} would be rejected by the JSON
 * binder for these fields because the schema declares {@code NUMERIC}; the DTO type makes the intent
 * explicit at the edge rather than relying on the column type to catch it later.
 */
public final class MasterDataDtos {

	private MasterDataDtos() {
	}

	@Schema(name = "CurrencyResponse")
	public record CurrencyResponse(UUID id, String code, String name, String symbol, short decimalPlaces,
			boolean active) {
	}

	@Schema(name = "SaveCurrencyRequest")
	public record SaveCurrencyRequest(
			@NotBlank @Size(min = 3, max = 3) @Pattern(regexp = "^[A-Za-z]{3}$",
					message = "must be three letters (ISO 4217)") String code,
			@NotBlank @Size(max = 100) String name, @Size(max = 10) String symbol,
			@Min(value = 0, message = "must be between 0 and 4") @Max(value = 4,
					message = "must be between 0 and 4") short decimalPlaces) {
	}

	@Schema(name = "CurrencyActivationRequest")
	public record CurrencyActivationRequest(boolean active) {
	}

	@Schema(name = "ExchangeRateResponse")
	public record ExchangeRateResponse(UUID id, UUID fromCurrencyId, String fromCurrencyCode, UUID toCurrencyId,
			String toCurrencyCode, BigDecimal rate, Instant effectiveFrom, Instant effectiveTo, Instant createdAt) {
	}

	/**
	 * A new effective period.
	 *
	 * <p>{@code rate} is bounded below at zero-exclusive and carries no upper bound: an implausible rate is
	 * a data-entry error the checker catches in review, not something a bean-validation rule can decide.
	 * What matters is that it is positive and exact.
	 */
	@Schema(name = "SaveExchangeRateRequest")
	public record SaveExchangeRateRequest(@NotNull UUID fromCurrencyId, @NotNull UUID toCurrencyId,
			@NotNull @DecimalMin(value = "0.0", inclusive = false,
					message = "must be greater than zero") @io.swagger.v3.oas.annotations.media.Schema(
							description = "Rate to multiply an amount in the source currency by") BigDecimal rate,
			@Schema(description = "Start of the period; defaults to now when omitted") Instant effectiveFrom,
			@Schema(description = "End of the period; null means the rate is currently in force") Instant effectiveTo) {
	}

	@Schema(name = "VendorResponse")
	public record VendorResponse(UUID id, String code, String name, String taxNumber, String contactPerson,
			String email, String phone, String address, boolean active) {
	}

	@Schema(name = "SaveVendorRequest")
	public record SaveVendorRequest(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 300) String name,
			@Size(max = 64) String taxNumber, @Size(max = 200) String contactPerson, @Email @Size(max = 255) String email,
			@Size(max = 50) String phone, @Size(max = 2000) String address) {
	}

	/**
	 * A tax authority (FBR, a provincial revenue authority, a municipality).
	 *
	 * <p>Deliberately carries no default rate. The rate belongs to the invoice line, because the same
	 * authority applies different rates to different supplies; a single "default" would be applied by
	 * mistake somewhere and would be wrong there. The authority is recorded so that a withheld-tax line can
	 * be attributed to the body it is owed to.
	 */
	@Schema(name = "TaxAuthorityResponse")
	public record TaxAuthorityResponse(UUID id, String code, String name, String description, boolean active) {
	}

	@Schema(name = "SaveTaxAuthorityRequest")
	public record SaveTaxAuthorityRequest(@NotBlank @Size(max = 64) String code,
			@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) {
	}

}
