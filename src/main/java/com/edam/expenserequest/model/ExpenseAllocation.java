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
 * A charge allocation: how much of the request each department bears (Module G).
 *
 * <h2>Why allocations exist at all</h2>
 * A single request can be shared between departments — a training course for three teams. Without
 * allocations the whole amount lands on the requester's department, and every cost report is then wrong
 * for departments that benefited. The allocation is therefore a first-class row, not a free-text note.
 *
 * <h2>Percentages are optional and independent of amounts</h2>
 * The schema permits either a percentage or just an amount, and does not force them to agree. That is
 * deliberate on the database's part and is honoured here: forcing agreement would break the common case
 * where a percentage is unknown but the amount is contractual.
 */
@Entity
@Table(name = "expense_allocations")
public class ExpenseAllocation {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "department_id", nullable = false)
	private UUID departmentId;

	@Column(name = "branch_id")
	private UUID branchId;

	@Column(name = "cost_center", length = 100)
	private String costCenter;

	@Column(name = "allocation_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal allocationAmount;

	@Column(name = "allocation_percentage", precision = 9, scale = 4)
	private BigDecimal allocationPercentage;

	protected ExpenseAllocation() {
	}

	public ExpenseAllocation(UUID departmentId, UUID branchId, String costCenter, BigDecimal allocationAmount,
			BigDecimal allocationPercentage) {
		this.departmentId = departmentId;
		this.branchId = branchId;
		this.costCenter = costCenter;
		this.allocationAmount = allocationAmount;
		this.allocationPercentage = allocationPercentage;
	}

	void attachTo(ExpenseRequest request) {
		this.request = request;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getDepartmentId() {
		return this.departmentId;
	}

	public UUID getBranchId() {
		return this.branchId;
	}

	public String getCostCenter() {
		return this.costCenter;
	}

	public BigDecimal getAllocationAmount() {
		return this.allocationAmount;
	}

	public BigDecimal getAllocationPercentage() {
		return this.allocationPercentage;
	}

}
