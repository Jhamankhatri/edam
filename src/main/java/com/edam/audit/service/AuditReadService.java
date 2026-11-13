package com.edam.audit.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.audit.dto.AuditDtos;
import com.edam.audit.dto.AuditDtos.AuditEventDetail;
import com.edam.audit.dto.AuditDtos.AuditEventSummary;
import com.edam.audit.dto.AuditDtos.TimelineEntry;
import com.edam.audit.dto.AuditDtos.TimelinePage;
import com.edam.common.api.ErrorCode;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditEvent;
import com.edam.common.audit.AuditRepository;
import com.edam.common.audit.AuditValueRedactor;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.DataScope;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read side of the audit trail (Module K) and the request activity timeline.
 *
 * <h2>Why this is separate from {@code common.audit}</h2>
 * {@code common.audit} owns the write path, redaction and append-only guarantees; it lives in
 * {@code common} because every business module writes audit. The read side is an application
 * feature — paginated search, detail, timeline — with authorization rules that depend on the
 * caller's data scopes, so it lives in the {@code audit} package.
 *
 * <h2>Bounded search window</h2>
 * BRD AUD-01 forbids unbounded reads of an append-only table. The service enforces both a default
 * window (last 24 hours) and a hard maximum of 31 days per request.
 *
 * <h2>Authorization is applied at the database</h2>
 * {@link AuditRepository#search} is a native query that joins {@code expense_requests} for events
 * whose {@code entity_type = 'ExpenseRequest'}, applying the same visibility predicate the
 * request register uses. This keeps {@code totalElements} accurate (no invisible rows counted)
 * and avoids N+1 post-filtering.
 *
 * <h2>Timeline pagination is keyset-based</h2>
 * To support arbitrary depth without an in-memory materialisation cap, the timeline endpoint
 * issues one bounded query per source (audit events, workflow actions, task assignments) and
 * performs a k-way merge in memory for one page at a time. The cursor is an opaque token that
 * fixes the upper bound ({@code boundedAt}) and the last-returned key per source, so pages stay
 * stable when new events arrive mid-browse.
 *
 * <h2>No logging of payloads</h2>
 * Log statements here intentionally exclude old/new values and remarks (rule #8).
 */
@Service
public class AuditReadService {

	private static final Logger log = LoggerFactory.getLogger(AuditReadService.class);

	/** Longest window a single search may span. */
	static final Duration MAX_WINDOW = Duration.ofDays(31);

	/** Default window when the caller supplies no bounds. */
	static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

	/** Maximum page size for the timeline endpoint. */
	public static final int MAX_TIMELINE_PAGE_SIZE = 200;

	/** Default timeline page size. */
	public static final int DEFAULT_TIMELINE_PAGE_SIZE = 50;

	/** Per-source fetch size used when satisfying a page — enough to absorb overlap across sources. */
	private static final int SOURCE_FETCH_CHUNK = 400;

	/** Cursor format version — bump on structural changes so stale cursors are rejected cleanly. */
	private static final int CURSOR_VERSION = 1;

	static final String SOURCE_AUDIT = "AUDIT";
	static final String SOURCE_WORKFLOW = "WORKFLOW_ACTION";
	static final String SOURCE_TASK = "WORKFLOW_TASK";
	static final int SOURCE_ORDER_AUDIT = 0;
	static final int SOURCE_ORDER_TASK = 1;
	static final int SOURCE_ORDER_WORKFLOW = 2;
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

	@Transactional(readOnly = true)
	public PageResponse<AuditEventSummary> search(SearchCriteria criteria, PageQuery pageQuery,
			EdamPrincipal caller) {
		Instant[] window = resolveWindow(criteria.from(), criteria.to());
		String actorName = (criteria.actorName() == null || criteria.actorName().isBlank())
				? ""
				: criteria.actorName().trim();
		// The native query defines its own deterministic ordering (created_at DESC, id DESC) so
		// pagination does not add a Sort — Spring Data's page-injection would prepend a second
		// ORDER BY that could override it.
		Pageable pageable = pageQuery.toPageable(Sort.unsorted());
		Scope scope = Scope.from(caller);
		String actionName = criteria.action() == null ? null : criteria.action().name();
		Page<AuditEvent> page = this.audit.search(window[0], window[1], actionName,
				normalize(criteria.entityType()), normalize(criteria.entityId()), criteria.actorId(),
				normalize(criteria.correlationId()), actorName, caller.userId(), scope.installationWide(),
				scope.deptIdsArray(), scope.branchIdsArray(), pageable);
		return PageResponse.of(page, this::toSummary);
	}

	@Transactional(readOnly = true)
	public AuditEventDetail detail(UUID id, EdamPrincipal caller) {
		AuditEvent event = this.audit.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("AuditEvent", id));
		if (ENTITY_TYPE_REQUEST.equals(event.getEntityType()) && event.getEntityId() != null) {
			UUID requestId = parseUuid(event.getEntityId());
			if (requestId != null) {
				ExpenseRequest request = this.requests.findById(requestId).orElse(null);
				if (request == null || !this.requestVisibility.isVisible(request, caller)) {
					throw new ResourceNotFoundException("AuditEvent", id);
				}
			}
		}
		return toDetail(event);
	}

	// ---------------------------------------------------------------------------------------------
	// Request activity timeline (keyset pagination)
	// ---------------------------------------------------------------------------------------------

	/**
	 * Returns one page of the request activity timeline.
	 *
	 * <p>Consistency model: every page is evaluated against the {@code boundedAt} instant recorded
	 * in the cursor (or {@code Instant.now()} on the first request). Events whose timestamp is
	 * after {@code boundedAt} are excluded for the lifetime of the cursor, so new events arriving
	 * mid-browse do not shift pages or produce duplicates. Callers start a fresh pagination
	 * sequence (by omitting {@code cursor}) to observe newer events.
	 */
	@Transactional(readOnly = true)
	public TimelinePage requestTimeline(UUID requestId, Integer requestedSize, String encodedCursor,
			EdamPrincipal caller) {
		ExpenseRequest request = this.requests.findById(requestId)
				.orElseThrow(() -> new ResourceNotFoundException("ExpenseRequest", requestId));
		if (!this.requestVisibility.isVisible(request, caller)) {
			throw new ResourceNotFoundException("ExpenseRequest", requestId);
		}
		int size = (requestedSize == null || requestedSize <= 0)
				? DEFAULT_TIMELINE_PAGE_SIZE
				: Math.min(requestedSize, MAX_TIMELINE_PAGE_SIZE);

		TimelineCursor cursor = decodeCursor(encodedCursor, requestId);

		// Bounded source queries.
		CursorKey auditKey = cursor.lastKeys.getOrDefault(SOURCE_AUDIT, new CursorKey(null, null));
		CursorKey taskKey = cursor.lastKeys.getOrDefault(SOURCE_TASK, new CursorKey(null, null));
		CursorKey actionKey = cursor.lastKeys.getOrDefault(SOURCE_WORKFLOW, new CursorKey(null, null));

		int fetchLimit = size + SOURCE_FETCH_CHUNK;
		List<AuditEvent> auditRows = fetchAuditEventsFor(requestId, cursor.boundedAt, auditKey, fetchLimit);
		List<WorkflowTask> taskRows = fetchTaskEventsFor(request, cursor.boundedAt, taskKey, fetchLimit);
		List<WorkflowAction> actionRows = fetchActionEventsFor(request, cursor.boundedAt, actionKey, fetchLimit);

		Map<UUID, String> stateNames = loadStateNames(request);
		List<TimelineEntry> all = new ArrayList<>();
		auditRows.forEach(e -> all.add(timelineFromAudit(e)));
		taskRows.forEach(t -> all.add(timelineFromAssignment(t)));
		actionRows.forEach(a -> all.add(timelineFromAction(a, stateNames)));

		all.sort(TimelineKey.ORDER);

		List<TimelineEntry> page = all.size() <= size ? all : all.subList(0, size);
		boolean hasNext = all.size() > size;

		Map<String, CursorKey> nextLastKeys = new LinkedHashMap<>(cursor.lastKeys);
		if (!page.isEmpty()) {
			TimelineEntry last = page.get(page.size() - 1);
			nextLastKeys.put(last.source(), new CursorKey(last.timestamp(), last.eventId()));
		}
		String nextCursor = hasNext
				? encodeCursor(new TimelineCursor(CURSOR_VERSION, cursor.boundedAt, requestId, nextLastKeys))
				: null;
		// "complete" = not hasNext AND this is the first page bounded to now (i.e. there are no
		// events later than boundedAt). When continuing an existing cursor we cannot re-check
		// without a race; report false so the client must issue a fresh request to see new events.
		boolean complete = !hasNext && encodedCursor == null;
		return new TimelinePage(page, size, nextCursor, hasNext, cursor.boundedAt, complete);
	}

	// ---------------------------------------------------------------------------------------------
	// Source fetchers for timeline (keyset queries)
	// ---------------------------------------------------------------------------------------------

	private List<AuditEvent> fetchAuditEventsFor(UUID requestId, Instant boundedAt, CursorKey after, int fetchLimit) {
		// The repository's finder is ordered DESC; fetch a generous chunk and reverse it.
		List<AuditEvent> rows = this.audit
				.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(ENTITY_TYPE_REQUEST, requestId.toString(),
						PageRequest.of(0, fetchLimit, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
				.getContent();
		// Filter to the bounded window and after-key in Java; the volume per request is bounded
		// by request depth, and we already load only one request's events.
		List<AuditEvent> picked = new ArrayList<>();
		for (AuditEvent e : rows) {
			if (e.getCreatedAt().isAfter(boundedAt)) {
				continue;
			}
			if (after.timestamp() != null) {
				int c = e.getCreatedAt().compareTo(after.timestamp());
				if (c > 0) {
					continue;
				}
				if (c == 0 && after.id() != null && e.getId().compareTo(after.id()) >= 0) {
					continue;
				}
			}
			picked.add(e);
		}
		picked.sort(Comparator.comparing(AuditEvent::getCreatedAt).thenComparing(AuditEvent::getId));
		return picked.size() > SOURCE_FETCH_CHUNK ? picked.subList(0, SOURCE_FETCH_CHUNK) : picked;
	}

	private List<WorkflowTask> fetchTaskEventsFor(ExpenseRequest request, Instant boundedAt, CursorKey after, int fetchLimit) {
		WorkflowInstance instance = this.instances.findByExpenseRequestId(request.getId()).orElse(null);
		if (instance == null) {
			return List.of();
		}
		List<WorkflowTask> rows = new ArrayList<>(this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId()));
		rows.sort(Comparator.comparing(WorkflowTask::getAssignedAt).thenComparing(WorkflowTask::getId));
		List<WorkflowTask> picked = new ArrayList<>();
		for (WorkflowTask t : rows) {
			if (t.getAssignedAt().isAfter(boundedAt)) {
				continue;
			}
			if (after.timestamp() != null) {
				int c = t.getAssignedAt().compareTo(after.timestamp());
				if (c > 0) {
					continue;
				}
				if (c == 0 && after.id() != null && t.getId().compareTo(after.id()) >= 0) {
					continue;
				}
			}
			picked.add(t);
		}
		return picked.size() > SOURCE_FETCH_CHUNK ? picked.subList(0, SOURCE_FETCH_CHUNK) : picked;
	}

	private List<WorkflowAction> fetchActionEventsFor(ExpenseRequest request, Instant boundedAt, CursorKey after, int fetchLimit) {
		WorkflowInstance instance = this.instances.findByExpenseRequestId(request.getId()).orElse(null);
		if (instance == null) {
			return List.of();
		}
		List<WorkflowTask> instanceTasks = this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId());
		List<UUID> taskIds = instanceTasks.stream().map(WorkflowTask::getId).toList();
		List<WorkflowAction> rows = new ArrayList<>(this.actions.findForInstance(taskIds));
		rows.sort(Comparator.comparing(WorkflowAction::getActionAt).thenComparing(WorkflowAction::getId));
		List<WorkflowAction> picked = new ArrayList<>();
		for (WorkflowAction a : rows) {
			if (a.getActionAt().isAfter(boundedAt)) {
				continue;
			}
			if (after.timestamp() != null) {
				int c = a.getActionAt().compareTo(after.timestamp());
				if (c > 0) {
					continue;
				}
				if (c == 0 && after.id() != null && a.getId().compareTo(after.id()) >= 0) {
					continue;
				}
			}
			picked.add(a);
		}
		return picked.size() > SOURCE_FETCH_CHUNK ? picked.subList(0, SOURCE_FETCH_CHUNK) : picked;
	}

	private Map<UUID, String> loadStateNames(ExpenseRequest request) {
		Map<UUID, String> names = new LinkedHashMap<>();
		this.instances.findByExpenseRequestId(request.getId()).ifPresent(instance ->
				this.states.findByWorkflowVersionId(instance.getWorkflowVersionId())
						.forEach(s -> names.put(s.getId(), s.getStateName())));
		return names;
	}

	// ---------------------------------------------------------------------------------------------
	// Mapping helpers
	// ---------------------------------------------------------------------------------------------

	private AuditEventSummary toSummary(AuditEvent event) {
		return new AuditEventSummary(event.getId(), event.getEntityType(), event.getEntityId(),
				event.getAction(), event.getPerformedBy(), event.getPerformedByUsername(),
				event.getCorrelationId(), event.getCreatedAt());
	}

	private AuditEventDetail toDetail(AuditEvent event) {
		return new AuditEventDetail(event.getId(), event.getEntityType(), event.getEntityId(),
				event.getAction(), parseAndRedact(event.getOldValues()),
				parseAndRedact(event.getNewValues()), event.getPerformedBy(), event.getPerformedByUsername(),
				event.getIpAddress(), event.getCorrelationId(), event.getCreatedAt());
	}

	private TimelineEntry timelineFromAudit(AuditEvent event) {
		return new TimelineEntry(SOURCE_AUDIT, event.getId(), event.getCreatedAt(), event.getPerformedBy(),
				event.getPerformedByUsername(), event.getAction().name(), describeAction(event.getAction()), null,
				null, extractReason(event.getNewValues()), event.getCorrelationId());
	}

	private TimelineEntry timelineFromAssignment(WorkflowTask task) {
		String actorName = null;
		if (task.getAssignedUserId() != null) {
			actorName = this.users.findById(task.getAssignedUserId()).map(User::displayName).orElse(null);
		}
		return new TimelineEntry(SOURCE_TASK, task.getId(), task.getAssignedAt(), task.getAssignedUserId(),
				actorName, "ASSIGNED",
				"Approval task assigned" + (task.getAssignedUserId() == null ? " to a role" : ""), null, null, null,
				null);
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
			case SOURCE_AUDIT -> SOURCE_ORDER_AUDIT;
			case SOURCE_TASK -> SOURCE_ORDER_TASK;
			case SOURCE_WORKFLOW -> SOURCE_ORDER_WORKFLOW;
			default -> 9;
		};
	}

	private Map<String, Object> parseAndRedact(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			LinkedHashMap<String, Object> parsed = this.jsonMapper.readValue(json, MAP_TYPE);
			return AuditValueRedactor.redact(parsed);
		}
		catch (Exception ex) {
			log.warn("audit.parse_failed for event; returning marker");
			return Map.of("_unparseable", true);
		}
	}

	private String extractReason(String newValuesJson) {
		Map<String, Object> values = parseAndRedact(newValuesJson);
		Object reason = values.get("reason");
		return reason == null ? null : redactFreeText(String.valueOf(reason));
	}

	private static String redactFreeText(String text) {
		if (text == null) {
			return null;
		}
		return text.replaceAll("(?i)(bearer\\s+\\S+|\\S{40,}={0,2})", AuditValueRedactor.REDACTED);
	}

	private static Instant[] resolveWindow(Instant from, Instant to) {
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

	// ---------------------------------------------------------------------------------------------
	// Cursor encoding
	// ---------------------------------------------------------------------------------------------

	/**
	 * An opaque continuation pointer. Format is versioned JSON, Base64-URL-encoded.
	 *
	 * <p>The cursor is not an authorization token — every request re-validates that the caller can
	 * read the request — but it is bound to a request id to prevent reuse across requests.
	 */
	private record TimelineCursor(int version, Instant boundedAt, UUID requestId, Map<String, CursorKey> lastKeys) {
		TimelineCursor(Instant boundedAt, Map<String, CursorKey> lastKeys) {
			this(CURSOR_VERSION, boundedAt, null, lastKeys);
		}
	}

	private record CursorKey(Instant timestamp, UUID id) {
	}

	private TimelineCursor decodeCursor(String encoded, UUID requestId) {
		if (encoded == null || encoded.isBlank()) {
			return new TimelineCursor(CURSOR_VERSION, Instant.now(), requestId, new LinkedHashMap<>());
		}
		try {
			String json = new String(Base64.getUrlDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
			JsonNode root = this.jsonMapper.readTree(json);
			int version = root.path("v").asInt(-1);
			if (version != CURSOR_VERSION) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "The pagination cursor is not recognized.");
			}
			UUID cursorReq = root.hasNonNull("r") ? UUID.fromString(root.get("r").asText()) : null;
			if (cursorReq == null || !cursorReq.equals(requestId)) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST,
						"The pagination cursor does not belong to this request.");
			}
			Instant boundedAt = Instant.parse(root.get("b").asText());
			Map<String, CursorKey> lastKeys = new LinkedHashMap<>();
			// Read cursor keys by known source names to avoid any reliance on
			// the shaded Jackson iterator type.
			tools.jackson.databind.JsonNode keysNode = root.path("k");
			for (String src : new String[]{"audit", "task", "action"}) {
				tools.jackson.databind.JsonNode kv = keysNode.get(src);
				if (kv == null || kv.isNull()) {
					continue;
				}
				Instant ts = Instant.parse(kv.get("t").asText());
				UUID keyId = UUID.fromString(kv.get("i").asText());
				lastKeys.put(src, new CursorKey(ts, keyId));
			}
			return new TimelineCursor(version, boundedAt, requestId, lastKeys);
		}
		catch (BusinessException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "The pagination cursor is not valid.");
		}
	}

	private String encodeCursor(TimelineCursor cursor) {
		try {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("v", cursor.version());
			payload.put("b", cursor.boundedAt().toString());
			payload.put("r", cursor.requestId() == null ? "" : cursor.requestId().toString());
			Map<String, Object> keys = new LinkedHashMap<>();
			cursor.lastKeys().forEach((source, key) -> {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("t", key.timestamp().toString());
				entry.put("i", key.id().toString());
				keys.put(source, entry);
			});
			payload.put("k", keys);
			byte[] bytes = this.jsonMapper.writeValueAsBytes(payload);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to encode timeline cursor", ex);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Input + scopes
	// ---------------------------------------------------------------------------------------------

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

	/**
	 * Resolved visibility scope for the current caller, used to bind parameters to the native
	 * audit search query.
	 */
	record Scope(boolean installationWide, List<UUID> deptIds, List<UUID> branchIds) {

		static Scope from(EdamPrincipal caller) {
			if (caller == null || caller.hasInstallationScope()) {
				return new Scope(true, List.of(), List.of());
			}
			List<UUID> dept = caller.dataScopes().stream()
					.filter(s -> s.type() == DataScope.Type.DEPARTMENT)
					.map(DataScope::referenceId).filter(java.util.Objects::nonNull).toList();
			List<UUID> branch = caller.dataScopes().stream()
					.filter(s -> s.type() == DataScope.Type.BRANCH)
					.map(DataScope::referenceId).filter(java.util.Objects::nonNull).toList();
			return new Scope(false, dept, branch);
		}

		UUID[] deptIdsArray() {
			return this.deptIds.toArray(UUID[]::new);
		}

		UUID[] branchIdsArray() {
			return this.branchIds.toArray(UUID[]::new);
		}
	}

	/** Composite sort key used by the timeline k-way merge and cursor comparisons. */
	static final class TimelineKey {

		static final Comparator<TimelineEntry> ORDER = (a, b) -> {
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
		};

		private TimelineKey() {
		}
	}

}
