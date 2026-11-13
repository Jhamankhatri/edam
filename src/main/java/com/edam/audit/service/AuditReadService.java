package com.edam.audit.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.edam.audit.dto.AuditDtos.AuditEventDetail;
import com.edam.audit.dto.AuditDtos.AuditEventSummary;
import com.edam.audit.dto.AuditDtos.TimelineEntry;
import com.edam.common.api.ErrorCode;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditEvent;
import com.edam.common.audit.AuditRepository;
import com.edam.common.audit.AuditValueRedactor;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.EdamPrincipal;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.expenserequest.service.ExpenseRequestService;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;
import com.edam.workflow.model.WorkflowAction;
import com.edam.workflow.model.WorkflowInstance;
import com.edam.workflow.model.WorkflowTask;
import com.edam.workflow.repository.ActionsRepository;
import com.edam.workflow.repository.InstancesRepository;
import com.edam.workflow.repository.StatesRepository;
import com.edam.workflow.repository.TasksRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read side of the audit trail (Module K) and the request activity timeline.
 *
 * <h2>Why this is separate from {@code common.audit}</h2>
 * {@code common.audit} owns the write path, redaction and append-only guarantees; it lives in
 * {@code common} because every business module writes audit. The read side is an application
 * feature — paginated search, detail, timeline — with its own authorization rules. Separating
 * them keeps the write surface narrow and means no controller needs to depend on internal
 * builder methods.
 *
 * <h2>Bounded search window</h2>
 * BRD AUD-01 forbids unbounded reads of an append-only table. The service enforces both a
 * default window (last 24 hours) and a hard maximum of 31 days per request. Clients can page
 * through history explicitly; a single request cannot scan the entire table.
 *
 * <h2>No logging of payloads</h2>
 * Rule #8 of the task is "do not log audit search results or sensitive audit payloads". Log
 * statements here intentionally exclude old/new values and remarks.
 */
@Service
public class AuditReadService {

	private static final Logger log = LoggerFactory.getLogger(AuditReadService.class);

	/** Longest window a single search may span. */
	static final Duration MAX_WINDOW = Duration.ofDays(31);

	/** Default window when the caller supplies no bounds. */
	static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

	/**
	 * Hard cap on timeline materialisation. A single request's audit+workflow history is bounded
	 * by workflow depth and number of updates, but the cap guarantees a pathological request
	 * (tens of thousands of edits) cannot exhaust memory.
	 */
	static final int MAX_TIMELINE_ENTRIES = 2000;

	/** Event source tags for timeline entries (visible in the DTO, not persisted). */
	static final String SOURCE_AUDIT = "AUDIT";
	static final String SOURCE_WORKFLOW = "WORKFLOW_ACTION";
	static final String SOURCE_TASK = "WORKFLOW_TASK";

	/** Entity type that audit uses for expense request records. */
	static final String ENTITY_TYPE_REQUEST = "ExpenseRequest";

	private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
			new TypeReference<>() {
			};

	private final AuditRepository audit;
	private final ExpenseRequestRepository requests;
	private final ExpenseRequestService requestVisibility;
	private final InstancesRepository instances;
	private final TasksRepository tasks;
	private final ActionsRepository actions;
	private final StatesRepository states;
	private final UserRepository users;
	private final JsonMapper jsonMapper;

	public AuditReadService(AuditRepository audit, ExpenseRequestRepository requests,
			ExpenseRequestService requestVisibility, InstancesRepository instances, TasksRepository tasks,
			ActionsRepository actions, StatesRepository states, UserRepository users, JsonMapper jsonMapper) {
		this.audit = audit;
		this.requests = requests;
		this.requestVisibility = requestVisibility;
		this.instances = instances;
		this.tasks = tasks;
		this.actions = actions;
		this.states = states;
		this.users = users;
		this.jsonMapper = jsonMapper;
	}

	// ---------------------------------------------------------------------------------------------
	// Search
	// ---------------------------------------------------------------------------------------------

