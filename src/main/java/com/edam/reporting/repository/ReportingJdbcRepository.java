package com.edam.reporting.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.reporting.dto.ReportingDtos;
import com.edam.reporting.dto.ReportingDtos.DistributionRow;
import com.edam.reporting.dto.ReportingDtos.RegisterTotals;
import com.edam.reporting.dto.ReportingDtos.TrendPoint;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

/**
 * All native SQL / JDBC access for the reporting module lives here so services contain no
 * JDBC wiring and the project-wide {@code Controller → Service → Repository} layering holds.
 *
 * <p>WHERE-clause construction, ORDER-BY construction, parameter binding, and SQL string
 * composition for every reporting query (dashboard aggregates, distributions, trend,
 * register count/page/totals, exports) live in this class. The service only validates
 * business filters and maps rows to DTOs; it never concatenates SQL, binds PreparedStatement
 * parameters, or references JDBC types.
 */
@Repository
public class ReportingJdbcRepository {

	/** Default ORDER BY column for register queries. */
	public static final String DEFAULT_SORT_COL = "r.submitted_at";

	/** PostgreSQL JDBC fetch size used for streaming exports; keeps result-set memory bounded. */
	public static final int EXPORT_FETCH_SIZE = 512;

	/** Maximum rows an export is allowed to emit; enforced before streaming. */
	public static final int EXPORT_ROW_LIMIT = 200_000;

	/** Trusted ORDER BY expressions for register sorts. */
	private static final Map<String, String> ALLOWED_SORT_COLUMNS = Map.ofEntries(
			Map.entry("submittedAt", "r.submitted_at"),
			Map.entry("requestNumber", "r.request_number"),
			Map.entry("currentStatusCode", "r.current_status_code"),
			Map.entry("status", "r.current_status_code"),
			Map.entry("baseCurrencyAmount", "r.base_currency_amount"),
			Map.entry("requestedAmount", "r.requested_amount"));

	/** FROM/JOIN fragment for dashboard aggregate queries. */
	public static final String DASHBOARD_FROM = """
			   from expense_requests r
			   left join expense_categories ec on ec.id = r.expense_category_id
			   left join currencies c on c.id = r.currency_id
			""";

	/** FROM/JOIN fragment for register count queries. */
	public static final String REGISTER_COUNT_FROM = """
			   from expense_requests r
			   left join expense_categories ec on ec.id = r.expense_category_id
			   left join currencies c on c.id = r.currency_id
			""";

	/**
	 * Register projection. Decision resolution uses LEFT JOIN LATERAL to the most recent
	 * APPROVE or REJECT workflow_action.
	 */
	public static final String REGISTER_SELECT = """
			select r.id,
			       r.request_number,
			       r.current_status_code,
			       coalesce(ec.expense_type, 'OPEX')::text as expense_type,
			       (coalesce(u.first_name,'') || case when u.first_name is not null and u.last_name is not null then ' ' else '' end || coalesce(u.last_name,'')) as requester_name,
			       r.requested_by,
			       d.department_name,
			       b.branch_name,
			       ec.category_name,
			       esc.subcategory_name,
			       c.currency_code,
			       r.requested_amount,
			       r.base_currency_amount,
			       r.submitted_at,
			       dec.action_at as decided_at,
			       case when dec.action_at is not null and r.submitted_at is not null
			            then extract(epoch from (dec.action_at - r.submitted_at))::bigint
			            else null end as decision_age_seconds,
			       (coalesce(ua.first_name,'') || case when ua.first_name is not null and ua.last_name is not null then ' ' else '' end || coalesce(ua.last_name,'')) as last_actor_name
			  from expense_requests r
			  left join users u on u.id = r.requested_by
			  left join departments d on d.id = r.department_id
			  left join branches b on b.id = r.branch_id
			  left join expense_categories ec on ec.id = r.expense_category_id
			  left join expense_subcategories esc on esc.id = r.expense_subcategory_id
			  left join currencies c on c.id = r.currency_id
			  left join lateral (
			      select wa.action_at, wa.performed_by
			        from workflow_instances wi
			        join workflow_tasks     wt  on wt.workflow_instance_id = wi.id
			        join workflow_actions   wa  on wa.workflow_task_id   = wt.id
			       where wi.id = r.current_workflow_instance_id
			         and wa.action_code in ('APPROVE','REJECT')
			       order by wa.action_at desc, wa.id desc
			       limit 1
			  ) dec on true
			  left join users ua on ua.id = dec.performed_by
			""";

	// -------------------------------------------------------------------------------------
	// Query descriptor — built by the service, consumed by repository SQL methods
	// -------------------------------------------------------------------------------------

