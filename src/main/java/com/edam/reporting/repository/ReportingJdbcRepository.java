package com.edam.reporting.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

/**
 * Holds all native SQL and JDBC access for the reporting module. The reporting service
 * composes queries and interprets results, but it never touches JDBC directly —
 * {@code Controller → Service → Repository} layering holds project-wide.
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

	public void query(String sql, PreparedStatementSetter pss, RowCallbackHandler rch) {
		this.jdbc.query(sql, pss, rch);
	}

	/** Functional hook for populating bind variables; keeps binding close to query composition. */
	@FunctionalInterface
	public interface Binder {
		void bind(PreparedStatement ps) throws SQLException;
	}

	/** Functional hook for reading a single row of a result set. */
	@FunctionalInterface
	public interface RowReader<T> {
		T read(ResultSet rs) throws SQLException;
	}
}
