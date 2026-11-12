package com.edam.expense;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Composite key of {@link RequestCurrencySnapshot}: one rate per request and currency. */
@Embeddable
public class RequestCurrencySnapshotId implements Serializable {

	@Column(name = "request_id")
	private Long requestId;

	@Column(name = "currency_code", length = 3)
	private String currencyCode;

	public RequestCurrencySnapshotId() {
		// JPA
	}

	public RequestCurrencySnapshotId(Long requestId, String currencyCode) {
		this.requestId = requestId;
		this.currencyCode = currencyCode;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}

	public String getCurrencyCode() {
		return this.currencyCode;
	}

	public void setCurrencyCode(String currencyCode) {
		this.currencyCode = currencyCode;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof RequestCurrencySnapshotId that)) {
			return false;
		}
		return Objects.equals(this.requestId, that.requestId)
				&& Objects.equals(this.currencyCode, that.currencyCode);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.requestId, this.currencyCode);
	}

}