	/** Caller visibility scope (mirrors ReportingService.Scope). */
	public record Scope(UUID callerId, boolean installationWide,
			List<UUID> departmentIds, List<UUID> branchIds) {}

	/** All inputs needed to compose any reporting query. */
	public record Query(Scope scope, ReportingDtos.ReportFilter filter, boolean dashboard) {}

	// -------------------------------------------------------------------------------------
	// High-level typed queries (called by the service; no SQL in the caller)
	// -------------------------------------------------------------------------------------

	public record MetricAggregate(long totalCount, java.math.BigDecimal totalBase,
			long pendingCount, java.math.BigDecimal pendingBase,
			long approvedCount, java.math.BigDecimal approvedBase,
			long rejectedCount, long returnedCount, long cancelledCount) {}

	public MetricAggregate loadMetrics(Query q) {
		String sql = "select count(*) as total_count,\n"
				+ "       coalesce(sum(r.base_currency_amount), 0) as total_base,\n"
				+ "       count(*) filter (where r.current_status_code in ('SUBMITTED','IN_PROGRESS')) as pending_count,\n"
				+ "       coalesce(sum(r.base_currency_amount) filter (where r.current_status_code in ('SUBMITTED','IN_PROGRESS')), 0) as pending_base,\n"
				+ "       count(*) filter (where r.current_status_code = 'APPROVED') as approved_count,\n"
				+ "       coalesce(sum(r.base_currency_amount) filter (where r.current_status_code = 'APPROVED'), 0) as approved_base,\n"
				+ "       count(*) filter (where r.current_status_code = 'REJECTED') as rejected_count,\n"
				+ "       count(*) filter (where r.current_status_code = 'RETURNED') as returned_count,\n"
				+ "       count(*) filter (where r.current_status_code = 'CANCELLED') as cancelled_count\n"
				+ DASHBOARD_FROM
				+ whereClause(q);
		return this.jdbc.query(sql, pss(q, 1), rs -> {
			rs.next();
			return new MetricAggregate(
				rs.getLong("total_count"), rs.getBigDecimal("total_base"),
				rs.getLong("pending_count"), rs.getBigDecimal("pending_base"),
				rs.getLong("approved_count"), rs.getBigDecimal("approved_base"),
				rs.getLong("rejected_count"), rs.getLong("returned_count"),
				rs.getLong("cancelled_count"));
		});
	}

	public long countMyRequests(UUID callerId) {
		Long v = this.jdbc.query(
				"select count(*) from expense_requests r where r.requested_by = ? and r.current_status_code <> 'DRAFT'",
				ps -> ps.setObject(1, callerId),
				rs -> { rs.next(); return rs.getLong(1); });
		return v == null ? 0L : v;
	}

	@FunctionalInterface
	public interface RowLabeler { String apply(ResultSet rs) throws SQLException; }

	/** Distribution without amount column. */
	public List<DistributionRow> distribution(Query q, String selectPrefix, String groupByClause,
			RowLabeler labeler) {
		String sql = selectPrefix + DASHBOARD_FROM + whereClause(q) + groupByClause;
		return this.jdbc.query(sql, pss(q, 1), (rs) -> {
			List<DistributionRow> out = new ArrayList<>();
			while (rs.next()) {
				out.add(new DistributionRow(rs.getString("k"), labeler.apply(rs), rs.getLong("cnt"), null));
			}
			out.sort(java.util.Comparator.comparing(DistributionRow::count).reversed());
			return out;
		});
	}

	/** Distribution with amount column (top-N by count). */
	public List<DistributionRow> distributionWithAmount(Query q, String selectPrefix, String groupByClause,
			RowLabeler labeler, int limit) {
		String sql = selectPrefix + DASHBOARD_FROM + whereClause(q) + groupByClause;
		return this.jdbc.query(sql, pss(q, 1), (rs) -> {
			List<DistributionRow> out = new ArrayList<>();
			while (rs.next()) {
				out.add(new DistributionRow(rs.getString("k"), labeler.apply(rs),
						rs.getLong("cnt"), rs.getBigDecimal("amt")));
			}
			return out.stream()
					.sorted(java.util.Comparator.comparing(DistributionRow::count).reversed())
					.limit(limit).toList();
		});
	}

