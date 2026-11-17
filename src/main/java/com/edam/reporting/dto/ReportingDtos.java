package com.edam.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.expenserequest.model.ExpenseRequest;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.data.domain.Page;

/**
 * Request and response shapes for dashboards and reports (Phase 4F).
 *
 * <h2>Scope discipline</h2>
 * Reports and widgets in this phase are system-defined. Every metric is calculated
 * server-side using parameters that enumerate discrete choices — never arbitrary SQL,
 * expressions, or column lists. Administrators pick which <em>available</em> report or
 * widget a dashboard includes rather than authoring a query. That is the hard line that
 * keeps the reporting surface from becoming a scripting engine.
 */
public final class ReportingDtos {

	private ReportingDtos() {}

	// -------------------------------------------------------------------------------------
	// Dashboard
	// -------------------------------------------------------------------------------------

	/** A single scalar card on a dashboard, e.g. "Pending my approval". */
	public record MetricCard(String key, String label, BigDecimal value, String unit, String subtitle) {
	}

	/** One row of a distribution (department/category/status breakdown). */
	public record DistributionRow(String key, String label, long count, BigDecimal amount) {
	}

	/** A time-bucketed total for trend charts. */
	public record TrendPoint(Instant bucketStart, Instant bucketEnd, long count, BigDecimal amount) {
	}

	/**
	 * Dashboard summary payload.
	 *
	 * <p>All monetary values are expressed in the installation default currency
	 * ({@code baseCurrencyCode}) using {@code base_currency_amount}, which is set at
	 * request submission using the exchange rate in force at that time. We deliberately
	 * do not re-convert at report time: doing so would change historical totals, and no
	 * approved policy for a reporting FX rate exists yet.
	 */
	public record DashboardSummary(String baseCurrencyCode,
			List<MetricCard> metrics,
			List<DistributionRow> byStatus,
			List<DistributionRow> byDepartment,
			List<DistributionRow> byCategory,
			List<DistributionRow> byExpenseType,
			List<TrendPoint> trend) {
	}

	// -------------------------------------------------------------------------------------
	// Report register
	// -------------------------------------------------------------------------------------

	/** Filter parameters shared across register reports. All are optional; applied with AND semantics. */
	public record ReportFilter(UUID departmentId, UUID branchId, UUID categoryId, UUID subcategoryId,
			UUID requesterId, String expenseType, ExpenseRequest.Status status,
			Instant from, Instant to, String currency, String search) {

		@AssertTrue(message = "The end of the date range must follow its start.")
		public boolean isRangeValid() {
			return from == null || to == null || !to.isBefore(from);
		}
	}

	/** One row of the expense-request register (wide but denormalised, never an entity). */
	public record RegisterRow(UUID id, String requestNumber, String status, String expenseType,
			String requesterName, UUID requesterId, String departmentName, String branchName,
			String categoryName, String subcategoryName, String currencyCode, BigDecimal requestedAmount,
			BigDecimal baseCurrencyAmount, Instant submittedAt, Instant decidedAt,
			long decisionAgeSeconds, String lastActorName) {
	}

	/** Paged register response plus column catalogue and aggregate totals. */
	public record RegisterResponse(List<RegisterRow> rows, long total, int page, int size,
			RegisterTotals totals, List<String> availableColumns) {

		public static RegisterResponse from(Page<RegisterRow> page, RegisterTotals totals,
				List<String> columns) {
			return new RegisterResponse(page.getContent(), page.getTotalElements(),
					page.getNumber(), page.getSize(), totals, columns);
		}
	}

	/** Totals accompanying a register view, all in base currency. */
	public record RegisterTotals(long totalCount, BigDecimal totalBaseAmount,
			long pendingCount, BigDecimal pendingBaseAmount,
			long approvedCount, BigDecimal approvedBaseAmount,
			long rejectedCount, long returnedCount, long cancelledCount) {
	}

	// -------------------------------------------------------------------------------------
	// Export
	// -------------------------------------------------------------------------------------

	/** Supported export formats. */
	public enum ExportFormat { CSV, EXCEL }

	/** Export request descriptor. */
	public record ExportRequest(String reportKey, ReportFilter filter, ExportFormat format) {
	}

}
