package com.edam.expenserequest.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A vendor quotation supporting a request (Module G).
 *
 * <p>Quotations are rows rather than attached files because the comparison between them is the point: an
 * approver needs to see three quoted amounts side by side, not three PDFs. The document, when there is
 * one, is a separate {@code request_documents} row; the figures are queryable.
 */
@Entity
@Table(name = "expense_quotations")
public class ExpenseQuotation {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "vendor_id")
	private UUID vendorId;

	@Column(name = "quotation_number", nullable = false, length = 100)
	private String quotationNumber;

	@Column(name = "quotation_date", nullable = false)
	private LocalDate quotationDate;

	@Column(name = "quotation_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal quotationAmount;

	@Column(name = "currency_id", nullable = false)
	private UUID currencyId;

	@Column(name = "media_asset_id")
	private UUID mediaAssetId;

	protected ExpenseQuotation() {
	}

	public ExpenseQuotation(UUID vendorId, String quotationNumber, LocalDate quotationDate, BigDecimal amount,
			UUID currencyId, UUID mediaAssetId) {
		this.vendorId = vendorId;
		this.quotationNumber = quotationNumber;
		this.quotationDate = quotationDate;
		this.quotationAmount = amount;
		this.currencyId = currencyId;
		this.mediaAssetId = mediaAssetId;
	}

	void attachTo(ExpenseRequest request) {
		this.request = request;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getVendorId() {
		return this.vendorId;
	}

	public String getQuotationNumber() {
		return this.quotationNumber;
	}

	public LocalDate getQuotationDate() {
		return this.quotationDate;
	}

	public BigDecimal getQuotationAmount() {
		return this.quotationAmount;
	}

	public UUID getCurrencyId() {
		return this.currencyId;
	}

	public UUID getMediaAssetId() {
		return this.mediaAssetId;
	}

}
