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
