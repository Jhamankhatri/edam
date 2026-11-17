package com.edam.reporting.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

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
 * <p>This class intentionally exposes the same narrow set of {@code query(...)} overloads that
 * {@link JdbcTemplate} does; ReportingService composes queries and interprets results but
 * never talks to JDBC directly.
 */
@Repository
public class ReportingJdbcRepository {

	/** Default ORDER BY column for register queries. */
	public static final String DEFAULT_SORT_COL = "r.submitted_at";

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

	private final JdbcTemplate jdbc;

	public ReportingJdbcRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

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
}
