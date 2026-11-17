package com.edam.reporting.service;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.edam.reporting.repository.ReportingJdbcRepository;

import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.repository.ExpenseCategoryRepository;
import com.edam.expenseconfiguration.repository.ExpenseSubcategoryRepository;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.organization.model.Department;
import com.edam.organization.repository.BranchRepository;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.organization.repository.OrganizationProfileRepository;
import com.edam.reporting.dto.ReportingDtos;
import com.edam.reporting.dto.ReportingDtos.DashboardSummary;
import com.edam.reporting.dto.ReportingDtos.DistributionRow;
import com.edam.reporting.dto.ReportingDtos.MetricCard;
import com.edam.reporting.dto.ReportingDtos.RegisterResponse;
import com.edam.reporting.dto.ReportingDtos.RegisterRow;
import com.edam.reporting.dto.ReportingDtos.RegisterTotals;
import com.edam.reporting.dto.ReportingDtos.TrendPoint;
import com.edam.usermanagement.repository.UserRepository;
import com.edam.workflow.repository.TasksRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard summaries and the expense-request register (Phase 4F).
 *
 * <h2>Monetary treatment</h2>
 * All sums use {@code base_currency_amount} captured at submission. No FX re-conversion
 * is performed. The base currency code is read from the active {@code OrganizationProfile}.
 *
 * <h2>Scope predicates</h2>
 * All WHERE/ORDER-BY construction and parameter binding lives in {@link ReportingJdbcRepository},
 * which uses a single source-of-truth whereClause that applies identically to the paged register,
 * count query, dashboard aggregates, distributions, trend, and export streams. The service
 * resolves caller scope via {@link #resolveScope(EdamPrincipal)} and hands a {@code Query}
 * descriptor to the repository; this guarantees every read path enforces identical authorization.
 *
 * <h2>Aggregates run in the database</h2>
 * Dashboard metrics, distributions, trend buckets and register totals are computed by SQL
 * {@code GROUP BY} / {@code COUNT} / {@code SUM} queries; there is no application-level
 * page-size cap on aggregates. The paged register remains bounded (max 500 rows).
 *
 * <h2>Widget catalogue is fixed</h2>
 * Widgets and reports are enumerated in code; no free-form widget or report definitions
 * are accepted. Administrators cannot store executable SQL.
 */
@Service
public class ReportingService {

	private static final Logger log = LoggerFactory.getLogger(ReportingService.class);

	/** Maximum number of rows an export is allowed to emit; enforced before streaming. */
	public static final int EXPORT_ROW_LIMIT = ReportingJdbcRepository.EXPORT_ROW_LIMIT;

	/** PostgreSQL JDBC fetch size used for streaming exports; keeps result-set memory bounded. */
	static final int EXPORT_FETCH_SIZE = ReportingJdbcRepository.EXPORT_FETCH_SIZE;

	/** Columns the CSV/Excel register export emits (whitelist). Names match the SQL column aliases used in {@link ReportingJdbcRepository#REGISTER_SELECT}. */
	public static final List<String> REGISTER_COLUMNS = List.of(
			"request_number", "current_status_code", "expense_type", "requester_name", "department_name",
			"branch_name", "category_name", "subcategory_name", "currency_code",
			"requested_amount", "base_currency_amount", "submitted_at", "decided_at",
			"decision_age_seconds", "last_actor_name");

	private final DepartmentRepository departments;
	private final BranchRepository branches;
	private final ExpenseCategoryRepository categories;
	private final ExpenseSubcategoryRepository subcategories;
	private final TasksRepository tasks;
	private final CurrencyRepository currencyRepo;
	private final OrganizationProfileRepository profiles;
	private final UserRepository users;
	private final ReportingJdbcRepository jdbc;
	private final AuditRecorder audit;

	public ReportingService(DepartmentRepository departments,
			BranchRepository branches, ExpenseCategoryRepository categories,
			ExpenseSubcategoryRepository subcategories,
			TasksRepository tasks,
			CurrencyRepository currencyRepo,
			OrganizationProfileRepository profiles,
			UserRepository users,
			ReportingJdbcRepository jdbc, AuditRecorder audit) {
		this.departments = departments;
		this.branches = branches;
		this.categories = categories;
		this.subcategories = subcategories;
		this.tasks = tasks;
		this.currencyRepo = currencyRepo;
		this.profiles = profiles;
		this.users = users;
		this.jdbc = jdbc;
		this.audit = audit;
	}

	// ------------------------------------------------------------------------------------
	// Dashboard
	// ------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public DashboardSummary dashboard(EdamPrincipal caller, ReportingDtos.ReportFilter filter,
			Integer lookbackDays) {
		Scope scope = resolveScope(caller);
		int days = (lookbackDays == null || lookbackDays < 1) ? 30 : Math.min(lookbackDays, 366);
		Instant now = Instant.now();
		Instant trendFrom = now.minus(Duration.ofDays(days));

		String baseCode = baseCurrencyCode();
		ReportingJdbcRepository.Query dashQ = new ReportingJdbcRepository.Query(toRepoScope(scope), filter, true);

		ReportingJdbcRepository.MetricAggregate agg = this.jdbc.loadMetrics(dashQ);

		// Pending my approval (inbox) is always scoped to the caller's own tasks.
		long pendingMyApproval = this.tasks.countByAssignedUserIdAndStatus(caller.userId(),
				com.edam.workflow.model.WorkflowTask.TaskStatus.PENDING);

		long myRequests = this.jdbc.countMyRequests(caller.userId());

		List<MetricCard> metrics = List.of(
				card("total_requests", "Total requests", BigDecimal.valueOf(agg.totalCount()), null, "Matching requests in period"),
				card("pending_approvals", "Pending approvals (visible)", BigDecimal.valueOf(agg.pendingCount()), null, "Awaiting approver action"),
				card("pending_my_approval", "Pending my approval", BigDecimal.valueOf(pendingMyApproval), null, "Open tasks assigned to you"),
				card("approved_total", "Approved total", BigDecimal.valueOf(agg.approvedCount()), null, "Terminal approved"),
				card("approved_base_amount", "Approved amount", agg.approvedBase(), baseCode, "Base-currency total of approved requests"),
				card("returned_total", "Returned", BigDecimal.valueOf(agg.returnedCount()), null, null),
				card("rejected_total", "Rejected", BigDecimal.valueOf(agg.rejectedCount()), null, null),
				card("cancelled_total", "Cancelled", BigDecimal.valueOf(agg.cancelledCount()), null, null),
				card("my_requests", "My requests", BigDecimal.valueOf(myRequests), null, "Requests you have submitted"),
				card("total_base_amount", "Total amount", agg.totalBase(), baseCode, "Base-currency total of matching requests"));

		List<DistributionRow> statusDist = this.jdbc.distribution(dashQ,
				"select r.current_status_code as k, count(*) as cnt ",
				" group by r.current_status_code",
				rs -> labelFor(ExpenseRequest.Status.valueOf(rs.getString("k"))));

		List<DistributionRow> typeDist = this.jdbc.distribution(dashQ,
				"select coalesce(ec.expense_type,'OPEX')::text as k, count(*) as cnt ",
				" group by 1",
				rs -> rs.getString("k"));

		List<DistributionRow> deptDist = this.jdbc.distributionWithAmount(dashQ,
				"select r.department_id as k, count(*) as cnt, coalesce(sum(r.base_currency_amount),0) as amt ",
				" group by r.department_id",
				rs -> departmentName(toUuid(rs.getObject("k"))), 10);

		List<DistributionRow> catDist = this.jdbc.distributionWithAmount(dashQ,
				"select r.expense_category_id as k, count(*) as cnt, coalesce(sum(r.base_currency_amount),0) as amt ",
				" group by r.expense_category_id",
				rs -> categoryName(toUuid(rs.getObject("k"))), 10);

		List<TrendPoint> trend = this.jdbc.trend(dashQ, trendFrom);

		return new DashboardSummary(baseCode, metrics, statusDist, deptDist, catDist, typeDist, trend);
	}

	// ------------------------------------------------------------------------------------
	// Register
	// ------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public RegisterResponse register(EdamPrincipal caller, ReportingDtos.ReportFilter filter,
			Pageable pageable) {
		Scope scope = resolveScope(caller);
		validateFilter(filter);
		Pageable safe = safePageable(pageable);

		ReportingJdbcRepository.Query q = new ReportingJdbcRepository.Query(toRepoScope(scope), filter, false);

		long total;
		try {
			total = this.jdbc.count(q);
		} catch (RuntimeException rex) {
			log.error("register count failed: {}", rex.getMessage(), rex);
			throw rex;
		}

		List<RegisterRow> mapped;
		try {
			mapped = this.jdbc.page(q, safe, new RegisterRowMapper());
		} catch (RuntimeException rex) {
			log.error("register page failed: {}", rex.getMessage(), rex);
			throw rex;
		}

		// Register totals use the SAME visibility as the paged register (dashboard=false), so
		// total/totals.totalCount agree and own DRAFTs remain visible. Dashboard metrics
		// keep their own DRAFT-excluding dashboard=true path.
		RegisterTotals totals = this.jdbc.totals(q);
		return new RegisterResponse(mapped, total, safe.getPageNumber(), safe.getPageSize(), totals, REGISTER_COLUMNS);
	}

	/**
	 * Count the matching export rows; used by the controller to enforce EXPORT_ROW_LIMIT
	 * before streaming begins.
	 */
	@Transactional(readOnly = true)
	public long countForExport(EdamPrincipal caller, ReportingDtos.ReportFilter filter) {
		Scope scope = resolveScope(caller);
		validateFilter(filter);
		return this.jdbc.count(new ReportingJdbcRepository.Query(toRepoScope(scope), filter, false));
	}

	/** Streams the register as CSV to the given writer; caller is responsible for closing. */
	@Transactional(readOnly = true)
	public void streamCsv(EdamPrincipal caller, ReportingDtos.ReportFilter filter,
			Writer writer, String formatTag) throws IOException {
		Scope scope = resolveScope(caller);
		validateFilter(filter);
		recordExportAudit(caller, formatTag, filter);
		ReportingJdbcRepository.Query q = new ReportingJdbcRepository.Query(toRepoScope(scope), filter, false);
		writer.write('\uFEFF');
		writeCsvRow(writer, REGISTER_COLUMNS);
		this.jdbc.streamExport(q, true, rs -> {
			List<String> vals = new ArrayList<>(REGISTER_COLUMNS.size());
			for (String col : REGISTER_COLUMNS) {
				vals.add(rs.getString(col));
			}
			try {
				writeCsvRow(writer, vals);
			} catch (IOException iox) {
				throw new RuntimeException(iox);
			}
		});
		writer.flush();
	}

	/** Streams the register as XLSX via SXSSF to the given output stream; caller closes. */
	@Transactional(readOnly = true)
	public void streamExcel(EdamPrincipal caller, ReportingDtos.ReportFilter filter,
			java.io.OutputStream out) throws IOException {
		Scope scope = resolveScope(caller);
		validateFilter(filter);
		recordExportAudit(caller, "EXCEL", filter);
		ReportingJdbcRepository.Query q = new ReportingJdbcRepository.Query(toRepoScope(scope), filter, false);
		try (org.apache.poi.xssf.streaming.SXSSFWorkbook wb = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100)) {
			org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Expense requests");
			int[] widths = { 4000, 3000, 3000, 4500, 4000, 3500, 4500, 4500, 2500, 3500, 4000, 5000, 5000, 4000, 4500 };
			for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i]);

			org.apache.poi.ss.usermodel.CellStyle headerStyle = wb.createCellStyle();
			org.apache.poi.ss.usermodel.Font bold = wb.createFont();
			bold.setBold(true);
			headerStyle.setFont(bold);

			String[] headers = { "Request number", "Status", "Expense type", "Requester", "Department",
					"Branch", "Category", "Subcategory", "Currency",
					"Requested amount", "Base currency amount", "Submitted at", "Decided at",
					"Decision age (s)", "Last actor" };
			org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
			for (int c = 0; c < headers.length; c++) {
				org.apache.poi.ss.usermodel.Cell cell = header.createCell(c);
				cell.setCellValue(headers[c]);
				cell.setCellStyle(headerStyle);
			}

			int[] rowNum = { 1 };
			this.jdbc.streamExport(q, true, rs -> {
				org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum[0]++);
				for (int c = 0; c < REGISTER_COLUMNS.size(); c++) {
					org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
					setExcelCell(cell, REGISTER_COLUMNS.get(c), rs);
				}
			});
			wb.write(out);
			wb.dispose();
		}
	}

	private void setExcelCell(org.apache.poi.ss.usermodel.Cell cell, String col, ResultSet rs)
			throws SQLException {
		switch (col) {
			// Monetary: write as text to preserve NUMERIC(19,4) precision exactly.
			case "requested_amount", "base_currency_amount" -> {
				BigDecimal v = rs.getBigDecimal(col);
				if (v != null) cell.setCellValue(v.toPlainString());
			}
			case "decision_age_seconds" -> {
				long v = rs.getLong(col);
				if (!rs.wasNull()) cell.setCellValue(v);
				else cell.setCellValue(0);
			}
			case "submitted_at", "decided_at" -> {
				Timestamp ts = rs.getTimestamp(col);
				if (ts != null) cell.setCellValue(ts.toInstant().toString());
			}
			default -> {
				String s = rs.getString(col);
				if (s != null) cell.setCellValue(s);
			}
		}
	}

	private void recordExportAudit(EdamPrincipal caller, String formatTag, ReportingDtos.ReportFilter filter) {
		StringBuilder reason = new StringBuilder();
		reason.append(formatTag).append(" export: expense-request-register by ").append(caller.userId());
		if (filter != null) {
			if (filter.status() != null) reason.append(" status=").append(filter.status());
			if (filter.departmentId() != null) reason.append(" dept=").append(filter.departmentId());
			if (filter.from() != null) reason.append(" from=").append(filter.from());
			if (filter.to() != null) reason.append(" to=").append(filter.to());
		}
		this.audit.record(AuditAction.EXPORT, "Report", null, reason.toString());
	}

	// ------------------------------------------------------------------------------------
	// Internals — scope resolution, validation, row mapping
	// ------------------------------------------------------------------------------------

	Scope resolveScope(EdamPrincipal caller) {
		boolean installationWide = caller.hasInstallationScope();
		List<UUID> departmentIds = new ArrayList<>();
		List<UUID> branchIds = new ArrayList<>();
		boolean selfOnly = false;
		if (!installationWide) {
			for (DataScope s : caller.dataScopes()) {
				switch (s.type()) {
					case DEPARTMENT -> departmentIds.add(s.referenceId());
					case BRANCH -> branchIds.add(s.referenceId());
					case SELF -> selfOnly = true;
					case ALL, ORGANIZATION_UNIT -> {}
				}
			}
		}
		return new Scope(caller.userId(), installationWide, selfOnly, List.copyOf(departmentIds), List.copyOf(branchIds));
	}

	private static ReportingJdbcRepository.Scope toRepoScope(Scope s) {
		return new ReportingJdbcRepository.Scope(s.callerId(), s.installationWide(),
				s.departmentIds(), s.branchIds());
	}

	/** RowMapper for register rows — uses defensive coercion for PG driver type variance. */
	private final class RegisterRowMapper implements RowMapper<RegisterRow> {
		@Override
		public RegisterRow mapRow(ResultSet rs, int rowNum) throws SQLException {
			long agePrim = rs.getLong("decision_age_seconds");
			long ageBoxed = rs.wasNull() ? 0L : agePrim;
			return new RegisterRow(
				toUuid(rs.getString("id")),
				toStringSafe(rs.getString("request_number")),
				toStringSafe(rs.getString("current_status_code")),
				toStringSafe(rs.getString("expense_type")),
				toStringSafe(rs.getString("requester_name")),
				toUuid(rs.getString("requested_by")),
				toStringSafe(rs.getString("department_name")),
				toStringSafe(rs.getString("branch_name")),
				toStringSafe(rs.getString("category_name")),
				toStringSafe(rs.getString("subcategory_name")),
				toStringSafe(rs.getString("currency_code")),
				rs.getBigDecimal("requested_amount"),
				rs.getBigDecimal("base_currency_amount"),
				toInstant(rs.getObject("submitted_at")),
				toInstant(rs.getObject("decided_at")),
				ageBoxed,
				toStringSafe(rs.getString("last_actor_name")));
		}
	}

	public void validateFilter(ReportingDtos.ReportFilter f) {
		if (f == null) return;
		if (!f.isRangeValid()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The end of the date range must follow its start.");
		}
		if (f.departmentId() != null && !this.departments.existsById(f.departmentId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown department filter.");
		}
		if (f.branchId() != null && !this.branches.existsById(f.branchId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown branch filter.");
		}
		if (f.categoryId() != null && !this.categories.existsById(f.categoryId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown category filter.");
		}
		if (f.subcategoryId() != null && !this.subcategories.existsById(f.subcategoryId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown subcategory filter.");
		}
		if (f.requesterId() != null && !this.users.existsById(f.requesterId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown requester filter.");
		}
		if (f.currency() != null && !f.currency().isBlank()
				&& this.currencyRepo.findByCode(f.currency().toUpperCase()).isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown currency filter.");
		}
	}

	private static final java.util.Set<String> ALLOWED_SORT_PROPERTIES = java.util.Set.of(
			"submittedAt", "requestNumber", "currentStatusCode", "status",
			"baseCurrencyAmount", "requestedAmount");

	private Pageable safePageable(Pageable p) {
		if (p == null) return PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "submittedAt"));
		int size = Math.min(Math.max(p.getPageSize(), 1), 500);
		// Validate sort columns; drop unsupported properties silently so we never concatenate
		// raw client input into SQL. The repository's orderByClause adds default + tiebreaker.
		List<Sort.Order> orders = new ArrayList<>();
		if (p.getSort().isSorted()) {
			for (Sort.Order o : p.getSort()) {
				if (ALLOWED_SORT_PROPERTIES.contains(o.getProperty())) {
					orders.add(o);
				}
			}
		}
		Sort sort = orders.isEmpty() ? Sort.by(Sort.Direction.DESC, "submittedAt") : Sort.by(orders);
		return PageRequest.of(p.getPageNumber(), size, sort);
	}

	private static String toStringSafe(String v) {
		return v; // already a String from ResultSet.getString; null pass-through is fine
	}

	private static Instant toInstant(Object v) {
		if (v == null) return null;
		if (v instanceof Instant i) return i;
		if (v instanceof java.sql.Timestamp ts) return ts.toInstant();
		if (v instanceof java.time.LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC);
		if (v instanceof java.time.OffsetDateTime odt) return odt.toInstant();
		if (v instanceof java.time.ZonedDateTime zdt) return zdt.toInstant();
		throw new ClassCastException("Cannot convert " + v.getClass().getName() + " to Instant: " + v);
	}

	private static UUID toUuid(Object v) {
		if (v == null) return null;
		if (v instanceof UUID u) return u;
		if (v instanceof String s && !s.isEmpty()) return UUID.fromString(s);
		return null;
	}

	private String baseCurrencyCode() {
		return this.profiles.findFirstByActiveTrue()
				.flatMap(p -> this.currencyRepo.findById(p.getDefaultCurrencyId()))
				.map(Currency::getCode)
				.orElse("PKR");
	}

	private MetricCard card(String key, String label, BigDecimal value, String unit, String subtitle) {
		return new MetricCard(key, label, value, unit, subtitle);
	}

	private String labelFor(ExpenseRequest.Status s) {
		return switch (s) {
			case DRAFT -> "Draft";
			case SUBMITTED -> "Submitted";
			case IN_PROGRESS -> "In progress";
			case APPROVED -> "Approved";
			case REJECTED -> "Rejected";
			case RETURNED -> "Returned";
			case CANCELLED -> "Cancelled";
			case REVERSED -> "Reversed";
		};
	}

	private String departmentName(UUID id) {
		if (id == null) return "—";
		return this.departments.findById(id).map(Department::getName).orElse(id.toString());
	}

	private String categoryName(UUID id) {
		if (id == null) return "—";
		return this.categories.findById(id).map(ExpenseCategory::getName).orElse(id.toString());
	}

	/**
	 * Write one CSV row with RFC 4180 quoting and formula-injection prefixing.
	 * Any cell whose first non-whitespace character is one of {@code = + - @} is prefixed with
	 * a single quote so that Excel / Google Sheets / LibreOffice do not parse it as a formula.
	 * Leading tabs, CR and LF are also treated as formula triggers.
	 */
	static String csvCell(String value) {
		if (value == null) return "";
		String v = value;
		int idx = 0;
		while (idx < v.length()) {
			char ch = v.charAt(idx);
			if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') break;
			idx++;
		}
		boolean formulaTrigger = idx < v.length()
				&& (v.charAt(idx) == '=' || v.charAt(idx) == '+'
				    || v.charAt(idx) == '-' || v.charAt(idx) == '@');
		boolean needsQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
				|| v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
		String escaped = needsQuote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
		return formulaTrigger ? "'" + escaped : escaped;
	}

	private static void writeCsvRow(java.io.Writer w, List<String> cells) throws java.io.IOException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0) sb.append(',');
			sb.append(csvCell(cells.get(i)));
		}
		sb.append('\n');
		w.write(sb.toString());
	}

	record Scope(UUID callerId, boolean installationWide, boolean selfOnly,
			List<UUID> departmentIds, List<UUID> branchIds) {}

	// Supported widget/report catalogue
	public static final List<String> WIDGET_CATALOGUE = List.of(
			"total_requests", "pending_approvals", "pending_my_approval", "approved_total",
			"approved_base_amount", "returned_total", "rejected_total", "cancelled_total",
			"my_requests", "total_base_amount",
			"by_status", "by_department", "by_category", "by_expense_type", "trend");

	public static final List<String> REPORT_CATALOGUE = List.of("expense_request_register");
}