	/**
	 * Paginated audit search.
	 *
	 * <p>The caller must already hold {@code audit:view}. This method does not re-check that
	 * permission — the aspect does — but it does enforce window bounds and never loads payload
	 * columns in the list query.
	 */
	@Transactional(readOnly = true)
	public PageResponse<AuditEventSummary> search(SearchCriteria criteria, PageQuery pageQuery,
			EdamPrincipal caller) {
		Instant[] window = resolveWindow(criteria.from(), criteria.to());
		String actorName = (criteria.actorName() == null || criteria.actorName().isBlank())
				? ""
				: criteria.actorName().trim();
		Pageable pageable = pageQuery.toPageable(Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<AuditEvent> page = this.audit.search(window[0], window[1], criteria.action(),
				normalize(criteria.entityType()), normalize(criteria.entityId()), criteria.actorId(),
				normalize(criteria.correlationId()), actorName, pageable);
		return PageResponse.of(page, this::toSummary);
	}

	/** Single event detail. Enforces request-level visibility for request events. */
	@Transactional(readOnly = true)
	public AuditEventDetail detail(UUID id, EdamPrincipal caller) {
		AuditEvent event = this.audit.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("AuditEvent", id));
		if (ENTITY_TYPE_REQUEST.equals(event.getEntityType()) && event.getEntityId() != null) {
			// The auditor must also be able to see the request to read the request's detail diff.
			// A bare audit:view lets an auditor browse the register but not read another user's
			// request diff (rule #2).
			UUID requestId = parseUuid(event.getEntityId());
			if (requestId != null) {
				ExpenseRequest request = this.requests.findById(requestId).orElse(null);
				if (request == null || !this.requestVisibility.isVisible(request, caller)) {
					// 404 rather than 403: mirrors the existing request controller.
					throw new ResourceNotFoundException("AuditEvent", id);
				}
			}
		}
		return toDetail(event);
	}

	// ---------------------------------------------------------------------------------------------
	// Request activity timeline
	// ---------------------------------------------------------------------------------------------

	/**
	 * Activity timeline for a request, merged from audit events, workflow actions, and task assignments.
	 *
	 * <p>The caller must already be authorized to read the request. The service re-validates that via
	 * {@link ExpenseRequestService#isVisible} so the rule is enforced in the service layer, not only
	 * in the controller.
	 */
	@Transactional(readOnly = true)
	public PageResponse<TimelineEntry> requestTimeline(UUID requestId, PageQuery pageQuery,
			EdamPrincipal caller) {
		ExpenseRequest request = this.requests.findById(requestId)
				.orElseThrow(() -> new ResourceNotFoundException("ExpenseRequest", requestId));
		if (!this.requestVisibility.isVisible(request, caller)) {
			throw new ResourceNotFoundException("ExpenseRequest", requestId);
		}

		List<TimelineEntry> entries = new ArrayList<>();
		// 1. Audit events for the request.
		for (AuditEvent event : this.audit
				.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(ENTITY_TYPE_REQUEST,
						requestId.toString(), Pageable.unpaged())) {
			entries.add(timelineFromAudit(event));
		}

		// 2. Workflow actions and task assignments for the current (or only) workflow instance.
		this.instances.findByExpenseRequestId(requestId).ifPresent(instance ->
				appendWorkflowEntries(entries, instance));

		if (entries.size() > MAX_TIMELINE_ENTRIES) {
			// Truncate deterministically by recency rather than failing the read. The paged
			// response still reports the capped total so the UI can surface the cap.
			entries.sort((a, b) -> {
				int c = b.timestamp().compareTo(a.timestamp());
				if (c != 0) {
					return c;
				}
				return Integer.compare(sourceOrder(b.source()), sourceOrder(a.source()));
			});
			List<TimelineEntry> recent = new ArrayList<>(entries.subList(0, MAX_TIMELINE_ENTRIES));
			entries.clear();
			entries.addAll(recent);
		}

		// Deterministic ordering: (timestamp ASC, source_sequence ASC, eventId ASC). The source
		// sequence guarantees stable ordering even when two entries share a timestamp, and keeps
		// audit events ahead of workflow events when they describe the same submit (so "submitted"
		// appears before the resulting assignment, which is the order a reader expects).
		entries.sort((a, b) -> {
			int c = a.timestamp().compareTo(b.timestamp());
			if (c != 0) {
				return c;
			}
			c = Integer.compare(sourceOrder(a.source()), sourceOrder(b.source()));
			if (c != 0) {
				return c;
			}
			String aid = a.eventId() == null ? "" : a.eventId().toString();
			String bid = b.eventId() == null ? "" : b.eventId().toString();
			return aid.compareTo(bid);
		});

		return paginateEntries(entries, pageQuery);
	}

