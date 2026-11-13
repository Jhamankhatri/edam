package com.edam.expenserequest.model;

import java.math.BigDecimal;
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
 * Sales-tax (STA) treatment of a CAPEX request (Module G).
 *
 * <p>Separate rows rather than columns because a single capital purchase can attract several tax
 * treatments — import duty plus sales tax, each with its own authority and reference. Flattening them
 * onto the detail row would cap the model at one tax per request.
 */
@Entity
@Table(name = "capex_sta_details")
public class CapexStaDetail {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "tax_authority_id", nullable = false)
	private UUID taxAuthorityId;

	@Column(name = "sta_reference", length = 150)
	private String staReference;

	@Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal taxAmount;

	@Column(name = "description")
	private String description;

	protected CapexStaDetail() {
	}

	public CapexStaDetail(UUID taxAuthorityId, String staReference, BigDecimal taxAmount, String description) {
		this.taxAuthorityId = taxAuthorityId;
		this.staReference = staReference;
		this.taxAmount = taxAmount;
		this.description = description;
	}

	void attachTo(ExpenseRequest request) {
		this.request = request;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getTaxAuthorityId() {
		return this.taxAuthorityId;
	}

	public String getStaReference() {
		return this.staReference;
	}

	public BigDecimal getTaxAmount() {
		return this.taxAmount;
	}

	public String getDescription() {
		return this.description;
	}

}
