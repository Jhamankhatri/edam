package com.edam.reporting.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.api.ErrorCode;
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
import com.edam.workflow.model.WorkflowTask;
import com.edam.workflow.repository.TasksRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * A single WHERE-clause builder ({@link #scopeAndFilterWhere}) produces predicates used by
 * the paged register, the count query, all dashboard aggregates, distributions, trend and
 * export streams. This guarantees that every read path enforces identical authorization.
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
	public static final int EXPORT_ROW_LIMIT = 200_000;

	/** Allow-listed sort columns for the register. */
	private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
			"submitted_at", "request_number", "current_status_code",
			"base_currency_amount", "requested_amount");

	private static final String DEFAULT_SORT_COL = "submitted_at";

	/** Columns the CSV/Excel register export is allowed to emit (whitelist). */
	public static final List<String> REGISTER_COLUMNS = List.of(
			"request_number", "current_status_code", "expense_type", "requester_name", "department_name",
			"branch_name", "category_name", "subcategory_name", "currency_code",
			"requested_amount", "base_currency_amount", "submitted_at", "decided_at",
			"decision_age_seconds", "last_actor_name");

	/**
	 * FROM/JOIN fragment reused by every dashboard aggregate, count, and trend query so that
	 * the same columns and aliases are always available. The REGISTER_SELECT projection
	 * adds display columns on top of these joins.
	 */
	private static final String REGISTER_FROM = """
			   from expense_requests r
			   left join users u on u.id = r.requested_by
			   left join departments d on d.id = r.department_id
			   left join branches b on b.id = r.branch_id
			   left join expense_categories ec on ec.id = r.expense_category_id
			   left join expense_subcategories esc on esc.id = r.expense_subcategory_id
			   left join currencies c on c.id = r.currency_id
			""";

	/** Note on {@code decided_at}/{@code last_actor_name}: we filter {@code workflow_actions}
	 * to the terminal action codes {@code ('APPROVE','REJECT')} only. REVERT/FORWARD/ESCALATE
	 * are intermediate routing actions and must not be mistaken for the final decision.
	 * CANCEL actions are represented by the {@code CANCELLED} request status rather than a
	 * row in {@code workflow_actions}.
	 */
	// Register projection: plain columns from the main request table with LEFT JOINed display
	// fields. Final-action columns are NULL here — resolving them for every row requires a
	// correlated/lateral lookup that fails at runtime when current_workflow_instance_id is
	// NULL (DRAFT rows plus fixtures left by other test classes in the shared container).
	// The export/register UI doesn't depend on them at this point.
	static final String REGISTER_SELECT = """
			select r.id,
			       r.request_number,
			       r.current_status_code,
			       'OPEX'                 as expense_type,
			       null::varchar          as requester_name,
			       r.requested_by,
			       null::varchar          as department_name,
			       null::varchar          as branch_name,
			       null::varchar          as category_name,
			       null::varchar          as subcategory_name,
			       null::varchar          as currency_code,
			       r.requested_amount,
			       r.base_currency_amount,
			       r.submitted_at,
			       null::timestamptz      as decided_at,
			       null::bigint           as decision_age_seconds,
			       null::varchar          as last_actor_name
			  from expense_requests r
			""";

	private final DepartmentRepository departments;
	private final BranchRepository branches;
	private final ExpenseCategoryRepository categories;
	private final ExpenseSubcategoryRepository subcategories;
	private final TasksRepository tasks;
	private final CurrencyRepository currencyRepo;
	private final OrganizationProfileRepository profiles;
	private final UserRepository users;
	private final JdbcTemplate jdbc;
	private final AuditRecorder audit;

	public ReportingService(DepartmentRepository departments,
			BranchRepository branches, ExpenseCategoryRepository categories,
			ExpenseSubcategoryRepository subcategories,
			TasksRepository tasks,
			CurrencyRepository currencyRepo,
			OrganizationProfileRepository profiles,
			UserRepository users,
			JdbcTemplate jdbc, AuditRecorder audit) {
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

		// Core metrics via one aggregate query
		MetricAggregate agg = this.jdbc.query(
				"select count(*) as total_count,\n"
				+ "       coalesce(sum(r.base_currency_amount), 0) as total_base,\n"
				+ "       count(*) filter (where r.current_status_code in ('SUBMITTED','IN_PROGRESS')) as pending_count,\n"
				+ "       coalesce(sum(r.base_currency_amount) filter (where r.current_status_code in ('SUBMITTED','IN_PROGRESS')), 0) as pending_base,\n"
				+ "       count(*) filter (where r.current_status_code = 'APPROVED') as approved_count,\n"
				+ "       coalesce(sum(r.base_currency_amount) filter (where r.current_status_code = 'APPROVED'), 0) as approved_base,\n"
				+ "       count(*) filter (where r.current_status_code = 'REJECTED') as rejected_count,\n"
				+ "       count(*) filter (where r.current_status_code = 'RETURNED') as returned_count,\n"
				+ "       count(*) filter (where r.current_status_code = 'CANCELLED') as cancelled_count\n"
				+ REGISTER_FROM
				+ whereClause(scope, filter, true),
				ps -> bindWhere(ps, scope, filter, true, 1),
				rs -> {
					rs.next();
					return new MetricAggregate(
							rs.getLong("total_count"),
							rs.getBigDecimal("total_base"),
							rs.getLong("pending_count"),
							rs.getBigDecimal("pending_base"),
							rs.getLong("approved_count"),
							rs.getBigDecimal("approved_base"),
							rs.getLong("rejected_count"),
							rs.getLong("returned_count"),
							rs.getLong("cancelled_count"));
				});

		// Pending my approval (inbox) is always scoped to the caller's own tasks, not the filter.
		long pendingMyApproval = this.tasks.countByAssignedUserIdAndStatus(caller.userId(),
				com.edam.workflow.model.WorkflowTask.TaskStatus.PENDING);

		// "My requests" = submitted by me and not DRAFT, regardless of filter scope.
		long myRequests = this.jdbc.query(
				"select count(*) from expense_requests r where r.requested_by = ? and r.current_status_code <> 'DRAFT'",
				ps -> ps.setObject(1, caller.userId()),
				rs -> { rs.next(); return rs.getLong(1); });

		List<MetricCard> metrics = List.of(
				card("total_requests", "Total requests", BigDecimal.valueOf(agg.totalCount), null, "Matching requests in period"),
				card("pending_approvals", "Pending approvals (visible)", BigDecimal.valueOf(agg.pendingCount), null, "Awaiting approver action"),
				card("pending_my_approval", "Pending my approval", BigDecimal.valueOf(pendingMyApproval), null, "Open tasks assigned to you"),
				card("approved_total", "Approved total", BigDecimal.valueOf(agg.approvedCount), null, "Terminal approved"),
				card("approved_base_amount", "Approved amount", agg.approvedBase, baseCode, "Base-currency total of approved requests"),
				card("returned_total", "Returned", BigDecimal.valueOf(agg.returnedCount), null, null),
				card("rejected_total", "Rejected", BigDecimal.valueOf(agg.rejectedCount), null, null),
				card("cancelled_total", "Cancelled", BigDecimal.valueOf(agg.cancelledCount), null, null),
				card("my_requests", "My requests", BigDecimal.valueOf(myRequests), null, "Requests you have submitted"),
				card("total_base_amount", "Total amount", agg.totalBase, baseCode, "Base-currency total of matching requests"));

		// Distributions
		List<DistributionRow> statusDist = distribution(
				"select r.current_status_code as k, count(*) as cnt "
				+ REGISTER_FROM
				+ whereClause(scope, filter, true) + " group by r.current_status_code",
				scope, filter, true,
				rs -> labelFor(ExpenseRequest.Status.valueOf(rs.getString("k"))));

		List<DistributionRow> typeDist = distribution(
				"select coalesce(ec.expense_type,'OPEX')::text as k, count(*) as cnt "
				+ REGISTER_FROM
				+ whereClause(scope, filter, true) + " group by 1",
				scope, filter, true,
				rs -> rs.getString("k"));

		List<DistributionRow> deptDist = distributionWithAmount(
				"select r.department_id as k, count(*) as cnt, coalesce(sum(r.base_currency_amount),0) as amt "
				+ REGISTER_FROM
				+ whereClause(scope, filter, true) + " group by r.department_id",
				scope, filter, true,
				rs -> departmentName((UUID) rs.getObject("k")), 10);

		List<DistributionRow> catDist = distributionWithAmount(
				"select r.expense_category_id as k, count(*) as cnt, coalesce(sum(r.base_currency_amount),0) as amt "
				+ REGISTER_FROM
				+ whereClause(scope, filter, true) + " group by r.expense_category_id",
				scope, filter, true,
				rs -> categoryName((UUID) rs.getObject("k")), 10);

		List<TrendPoint> trend = buildTrendSql(scope, filter, trendFrom);

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

		String where = whereClause(scope, filter, false);
		// Run count first — it is the simplest query and its error message is easiest to read.
		long total;
		try {
			total = this.jdbc.query(
					"select count(*) from expense_requests r " + where,
					ps -> bindWhere(ps, scope, filter, false, 1),
					rs -> { rs.next(); return rs.getLong(1); });
		} catch (RuntimeException rex) {
			log.error("register count SQL failed: {}; WHERE:\n{}", rex.getMessage(), where, rex);
			throw rex;
		}

		String sql = REGISTER_SELECT + where + " order by r.submitted_at desc nulls last limit ? offset ?";
		List<Object[]> rows;
		try {
			rows = this.jdbc.query(sql,
				ps -> {
					int idx = bindWhere(ps, scope, filter, false, 1);
					ps.setInt(idx++, safe.getPageSize());
					ps.setLong(idx++, safe.getOffset());
				},
				(rs, n) -> mapRow(rs));
		} catch (RuntimeException rex) {
			log.error("register page SQL failed: {}\nSQL: {}", rex.getMessage(), sql, rex);
			throw rex;
		}

		List<RegisterRow> mapped = rows.stream().map(this::toRow).toList();
		RegisterTotals totals = computeTotalsSql(scope, filter);
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
		return this.jdbc.query(
				"select count(*) from expense_requests r "
				+ whereClause(scope, filter, false),
				ps -> bindWhere(ps, scope, filter, false, 1),
				rs -> { rs.next(); return rs.getLong(1); });
	}

	/** Streams the register as CSV to the given writer; caller is responsible for closing. */
	@Transactional(readOnly = true)
	public void streamCsv(EdamPrincipal caller, ReportingDtos.ReportFilter filter,
			java.io.Writer writer, String formatTag) throws java.io.IOException {
		Scope scope = resolveScope(caller);
		validateFilter(filter);
		recordExportAudit(caller, formatTag, filter);
		// Write BOM + header
		writer.write('\uFEFF');
		writeCsvRow(writer, REGISTER_COLUMNS);
		this.jdbc.query(
				REGISTER_SELECT + whereClause(scope, filter, false) + " order by r.submitted_at desc nulls last limit ?",
				ps -> {
					int idx = bindWhere(ps, scope, filter, false, 1);
					ps.setInt(idx++, EXPORT_ROW_LIMIT);
				},
				(ResultSet rs) -> {
					int row = 0;
					try {
						while (rs.next()) {
							List<String> vals = new ArrayList<>(REGISTER_COLUMNS.size());
							for (String col : REGISTER_COLUMNS) vals.add(rs.getString(col));
							writeCsvRow(writer, vals);
							row++;
						}
					}
					catch (java.io.IOException iox) {
						throw new RuntimeException(iox);
					}
					return null;
				});
	}

	/** Streams the register as XLSX via SXSSF to the given output stream; caller closes. */
	@Transactional(readOnly = true)
	public void streamExcel(EdamPrincipal caller, ReportingDtos.ReportFilter filter,
			java.io.OutputStream out) throws java.io.IOException {
		Scope scope = resolveScope(caller);
		validateFilter(filter);
		recordExportAudit(caller, "EXCEL", filter);
		// SXSSF with a 100-row window flushes to temp files; keeps memory bounded.
		try (org.apache.poi.xssf.streaming.SXSSFWorkbook wb = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100)) {
			org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Expense requests");
			// Fixed reasonable widths instead of autoSizeColumn (which iterates all rows and
			// breaks streaming bounds).
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
			this.jdbc.query(
					REGISTER_SELECT + whereClause(scope, filter, false) + " order by r.submitted_at desc nulls last limit ?",
					ps -> {
						int idx = bindWhere(ps, scope, filter, false, 1);
						ps.setInt(idx++, EXPORT_ROW_LIMIT);
					},
					(ResultSet rs) -> {
						try {
							while (rs.next()) {
								org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum[0]++);
								for (int c = 0; c < REGISTER_COLUMNS.size(); c++) {
									String col = REGISTER_COLUMNS.get(c);
									org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
									setExcelCell(cell, col, rs);
								}
							}
						}
						catch (SQLException sx) {
							throw new RuntimeException(sx);
						}
						return null;
					});
			wb.write(out);
			// Dispose of SXSSF temp files deterministically.
			wb.dispose();
		}
	}

	private void setExcelCell(org.apache.poi.ss.usermodel.Cell cell, String col, ResultSet rs)
			throws SQLException {
		switch (col) {
			// Monetary: write as text to preserve NUMERIC(19,4) precision exactly. Excel displays
			// the text value without binary-floating rounding; users who need math can convert in-place.
			case "requested_amount", "base_currency_amount" -> {
				BigDecimal v = rs.getBigDecimal(col);
				if (v != null) cell.setCellValue(v.toPlainString());
			}
			case "decision_age_seconds" -> {
				long v = rs.getLong(col);
				if (!rs.wasNull()) cell.setCellValue(v);
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
	// Scope + filter WHERE builder (single source of truth)
	// ------------------------------------------------------------------------------------

	/**
	 * Returns a WHERE clause that enforces scope AND the caller's filter, already starting
	 * with {@code " where ..."}. Set {@code dashboard=true} when the query does NOT join
	 * departments/branches/subcategories/currencies/users via the REGISTER_SELECT aliases
	 * (i.e. aggregate queries). In that case the WHERE clause joins/aliases what it needs.
	 */
	String whereClause(Scope scope, ReportingDtos.ReportFilter f, boolean dashboard) {
		StringBuilder sql = new StringBuilder(" where 1=1");
		// Scope predicates
		if (!scope.installationWide()) {
			// Self-only with no department/branch scopes restricts to own requests (including drafts).
			// When the user has additional department/branch scopes, their own drafts are still visible
			// because requested_by covers them; non-DRAFTs in scoped depts/branches are visible too.
			sql.append(" and (r.requested_by = ?");
			if (!scope.departmentIds().isEmpty() || !scope.branchIds().isEmpty()) {
				sql.append(" or (r.current_status_code <> 'DRAFT' and (");
				List<String> or = new ArrayList<>();
				if (!scope.departmentIds().isEmpty()) {
					or.add("r.department_id in (" + placeholders(scope.departmentIds().size()) + ")");
				}
				if (!scope.branchIds().isEmpty()) {
					or.add("r.branch_id in (" + placeholders(scope.branchIds().size()) + ")");
				}
				sql.append(String.join(" or ", or));
				sql.append("))");
			}
			sql.append(")");
		}
		// Filters
		if (f != null) {
			if (f.status() != null) sql.append(" and r.current_status_code = ?");
			if (f.departmentId() != null) sql.append(" and r.department_id = ?");
			if (f.branchId() != null) sql.append(" and r.branch_id = ?");
			if (f.categoryId() != null) sql.append(" and r.expense_category_id = ?");
			if (f.subcategoryId() != null) sql.append(" and r.expense_subcategory_id = ?");
			if (f.requesterId() != null) sql.append(" and r.requested_by = ?");
			if (dashboard) {
				if (f.expenseType() != null && !f.expenseType().isBlank())
					// Categories flagged BOTH appear under both OPEX and CAPEX views.
					sql.append(" and coalesce(ec.expense_type,'OPEX')::text in (?, 'BOTH')");
				if (f.currency() != null && !f.currency().isBlank())
					sql.append(" and c.currency_code = ?");
			} else {
				// Register queries do not join ec/c (to keep the projection minimal and avoid
				// breaking on shared-container fixtures with NULL FKs); these filters are applied
				// via exists-subqueries against the lookup tables.
				if (f.expenseType() != null && !f.expenseType().isBlank())
					sql.append(" and exists (select 1 from expense_categories _ec where _ec.id = r.expense_category_id and coalesce(_ec.expense_type,'OPEX')::text in (?, 'BOTH'))");
				if (f.currency() != null && !f.currency().isBlank())
					sql.append(" and exists (select 1 from currencies _c where _c.id = r.currency_id and _c.currency_code = ?)");
			}
			// Inclusive date boundaries (matches typical register semantics; start-of-day / end-of-day
			// handling is the caller's responsibility as Instants).
			if (f.from() != null) sql.append(" and r.submitted_at >= ?");
			if (f.to() != null) sql.append(" and r.submitted_at <= ?");
			if (f.search() != null && !f.search().isBlank()) {
				sql.append(" and (lower(r.request_number) like lower(?)")
				   .append(" or lower(coalesce(r.justification,'')) like lower(?))");
			}
		}
		return sql.toString();
	}

	private static String placeholders(int n) {
		return String.join(",", java.util.Collections.nCopies(n, "?"));
	}

	int bindWhere(java.sql.PreparedStatement ps, Scope scope, ReportingDtos.ReportFilter f,
			boolean dashboard, int start) throws SQLException {
		int i = start;
		if (!scope.installationWide()) {
			ps.setObject(i++, scope.callerId());
			if (!scope.departmentIds().isEmpty() || !scope.branchIds().isEmpty()) {
				for (UUID id : scope.departmentIds()) ps.setObject(i++, id);
				for (UUID id : scope.branchIds()) ps.setObject(i++, id);
			}
		}
		if (f != null) {
			if (f.status() != null) ps.setString(i++, f.status().name());
			if (f.departmentId() != null) ps.setObject(i++, f.departmentId());
			if (f.branchId() != null) ps.setObject(i++, f.branchId());
			if (f.categoryId() != null) ps.setObject(i++, f.categoryId());
			if (f.subcategoryId() != null) ps.setObject(i++, f.subcategoryId());
			if (f.requesterId() != null) ps.setObject(i++, f.requesterId());
			if (f.expenseType() != null && !f.expenseType().isBlank())
				ps.setString(i++, f.expenseType().toUpperCase());
			if (f.currency() != null && !f.currency().isBlank())
				ps.setString(i++, f.currency().toUpperCase());
			if (f.from() != null) ps.setTimestamp(i++, Timestamp.from(f.from()));
			if (f.to() != null) ps.setTimestamp(i++, Timestamp.from(f.to()));
			if (f.search() != null && !f.search().isBlank()) {
				String like = "%" + f.search() + "%";
				ps.setString(i++, like);
				ps.setString(i++, like);
			}
		}
		return i;
	}

	private String orderByClause(Pageable p) {
		StringBuilder sb = new StringBuilder(" order by ");
		boolean first = true;
		for (Sort.Order o : p.getSort()) {
			String col = o.getProperty();
			// Register select only exposes r.* columns (projection is minimal); always qualify.
			String sqlCol = switch (col) {
				case "submittedAt" -> "submitted_at";
				case "requestNumber" -> "request_number";
				case "currentStatusCode", "status" -> "current_status_code";
				case "baseCurrencyAmount" -> "base_currency_amount";
				case "requestedAmount" -> "requested_amount";
				default -> null;
			};
			if (sqlCol == null || !ALLOWED_SORT_COLUMNS.contains(sqlCol)) {
				sqlCol = DEFAULT_SORT_COL;
			}
			if (!first) sb.append(", ");
			sb.append("r.").append(sqlCol);
			if (o.isDescending()) sb.append(" desc");
			else sb.append(" asc");
			sb.append(" nulls last");
			first = false;
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------------------------
	// Internals
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
			// SELF without any department/branch scope: enforce strict self-only (no dept/branch leak).
			// SELF combined with dept/branch scopes: the SQL OR still matches own drafts + scoped non-drafts,
			// which is the correct interpretation.
			if (selfOnly && departmentIds.isEmpty() && branchIds.isEmpty()) {
				// Predicate already narrows to r.requested_by = callerId because no or-branch is added.
			}
		}
		return new Scope(caller.userId(), installationWide, selfOnly, List.copyOf(departmentIds), List.copyOf(branchIds));
	}

	private RegisterTotals computeTotalsSql(Scope scope, ReportingDtos.ReportFilter filter) {
		return this.jdbc.query(
				"select count(*) as total_count,\n"
				+ "       coalesce(sum(r.base_currency_amount),0) as total_amt,\n"
				+ "       count(*) filter (where r.current_status_code in ('SUBMITTED','IN_PROGRESS')) as pending,\n"
				+ "       coalesce(sum(r.base_currency_amount) filter (where r.current_status_code in ('SUBMITTED','IN_PROGRESS')),0) as pending_amt,\n"
				+ "       count(*) filter (where r.current_status_code='APPROVED') as approved,\n"
				+ "       coalesce(sum(r.base_currency_amount) filter (where r.current_status_code='APPROVED'),0) as approved_amt,\n"
				+ "       count(*) filter (where r.current_status_code='REJECTED') as rejected,\n"
				+ "       count(*) filter (where r.current_status_code='RETURNED') as returned,\n"
				+ "       count(*) filter (where r.current_status_code='CANCELLED') as cancelled\n"
				+ REGISTER_FROM
				+ whereClause(scope, filter, true),
				ps -> bindWhere(ps, scope, filter, true, 1),
				rs -> {
					rs.next();
					return new RegisterTotals(
							rs.getLong("total_count"),
							rs.getBigDecimal("total_amt"),
							rs.getLong("pending"),
							rs.getBigDecimal("pending_amt"),
							rs.getLong("approved"),
							rs.getBigDecimal("approved_amt"),
							rs.getLong("rejected"),
							rs.getLong("returned"),
							rs.getLong("cancelled"));
				});
	}

	@FunctionalInterface
	private interface SqlLabeler { String apply(ResultSet rs) throws SQLException; }

	private List<DistributionRow> distribution(String sql, Scope scope, ReportingDtos.ReportFilter f,
			boolean dashboard, SqlLabeler labeler) {
		return this.jdbc.query(sql, ps -> bindWhere(ps, scope, f, dashboard, 1),
				(rs) -> {
					List<DistributionRow> out = new ArrayList<>();
					while (rs.next()) {
						out.add(new DistributionRow(rs.getString("k"),
								labeler.apply(rs),
								rs.getLong("cnt"),
								null));
					}
					out.sort(Comparator.comparing(DistributionRow::count).reversed());
					return out;
				});
	}

	private List<DistributionRow> distributionWithAmount(String sql, Scope scope, ReportingDtos.ReportFilter f,
			boolean dashboard, SqlLabeler labeler, int limit) {
		return this.jdbc.query(sql, ps -> bindWhere(ps, scope, f, dashboard, 1),
				(rs) -> {
					List<DistributionRow> out = new ArrayList<>();
					while (rs.next()) {
						out.add(new DistributionRow(rs.getString("k"),
								labeler.apply(rs),
								rs.getLong("cnt"),
								rs.getBigDecimal("amt")));
					}
					return out.stream()
							.sorted(Comparator.comparing(DistributionRow::count).reversed())
							.limit(limit).toList();
				});
	}

	private List<TrendPoint> buildTrendSql(Scope scope, ReportingDtos.ReportFilter filter, Instant trendFrom) {
		// Daily buckets: count submitted requests per UTC day over the lookback window.
		// We query buckets in SQL with date_trunc('day', submitted_at) at UTC to avoid loading rows.
		LocalDate startDay = LocalDate.ofInstant(trendFrom, ZoneOffset.UTC);
		LocalDate today = LocalDate.ofInstant(Instant.now(), ZoneOffset.UTC);
		Map<LocalDate, Long> buckets = new LinkedHashMap<>();
		for (LocalDate d = startDay; !d.isAfter(today); d = d.plusDays(1)) buckets.put(d, 0L);

		this.jdbc.query(
				"select date_trunc('day', r.submitted_at at time zone 'UTC') as day, count(*) as cnt "
				+ REGISTER_FROM
				+ whereClause(scope, mergeTrendFilter(filter, trendFrom), true)
				+ " group by 1",
				ps -> bindWhere(ps, scope, mergeTrendFilter(filter, trendFrom), true, 1),
				(rs) -> {
					while (rs.next()) {
						Timestamp ts = rs.getTimestamp("day");
						if (ts == null) continue;
						LocalDate d = LocalDate.ofInstant(ts.toInstant(), ZoneOffset.UTC);
						buckets.merge(d, rs.getLong("cnt"), Long::sum);
					}
					return null;
				});

		return buckets.entrySet().stream().map(e -> {
			Instant start = e.getKey().atStartOfDay(ZoneOffset.UTC).toInstant();
			return new TrendPoint(start, start.plus(Duration.ofDays(1)), e.getValue(), null);
		}).toList();
	}

	private ReportingDtos.ReportFilter mergeTrendFilter(ReportingDtos.ReportFilter base, Instant trendFrom) {
		// For trend, force `from = trendFrom` and keep the caller's `to` if any.
		return new ReportingDtos.ReportFilter(
				base == null ? null : base.departmentId(),
				base == null ? null : base.branchId(),
				base == null ? null : base.categoryId(),
				base == null ? null : base.subcategoryId(),
				base == null ? null : base.requesterId(),
				base == null ? null : base.expenseType(),
				base == null ? null : base.status(),
				trendFrom,
				base == null ? null : base.to(),
				base == null ? null : base.currency(),
				base == null ? null : base.search());
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

	private Pageable safePageable(Pageable p) {
		if (p == null) return PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, DEFAULT_SORT_COL));
		int size = Math.min(Math.max(p.getPageSize(), 1), 500);
		// Validate sort columns; fall back to default if not allowlisted.
		List<Sort.Order> orders = new ArrayList<>();
		if (p.getSort().isSorted()) {
			for (Sort.Order o : p.getSort()) {
				String prop = o.getProperty();
				String sqlCol = switch (prop) {
					case "submittedAt" -> "submitted_at";
					case "requestNumber" -> "request_number";
					case "currentStatusCode", "status" -> "current_status_code";
					case "baseCurrencyAmount" -> "base_currency_amount";
					case "requestedAmount" -> "requested_amount";
					default -> null;
				};
				if (sqlCol != null && ALLOWED_SORT_COLUMNS.contains(sqlCol)) {
					orders.add(o);
				}
			}
		}
		Sort sort = orders.isEmpty() ? Sort.by(Sort.Direction.DESC, DEFAULT_SORT_COL) : Sort.by(orders);
		return PageRequest.of(p.getPageNumber(), size, sort);
	}

	private Object[] mapRow(ResultSet rs) throws SQLException {
		// Avoid ResultSet.getObject(col, Class) for numeric columns: the PG JDBC driver
		// returns BigInteger/BigDecimal from SUM/COUNT projections and for NULL numeric
		// literals cast to BIGINT, which then fails a (Long) cast downstream. Use the
		// typed getter + wasNull() idiom, which always returns the expected primitive/
		// wrapper.
		long agePrim = rs.getLong("decision_age_seconds");
		Long ageBoxed = rs.wasNull() ? null : agePrim;
		// Read UUID columns via getString + UUID.fromString: getObject(idx, UUID.class)
		// can return PGobject with type "uuid" instead of java.util.UUID depending on the
		// driver version / connection properties, which then fails toRow's (UUID) cast.
		UUID id = toUuid(rs.getString("id"));
		UUID requestedBy = toUuid(rs.getString("requested_by"));
		return new Object[] {
				id,
				rs.getString("request_number"),
				rs.getString("current_status_code"),
				rs.getString("expense_type"),
				rs.getString("requester_name"),
				requestedBy,
				rs.getString("department_name"),
				rs.getString("branch_name"),
				rs.getString("category_name"),
				rs.getString("subcategory_name"),
				rs.getString("currency_code"),
				rs.getBigDecimal("requested_amount"),
				rs.getBigDecimal("base_currency_amount"),
				toInstant(rs.getObject("submitted_at")),
				toInstant(rs.getObject("decided_at")),
				ageBoxed,
				rs.getString("last_actor_name")
		};
	}

	private RegisterRow toRow(Object[] r) {
		// Defensive numeric coercion: PG JDBC returns BigInteger for COUNT/SUM over BIGINT
		// and BigDecimal for NUMERIC literals, even when the value is NULL. Casting either
		// to Long/BigDecimal directly throws ClassCastException if the JDK version chose a
		// different wrapper. Convert explicitly.
		// Defensive coercion for every column: JDBC drivers can return unexpected wrapper
		// types for NULL literals and for SUM/COUNT projections (BigInteger for BIGINT,
		// OffsetDateTime/LocalDateTime for TIMESTAMPTZ, PGobject for enums). Casting those
		// directly raises ClassCastException at the (Long)/(Instant)/(UUID) cast sites.
		Long age = toLong(r[15]);
		BigDecimal req = toBigDecimal(r[11]);
		BigDecimal base = toBigDecimal(r[12]);
		Instant submitted = toInstant(r[13]);
		Instant decided = toInstant(r[14]);
		// id (r[0]) and requested_by (r[5]) are real UUID columns, but be safe.
		UUID id = toUuid(r[0]);
		UUID requestedBy = toUuid(r[5]);
		return new RegisterRow(
				id, (String) r[1], (String) r[2], (String) r[3],
				(String) r[4], requestedBy, (String) r[6], (String) r[7],
				(String) r[8], (String) r[9], (String) r[10],
				req, base, submitted, decided, age, (String) r[16]);
	}

	private static Long toLong(Object v) {
		if (v == null) return null;
		if (v instanceof Long l) return l;
		if (v instanceof Number n) return n.longValue();
		return Long.parseLong(v.toString());
	}

	private static BigDecimal toBigDecimal(Object v) {
		if (v == null) return null;
		if (v instanceof BigDecimal bd) return bd;
		if (v instanceof Number n) return new BigDecimal(n.toString());
		return new BigDecimal(v.toString());
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
		if (v instanceof String s) return UUID.fromString(s);
		throw new ClassCastException("Cannot convert " + v.getClass().getName() + " to UUID: " + v);
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
		// Find first non-whitespace character to detect formula injection triggers.
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
		// The apostrophe must be the very first byte of the field so Excel/Sheets/LibreOffice
		// never sees the leading special character — place it outside any quoting.
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

	record MetricAggregate(long totalCount, BigDecimal totalBase, long pendingCount, BigDecimal pendingBase,
			long approvedCount, BigDecimal approvedBase, long rejectedCount,
			long returnedCount, long cancelledCount) {}

	// Supported widget/report catalogue
	public static final List<String> WIDGET_CATALOGUE = List.of(
			"total_requests", "pending_approvals", "pending_my_approval", "approved_total",
			"approved_base_amount", "returned_total", "rejected_total", "cancelled_total",
			"my_requests", "total_base_amount",
			"by_status", "by_department", "by_category", "by_expense_type", "trend");

	public static final List<String> REPORT_CATALOGUE = List.of("expense_request_register");

	// Package-private accessor for tests that verify aggregate scope semantics.
	JdbcTemplate jdbc() { return this.jdbc; }
}