	// ---------------------------------------------------------------------------------------------
	// Internals
	// ---------------------------------------------------------------------------------------------

	private void appendWorkflowEntries(List<TimelineEntry> entries, WorkflowInstance instance) {
		// Task assignments come first (earliest), then workflow actions in their action_at order.
		Map<UUID, String> stateNames = new LinkedHashMap<>();
		this.states.findByWorkflowVersionId(instance.getWorkflowVersionId())
				.forEach(state -> stateNames.put(state.getId(), state.getStateName()));
		List<WorkflowTask> instanceTasks = this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId());
		for (WorkflowTask task : instanceTasks) {
			entries.add(timelineFromAssignment(task));
		}
		List<UUID> taskIds = instanceTasks.stream().map(WorkflowTask::getId).toList();
		for (WorkflowAction action : this.actions.findForInstance(taskIds)) {
			entries.add(timelineFromAction(action, stateNames));
		}
	}

	private TimelineEntry timelineFromAudit(AuditEvent event) {
		String description = describeAction(event.getAction());
		return new TimelineEntry(SOURCE_AUDIT, event.getId(), event.getCreatedAt(), event.getPerformedBy(),
				event.getPerformedByUsername(), event.getAction().name(), description, null, null,
				extractReason(event.getNewValues()), event.getCorrelationId());
	}

	private TimelineEntry timelineFromAssignment(WorkflowTask task) {
		String actorName = null;
		if (task.getAssignedUserId() != null) {
			actorName = this.users.findById(task.getAssignedUserId()).map(User::displayName).orElse(null);
		}
		return new TimelineEntry(SOURCE_TASK, task.getId(), task.getAssignedAt(), task.getAssignedUserId(),
				actorName, "ASSIGNED",
				"Approval task assigned" + (task.getAssignedUserId() == null ? " to a role" : ""), null,
				null, null, null);
	}

	private TimelineEntry timelineFromAction(WorkflowAction action, Map<UUID, String> stateNames) {
		UUID actorId = action.getPerformedBy();
		String actorName = actorId == null ? null
				: this.users.findById(actorId).map(User::displayName).orElse(null);
		String previous = action.getPreviousStateId() == null ? null
				: stateNames.getOrDefault(action.getPreviousStateId(), null);
		String next = action.getNextStateId() == null ? null
				: stateNames.getOrDefault(action.getNextStateId(), null);
		String remarks = redactFreeText(action.getRemarks());
		return new TimelineEntry(SOURCE_WORKFLOW, action.getId(), action.getActionAt(), actorId, actorName,
				action.getActionCode().name(), describeWorkflowAction(action.getActionCode()), previous, next,
				remarks, action.getCorrelationId());
	}

	private static String describeAction(AuditAction action) {
		return switch (action) {
			case CREATE -> "Request created";
			case UPDATE -> "Request updated";
			case SUBMIT -> "Request submitted for approval";
			case CANCEL -> "Request cancelled";
			case APPROVE -> "Request approved";
			case REJECT -> "Request rejected";
			case REVERT -> "Request returned for correction";
			case FORWARD -> "Approval forwarded";
			case ACTIVATE -> "Activated";
			case DEACTIVATE -> "Deactivated";
			case PUBLISH -> "Published";
			case ROLLBACK -> "Rolled back";
			case DELETE -> "Deleted";
			case DOCUMENT_UPLOAD -> "Document uploaded";
			case DOCUMENT_DOWNLOAD -> "Document downloaded";
			case DOCUMENT_DELETE -> "Document deleted";
			case SECRET_ROTATE -> "Secret rotated";
			case EXPORT -> "Data exported";
			case LOGIN_SUCCESS -> "Sign-in succeeded";
			case LOGIN_FAILURE -> "Sign-in failed";
			case ACCESS_DENIED -> "Access denied";
		};
	}

	private static String describeWorkflowAction(WorkflowAction.ActionCode code) {
		return switch (code) {
			case APPROVE -> "Approved";
			case REJECT -> "Rejected";
			case REVERT -> "Returned for correction";
			case FORWARD -> "Forwarded";
			case ESCALATE -> "Escalated";
		};
	}

	private static int sourceOrder(String source) {
		return switch (source) {
			case SOURCE_AUDIT -> 0;
			case SOURCE_TASK -> 1;
			case SOURCE_WORKFLOW -> 2;
			default -> 9;
		};
	}

	private AuditEventSummary toSummary(AuditEvent event) {
		// Summary deliberately excludes old/new/IP: list rows are for triage.
		return new AuditEventSummary(event.getId(), event.getEntityType(), event.getEntityId(),
				event.getAction(), event.getPerformedBy(), event.getPerformedByUsername(),
				event.getCorrelationId(), event.getCreatedAt());
	}

	private AuditEventDetail toDetail(AuditEvent event) {
		return new AuditEventDetail(event.getId(), event.getEntityType(), event.getEntityId(),
				event.getAction(), parseAndRedact(event.getOldValues()),
				parseAndRedact(event.getNewValues()), event.getPerformedBy(), event.getPerformedByUsername(),
				// ipAddress is part of BRD §26 "context"; we return it only to auditors (who hold
				// audit:view). It is already column-level, not a secret, and useful for incident
				// investigation.
				event.getIpAddress(), event.getCorrelationId(), event.getCreatedAt());
	}

	/** Defensive second pass over the persisted JSON in case a future write path forgets redaction. */
	private Map<String, Object> parseAndRedact(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			LinkedHashMap<String, Object> parsed = this.jsonMapper.readValue(json, MAP_TYPE);
			return AuditValueRedactor.redact(parsed);
		}
		catch (Exception ex) {
			// If a stored payload is unreadable for any reason, surface a marker rather than the
			// raw string — the raw string could contain anything.
			log.warn("audit.parse_failed for event; returning marker");
			return Map.of("_unparseable", true);
		}
	}

	private String extractReason(String newValuesJson) {
		Map<String, Object> values = parseAndRedact(newValuesJson);
		Object reason = values.get("reason");
		return reason == null ? null : redactFreeText(String.valueOf(reason));
	}

	/** Redact suspicious-value-shaped strings in free-text fields (e.g. remarks). */
	private static String redactFreeText(String text) {
		if (text == null) {
			return null;
		}
		return text.replaceAll("(?i)(bearer\\s+\\S+|\\S{40,}={0,2})",
				AuditValueRedactor.REDACTED);
	}

	private Instant[] resolveWindow(Instant from, Instant to) {
		Instant end = (to == null) ? Instant.now() : to;
		Instant start = from;
		if (start == null) {
			start = end.minus(DEFAULT_WINDOW);
		}
		if (!start.isBefore(end)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The date range 'from' must be before 'to'.");
		}
		if (Duration.between(start, end).compareTo(MAX_WINDOW) > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The date range must not exceed " + MAX_WINDOW.toDays() + " days.");
		}
		return new Instant[] { start, end };
	}

	private PageResponse<TimelineEntry> paginateEntries(List<TimelineEntry> entries, PageQuery pageQuery) {
		Pageable pageable = pageQuery.toPageable(Sort.unsorted());
		// Timeline is naturally ordered above; apply pagination in-memory because a single request's
		// timeline is bounded (one request ⇒ a handful of events; bounded by workflow depth and
		// updates) and a DB Page over an in-memory merge would be more complex and no safer.
		int size = pageable.getPageSize();
		int page = pageable.getPageNumber();
		int fromIndex = Math.min(page * size, entries.size());
		int toIndex = Math.min(fromIndex + size, entries.size());
		List<TimelineEntry> slice = entries.subList(fromIndex, toIndex);
		int totalPages = size == 0 ? 0 : (entries.size() + size - 1) / size;
		return new PageResponse<>(slice, page, size, entries.size(), totalPages, toIndex < entries.size(),
				page > 0);
	}

	private static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private static UUID parseUuid(String value) {
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(value.trim());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Input for {@link #search}. Every filter is optional; the window defaults to the last 24
	 * hours and is capped at {@link #MAX_WINDOW}.
	 */
	public record SearchCriteria(
			Instant from,
			Instant to,
			UUID actorId,
			String actorName,
			String entityType,
			String entityId,
			AuditAction action,
			String correlationId) {
	}

}
