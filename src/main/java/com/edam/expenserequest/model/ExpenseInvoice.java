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
 * An invoice supporting a request (Module G).
 *
 * <p>Invoices are rows rather than attachments because FR-REQ-005/006 require the invoice total to be
 * computed and compared with the approval amount. A PDF cannot be compared; a stored amount can.
 *
 * <p>The invoice total is recalculated from the rows on the request, not trusted from the caller — see
 * {@code OpexRequestValidator}. This class only records what one invoice says.
 */
@Entity
@Table(name = "expense_invoices")
public class ExpenseInvoice {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "invoice_number", nullable = false, length = 100)
	private String invoiceNumber;

	@Column(name = "invoice_date", nullable = false)
	private LocalDate invoiceDate;

	@Column(name = "invoice_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal invoiceAmount;

	@Column(name = "currency_id", nullable = false)
	private UUID currencyId;

	@Column(name = "vendor_id")
	private UUID vendorId;

	@Column(name = "media_asset_id")
	private UUID mediaAssetId;

	protected ExpenseInvoice() {
	}

	public ExpenseInvoice(String invoiceNumber, LocalDate invoiceDate, BigDecimal invoiceAmount, UUID currencyId,
			UUID vendorId, UUID mediaAssetId) {
		this.invoiceNumber = invoiceNumber;
		this.invoiceDate = invoiceDate;
		this.invoiceAmount = invoiceAmount;
		this.currencyId = currencyId;
		this.vendorId = vendorId;
		this.mediaAssetId = mediaAssetId;
	}

	void attachTo(ExpenseRequest request) {
		this.request = request;
	}

	public UUID getId() {
		return this.id;
	}

	public String getInvoiceNumber() {
		return this.invoiceNumber;
	}

	public LocalDate getInvoiceDate() {
		return this.invoiceDate;
	}

	public BigDecimal getInvoiceAmount() {
		return this.invoiceAmount;
	}

	public UUID getCurrencyId() {
		return this.currencyId;
	}

	public UUID getVendorId() {
		return this.vendorId;
	}

	public UUID getMediaAssetId() {
		return this.mediaAssetId;
	}

}
