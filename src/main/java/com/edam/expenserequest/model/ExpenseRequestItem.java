package com.edam.expenserequest.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * One line item of a request (Module G, CAPEX in particular).
 *
 * <h2>The total is computed, never supplied</h2>
 * The database enforces {@code total_amount = round(quantity × unit_price, 4)}. The constructor therefore
 * derives the total rather than accepting it: a caller-supplied total that disagreed with the arithmetic
 * would be rejected by PostgreSQL at flush time, surfacing as an opaque persistence error instead of a
 * field-level message. Computing it here makes the invalid state unrepresentable in Java as well as in the
 * schema, and keeps the two in agreement by construction.
 *
 * <p>{@link RoundingMode#HALF_UP} is chosen to match PostgreSQL's {@code round(numeric, 4)}, so the
 * value Java computes is the value the constraint will accept. A different rounding mode here would make
 * some inputs fail the constraint for reasons that look arbitrary.
 */
@Entity
@Table(name = "expense_request_items")
public class ExpenseRequestItem {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "item_description", nullable = false, length = 500)
	private String itemDescription;

	@Column(name = "quantity", nullable = false, precision = 19, scale = 4)
	private BigDecimal quantity;

	@Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
	private BigDecimal unitPrice;

	@Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal totalAmount;

	@Column(name = "warranty_date")
	private LocalDate warrantyDate;

	protected ExpenseRequestItem() {
	}

	public ExpenseRequestItem(String itemDescription, BigDecimal quantity, BigDecimal unitPrice, LocalDate warrantyDate) {
		if (quantity == null || quantity.signum() <= 0) {
			throw new IllegalArgumentException("Quantity must be greater than zero");
		}
		if (unitPrice == null || unitPrice.signum() < 0) {
			throw new IllegalArgumentException("Unit price must not be negative");
		}
		this.itemDescription = itemDescription;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		// Matches the database's round(quantity * unit_price, 4).
		this.totalAmount = quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
		this.warrantyDate = warrantyDate;
	}

	void attachTo(ExpenseRequest request) {
		this.request = request;
	}

	public UUID getId() {
		return this.id;
	}

	public String getItemDescription() {
		return this.itemDescription;
	}

	public BigDecimal getQuantity() {
		return this.quantity;
	}

	public BigDecimal getUnitPrice() {
		return this.unitPrice;
	}

	public BigDecimal getTotalAmount() {
		return this.totalAmount;
	}

	public LocalDate getWarrantyDate() {
		return this.warrantyDate;
	}

}
