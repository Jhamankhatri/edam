package com.edam.expense;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One entry of a request's case history (BRD §8.4, AUD-02).
 *
 * <h2>Why a separate history table</h2>
 * The task table records the current state of each assignment. The case file an auditor reads is the
 * chronological record of <em>every</em> event, including ones that did not end in an approval
 * (submission, return for correction, resubmission, cancellation) and the state the request moved
 * from and to. Keeping it separate means history is append-only even though tasks are mutable while
 * pending.
 *
 * <p>Entries carry the correlation id of the request that caused them, so a history line can be tied
 * to the exact HTTP request and log record — the BRD requires audit records to be traceable, and
 * without the correlation id a "who did this" question ends at the username.
 */
@Entity
@Table(name = "request_history_entry")
public class RequestHistoryEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "institution_id", nullable = false, updatable = false)
	private Long institutionId;

	@Column(name = "request_id", nullable = false, updatable = false)
	private Long requestId;

	@Column(name = "sequence_number", nullable = false, updatable = false)
	private int sequenceNumber;

	@Column(name = "action", nullable = false, updatable = false, length = 32)
	private String action;

	@Column(name = "from_state", length = 32)
	private String fromState;

	@Column(name = "to_state", length = 32)
	private String toState;

	@Column(name = "actor_employee_id", length = 64)
	private String actorEmployeeId;

	@Column(name = "actor_username", length = 64)
	private String actorUsername;

	@Column(name = "comment", length = 2000)
	private String comment;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "correlation_id", length = 64)
	private String correlationId;

	protected RequestHistoryEntry() {
		// JPA
	}

	public RequestHistoryEntry(Long institutionId, Long requestId, int sequenceNumber, String action,
			String fromState, String toState, String actorEmployeeId, String actorUsername, String comment) {
		this.institutionId = institutionId;
		this.requestId = requestId;
		this.sequenceNumber = sequenceNumber;
		this.action = action;
		this.fromState = fromState;
		this.toState = toState;
		this.actorEmployeeId = actorEmployeeId;
		this.actorUsername = actorUsername;
		this.comment = comment;
		this.occurredAt = Instant.now();
		this.correlationId = com.edam.common.logging.CorrelationId.current();
	}

	public Long getId() {
		return this.id;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public int getSequenceNumber() {
		return this.sequenceNumber;
	}

	public String getAction() {
		return this.action;
	}

	public String getFromState() {
		return this.fromState;
	}

	public String getToState() {
		return this.toState;
	}

	public String getActorEmployeeId() {
		return this.actorEmployeeId;
	}

	public String getActorUsername() {
		return this.actorUsername;
	}

	public String getComment() {
		return this.comment;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}

}