	/** Returns a daily-bucketed trend series spanning [trendFrom, today] in UTC. */
	public List<TrendPoint> trend(Query q, Instant trendFrom) {
		LocalDate startDay = LocalDate.ofInstant(trendFrom, ZoneOffset.UTC);
		LocalDate today = LocalDate.ofInstant(Instant.now(), ZoneOffset.UTC);
		Map<LocalDate, Long> buckets = new LinkedHashMap<>();
		for (LocalDate d = startDay; !d.isAfter(today); d = d.plusDays(1)) buckets.put(d, 0L);

		String sql = "select date_trunc('day', r.submitted_at at time zone 'UTC') as day, count(*) as cnt "
				+ DASHBOARD_FROM
				+ whereClause(q)
				+ " group by 1";
		this.jdbc.query(sql, pss(q, 1), (RowCallbackHandler) rs -> {
			Timestamp ts = rs.getTimestamp("day");
			if (ts == null) return;
			LocalDate d = LocalDate.ofInstant(ts.toInstant(), ZoneOffset.UTC);
			buckets.merge(d, rs.getLong("cnt"), Long::sum);
		});

		return buckets.entrySet().stream().map(e -> {
			Instant start = e.getKey().atStartOfDay(ZoneOffset.UTC).toInstant();
			return new TrendPoint(start, start.plus(java.time.Duration.ofDays(1)), e.getValue(), null);
		}).toList();
	}

	/** Count of rows matching the filter/scope (used for register pagination + export limit check). */
	public long count(Query q) {
		String sql = "select count(*) " + REGISTER_COUNT_FROM + whereClause(q);
		Long v = this.jdbc.query(sql, pss(q, 1), rs -> { rs.next(); return rs.getLong(1); });
		return v == null ? 0L : v;
	}

	/** Aggregate totals used by register responses and dashboard metric cards. */
	public RegisterTotals totals(Query q) {
		String from = q.dashboard() ? DASHBOARD_FROM : REGISTER_COUNT_FROM;
		String sql = "select count(*) as total_count,\n"
				+ "       coalesce(sum(r.base_currency_amount),0) as total_amt,\n"
				+ "       count(*) filter (where r.current_status_code in ('SUBMITTED','IN_PROGRESS')) as pending,\n"
				+ "       coalesce(sum(r.base_currency_amount) filter (where r.current_status_code in ('SUBMITTED','IN_PROGRESS')),0) as pending_amt,\n"
				+ "       count(*) filter (where r.current_status_code='APPROVED') as approved,\n"
				+ "       coalesce(sum(r.base_currency_amount) filter (where r.current_status_code='APPROVED'),0) as approved_amt,\n"
				+ "       count(*) filter (where r.current_status_code='REJECTED') as rejected,\n"
				+ "       count(*) filter (where r.current_status_code='RETURNED') as returned,\n"
				+ "       count(*) filter (where r.current_status_code='CANCELLED') as cancelled\n"
				+ from + whereClause(q);
		return this.jdbc.query(sql, pss(q, 1), rs -> {
			rs.next();
			return new RegisterTotals(
				rs.getLong("total_count"), rs.getBigDecimal("total_amt"),
				rs.getLong("pending"), rs.getBigDecimal("pending_amt"),
				rs.getLong("approved"), rs.getBigDecimal("approved_amt"),
				rs.getLong("rejected"), rs.getLong("returned"), rs.getLong("cancelled"));
		});
	}

	/** Paged register rows. */
	public <T> List<T> page(Query q, Pageable pageable, RowMapper<T> rowMapper) {
		String sql = REGISTER_SELECT + whereClause(q) + orderByClause(pageable) + " limit ? offset ?";
		return this.jdbc.query(sql, ps -> {
			int idx = pss(q, ps, 1);
			ps.setInt(idx++, pageable.getPageSize());
			ps.setLong(idx++, pageable.getOffset());
		}, rowMapper);
	}

	/** Stream register rows for CSV/Excel export. Uses a forward-only cursor when {@code forwardCursor=true}. */
	public void streamExport(Query q, boolean forwardCursor, RowCallbackHandler rowHandler) {
		String sql = REGISTER_SELECT + whereClause(q) + orderByClause(defaultExportSort()) + " limit ?";
		this.jdbc.query(sql, ps -> {
			int idx = pss(q, ps, 1);
			ps.setInt(idx++, EXPORT_ROW_LIMIT);
			if (forwardCursor) {
				ps.setFetchSize(EXPORT_FETCH_SIZE);
				ps.setFetchDirection(ResultSet.FETCH_FORWARD);
			}
		}, rowHandler);
	}

	// -------------------------------------------------------------------------------------
	// Generic JdbcTemplate passthrough — retained for any callers that need ad-hoc SQL.
	// Service no longer uses these; they remain for backwards compatibility and tests.
	// -------------------------------------------------------------------------------------

	public <T> T query(String sql, PreparedStatementSetter pss, ResultSetExtractor<T> rse) {
		return this.jdbc.query(sql, pss, rse);
	}

	public <T> T query(String sql, ResultSetExtractor<T> rse) {
		return this.jdbc.query(sql, rse);
	}

