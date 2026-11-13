package com.edam.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.common.audit.AuditAction;

/**
 * Public contracts for the audit read API (Module K).
 *
 * <p>These are deliberately shaped for the React client: stable field names, no lazy-loaded entities, no
 * JSONB column surfaced raw. {@link com.edam.common.audit.AuditValueRedactor} is re-applied to old/new
 * values on output so any future write path that forgets redaction cannot leak a secret through the read
 * API — defence in depth.
 */
public final class AuditDtos {

	private AuditDtos() {
	}

	/** Summary row returned by the search endpoint. Does not carry old/new values. */
	public record AuditEventSummary(
			UUID id,
			String entityType,
			String entityId,
			AuditAction action,
			UUID performedBy,
			String performedByUsername,
			String correlationId,
			Instant createdAt) {
	}

	/** Full event detail. */
	public record AuditEventDetail(
			UUID id,
			String entityType,
			String entityId,
			AuditAction action,
			Map<String, Object> oldValues,
			Map<String, Object> newValues,
			UUID performedBy,
			String performedByUsername,
			String ipAddress,
			String correlationId,
			Instant createdAt) {
	}

	/** One entry on a request activity timeline, merged from audit and workflow sources. */
	public record TimelineEntry(
			String source,
			UUID eventId,
			Instant timestamp,
			UUID actorId,
			String actorName,
			String action,
			String description,
			String previousState,
			String newState,
			String remarks,
			String correlationId) {
	}

	/**
	 * A single page of a request timeline, returned with an opaque continuation cursor.
	 *
	 * <p>The cursor encodes a stable upper bound ({@code boundedAt}) that fixes the query window
	 * for subsequent pages, plus the last-returned key per source so paging is not disrupted by
	 * new events arriving between requests. New events after {@code boundedAt} are excluded from
	 * the whole pagination sequence; clients must start a new timeline request (without a cursor)
	 * to see them.
	 */
	public record TimelinePage(
			List<TimelineEntry> items,
			int size,
			String nextCursor,
			boolean hasNext,
			Instant boundedAt,
			boolean complete) {
	}

}
