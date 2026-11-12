package com.edam.expense;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * The exchange rate a request was created with.
 *
 * <h2>Why the rate is copied rather than joined</h2>
 * A currency's rate changes. If a request joined the live rate, an approved amount would silently
 * restate itself when treasury updated a rate — the audit figure and the reported figure would
 * diverge, and neither would be wrong on its own terms. Capturing the rate makes the historical
 * figure reproducible, which is the property an auditor needs (BRD AUD-01).
 */
@Entity
@Table(name = "expense_request_currency_snapshot")
public class RequestCurrencySnapshot {

	@EmbeddedId
	private RequestCurrencySnapshotId id = new RequestCurrencySnapshotId();

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("requestId")
	@JoinColumn(name = "request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "institution_id", nullable = false, updatable = false)
	private Long institutionId;

	@Column(name = "rate_to_base", nullable = false, precision = 19, scale = 4)
	private BigDecimal rateToBase;

	@Column(name = "captured_at", nullable = false, updatable = false)
	private Instant capturedAt = Instant.now();

	protected RequestCurrencySnapshot() {
		// JPA
	}

	public RequestCurrencySnapshot(Long institutionId, String currencyCode, BigDecimal rateToBase) {
		this.institutionId = institutionId;
		this.rateToBase = rateToBase;
		this.id = new RequestCurrencySnapshotId(null, currencyCode);
	}

	/**
	 * Link the snapshot to its request. The currency code lives in the embedded id — mapping it as a
	 * separate field as well is what Hibernate rejects, because two properties would write one column.
	 */
	void attachTo(ExpenseRequest request) {
		this.request = request;
		if (this.id == null) {
			this.id = new RequestCurrencySnapshotId();
		}
		this.id.setRequestId(request.getId());
	}

	public RequestCurrencySnapshotId getId() {
		return this.id;
	}

	public String getCurrencyCode() {
		return this.id == null ? null : this.id.getCurrencyCode();
	}

	public BigDecimal getRateToBase() {
		return this.rateToBase;
	}

	public Instant getCapturedAt() {
		return this.capturedAt;
	}

	public Long getInstitutionId() {
		return this.institutionId;
	}

}