	public <T> List<T> query(String sql, PreparedStatementSetter pss, RowMapper<T> rm) {
		return this.jdbc.query(sql, pss, rm);
	}

	public <T> List<T> query(String sql, RowMapper<T> rm) {
		return this.jdbc.query(sql, rm);
	}

	public void query(String sql, PreparedStatementSetter pss, RowCallbackHandler rch) {
		this.jdbc.query(sql, pss, rch);
	}

	public void query(String sql, RowCallbackHandler rch) {
		this.jdbc.query(sql, rch);
	}

	/** For package-private test hooks that need the raw template. */
	public JdbcTemplate jdbcTemplate() { return this.jdbc; }

	// -------------------------------------------------------------------------------------
	// SQL construction (WHERE + ORDER BY) and parameter binding
	// -------------------------------------------------------------------------------------

	/**
	 * Returns a WHERE clause that enforces scope AND the caller's filter, already starting
	 * with {@code " where ..."}. When {@code dashboard=true} DRAFTs are excluded across the
	 * board; when {@code false} own DRAFTs remain visible via the scope predicate.
	 */
	String whereClause(Query q) {
		StringBuilder sql = new StringBuilder(" where 1=1");
		if (q.dashboard()) {
			sql.append(" and r.current_status_code <> 'DRAFT'");
		}
		Scope s = q.scope();
		if (!s.installationWide()) {
			sql.append(" and (r.requested_by = ?");
			if (!s.departmentIds().isEmpty() || !s.branchIds().isEmpty()) {
				sql.append(" or (r.current_status_code <> 'DRAFT' and (");
				List<String> or = new ArrayList<>();
				if (!s.departmentIds().isEmpty()) {
					or.add("r.department_id in (" + placeholders(s.departmentIds().size()) + ")");
				}
				if (!s.branchIds().isEmpty()) {
					or.add("r.branch_id in (" + placeholders(s.branchIds().size()) + ")");
				}
				sql.append(String.join(" or ", or));
				sql.append("))");
			}
			sql.append(")");
		}
		ReportingDtos.ReportFilter f = q.filter();
		if (f != null) {
			if (f.status() != null) sql.append(" and r.current_status_code = ?");
			if (f.departmentId() != null) sql.append(" and r.department_id = ?");
			if (f.branchId() != null) sql.append(" and r.branch_id = ?");
			if (f.categoryId() != null) sql.append(" and r.expense_category_id = ?");
			if (f.subcategoryId() != null) sql.append(" and r.expense_subcategory_id = ?");
			if (f.requesterId() != null) sql.append(" and r.requested_by = ?");
			if (f.expenseType() != null && !f.expenseType().isBlank())
				sql.append(" and coalesce(ec.expense_type,'OPEX')::text in (?, 'BOTH')");
			if (f.currency() != null && !f.currency().isBlank())
				sql.append(" and c.currency_code = ?");
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
		return String.join(",", Collections.nCopies(n, "?"));
	}

	private PreparedStatementSetter pss(Query q, int start) {
		return ps -> pss(q, ps, start);
	}

	/** Binds WHERE-clause parameters; returns the next parameter index. */
	int pss(Query q, PreparedStatement ps, int start) throws SQLException {
		int i = start;
		Scope s = q.scope();
		if (!s.installationWide()) {
			ps.setObject(i++, s.callerId());
			for (UUID id : s.departmentIds()) ps.setObject(i++, id);
			for (UUID id : s.branchIds())     ps.setObject(i++, id);
		}
		ReportingDtos.ReportFilter f = q.filter();
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

	/** Builds a trusted ORDER BY clause with a deterministic tiebreaker. */
	String orderByClause(Pageable p) {
		StringBuilder sb = new StringBuilder(" order by ");
		boolean any = false;
		if (p != null) {
			for (Sort.Order o : p.getSort()) {
				String expr = ALLOWED_SORT_COLUMNS.get(o.getProperty());
				if (expr == null) continue;
				if (any) sb.append(", ");
				sb.append(expr);
				sb.append(o.isDescending() ? " desc" : " asc");
				sb.append(" nulls last");
				any = true;
			}
		}
		if (!any) {
			sb.append(DEFAULT_SORT_COL).append(" desc nulls last");
		}
		sb.append(", r.id desc");
		return sb.toString();
	}

	private Pageable defaultExportSort() {
		return org.springframework.data.domain.PageRequest.of(0, EXPORT_ROW_LIMIT,
				Sort.by(Sort.Direction.DESC, "submittedAt"));
	}

	// -------------------------------------------------------------------------------------
	// Infrastructure
	// -------------------------------------------------------------------------------------

	private final JdbcTemplate jdbc;

	public ReportingJdbcRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}
}
