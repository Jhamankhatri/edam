package com.edam.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.audit.service.AuditReadService;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditEvent;
import com.edam.common.audit.AuditRepository;
import com.edam.common.audit.AuditValueRedactor;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.model.ExpenseNature;
import com.edam.expenseconfiguration.model.ExpenseRequestType;
import com.edam.expenseconfiguration.repository.ExpenseCategoryRepository;
import com.edam.expenseconfiguration.repository.ExpenseNatureRepository;
import com.edam.expenseconfiguration.repository.ExpenseRequestTypeRepository;
import com.edam.expenserequest.dto.ExpenseRequestDtos;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.expenserequest.service.ExpenseRequestService;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.organization.model.Department;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.organization.repository.OrganizationProfileRepository;
import com.edam.organization.repository.OrganizationUnitRepository;
import com.edam.organization.repository.OrganizationUnitTypeRepository;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;
import com.edam.testsupport.RequestFixtures;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;
import com.edam.workflow.model.WorkflowAction;
import com.edam.workflow.model.WorkflowInstance;
import com.edam.workflow.model.WorkflowTask;
import com.edam.workflow.repository.ActionsRepository;
import com.edam.workflow.repository.InstancesRepository;
import com.edam.workflow.repository.TasksRepository;
import com.edam.workflow.repository.VersionsRepository;
import com.edam.workflow.service.WorkflowConfigurationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-level tests for the audit search and request activity timeline endpoints.
 *
 * <p>Every test uses the real PostgreSQL container, real Flyway schema, real services. Tests cover
 * authorization, filtering, pagination, bounds validation, redaction, timeline ordering, and the
 * rule that reading audit does not itself write audit rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Audit read API")
class AuditReadApiTest extends AbstractPostgreSqlIntegrationTest {

	private static final String AUDIT_EVENTS = "/api/v1/audit-events";
	private static final String REQUESTS = "/api/v1/expense-requests";

	@Autowired private MockMvc mockMvc;
	@Autowired private AuditRepository auditRepository;
	@Autowired private ExpenseRequestRepository requestRepository;
	@Autowired private ExpenseRequestService requestService;
	@Autowired private WorkflowConfigurationService configuration;
	@Autowired private com.edam.workflow.service.ApprovalService approvalService;
	@Autowired private InstancesRepository instances;
	@Autowired private TasksRepository tasks;
	@Autowired private ActionsRepository actions;
	@Autowired private VersionsRepository versions;
	@Autowired private OrganizationProfileRepository profiles;
	@Autowired private CurrencyRepository currencies;
	@Autowired private DepartmentRepository departments;
	@Autowired private OrganizationUnitRepository units;
	@Autowired private OrganizationUnitTypeRepository unitTypes;
	@Autowired private ExpenseRequestTypeRepository requestTypes;
	@Autowired private ExpenseCategoryRepository categories;
	@Autowired private ExpenseNatureRepository natures;
	@Autowired private UserRepository users;

	private Department department;
	private Department otherDepartment;
	private User requester;
	private User approver;
	private User outsider;
	private User auditor;
	private Currency currency;
	private ExpenseRequestType requestType;
	private ExpenseCategory category;
	private ExpenseNature nature;

	@BeforeEach
	void setUp() {
		var profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.currency = this.currencies.findById(profile.getDefaultCurrencyId()).orElseThrow();
		this.department = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.otherDepartment = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.requester = RequestFixtures.user(this.users, this.department.getId());
		this.approver = RequestFixtures.user(this.users, this.department.getId());
		this.outsider = RequestFixtures.user(this.users, this.otherDepartment.getId());
		this.auditor = RequestFixtures.user(this.users, this.department.getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		this.requestType = RequestFixtures.requestType(this.requestTypes, "AUD-" + suffix);
		this.category = RequestFixtures.category(this.categories, "AUC-" + suffix, ExpenseCategory.ExpenseType.OPEX);
		this.nature = RequestFixtures.nature(this.natures, "AUN-" + suffix);
		RequestFixtures.publishedWorkflow(this.configuration, this.requestType, this.approver.getId(), "AUD-" + suffix);
	}

	// ---------------------------------------------------------------------------------------------
	// Principals
	// ---------------------------------------------------------------------------------------------

	private EdamPrincipal principal(User user, Set<DataScope> scopes, String... permissions) {
		return new EdamPrincipal(user.getId(), "EMP", user.getUsername(), user.displayName(), Set.of(),
				Set.of(permissions), scopes);
	}

	private EdamPrincipal auditor() {
		return principal(this.auditor,
				Set.of(new DataScope(DataScope.Type.ALL, null)), Permissions.AUDIT_VIEW);
	}

	private EdamPrincipal auditorNoScope() {
		// auditor but with no ALL scope and no department scope: holds audit:view globally because
		// audit search is installation-wide for auditors (the detail endpoint still enforces
		// per-request visibility).
		return principal(this.auditor, Set.of(), Permissions.AUDIT_VIEW);
	}

	private EdamPrincipal noAuditPermission() {
		return principal(this.requester,
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())),
				Permissions.REQUEST_VIEW_OWN);
	}

	private EdamPrincipal requesterPrincipal() {
		return principal(this.requester,
				Set.of(new DataScope(DataScope.Type.SELF, null),
						new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())),
				Permissions.REQUEST_CREATE, Permissions.REQUEST_VIEW_OWN, Permissions.REQUEST_SUBMIT,
				Permissions.REQUEST_EDIT, Permissions.REQUEST_CANCEL, Permissions.APPROVAL_VIEW);
	}

	private EdamPrincipal approverPrincipal() {
		return principal(this.approver,
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())),
				Permissions.REQUEST_VIEW_SCOPE, Permissions.APPROVAL_VIEW, Permissions.APPROVAL_ACT);
	}

	private EdamPrincipal outsiderPrincipal() {
		// REQUEST_VIEW_OWN is the code the request-list endpoint checks; scopes (SELF, department,
		// ALL) then decide which requests are visible. An out-of-department user still needs the
		// base code to reach the service at all — the 404 comes from isVisible, not from the aspect.
		return principal(this.outsider,
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.otherDepartment.getId())),
				Permissions.REQUEST_VIEW_OWN, Permissions.REQUEST_VIEW_SCOPE,
				Permissions.APPROVAL_VIEW, Permissions.APPROVAL_ACT);
	}

	// ---------------------------------------------------------------------------------------------
	// Fixture helpers
	// ---------------------------------------------------------------------------------------------

	private UUID createAndSubmitRequest() {
		ExpenseRequestDtos.RequestResponse created = this.requestService.create(
				new ExpenseRequestDtos.CreateRequest(
						this.requestType.getId(), this.category.getId(), null, this.nature.getId(),
						this.department.getId(), null, this.currency.getId(),
						RequestFixtures.money("1500.0000"), "Audit test",
						new ExpenseRequestDtos.OpexDetailPayload(true, "MONTHLY", null, null, null, null,
								null, null, null, null, null, null, null, null),
						null,
						List.of(new ExpenseRequestDtos.AllocationPayload(this.department.getId(), null, null,
								RequestFixtures.money("1500.0000"), null)),
						List.of(),
						List.of(new ExpenseRequestDtos.QuotationPayload(null, "Q-AUD-1",
								java.time.LocalDate.now(), RequestFixtures.money("1500.0000"),
								this.currency.getId(), null)),
						List.of(), List.of(), List.of(), List.of(), List.of()),
				requesterPrincipal());
		return this.requestService.submit(created.id(), requesterPrincipal()).id();
	}

	private long auditEventCount() {
		return this.auditRepository.count();
	}

	// ---------------------------------------------------------------------------------------------
	// Audit search
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("GET /api/v1/audit-events")
	class Search {

		@Test
		@DisplayName("unauthenticated request is refused")
		void unauthenticated() throws Exception {
			AuditReadApiTest.this.mockMvc.perform(get(AUDIT_EVENTS).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("authenticated caller without audit:view is forbidden")
		void forbiddenWithoutPermission() throws Exception {
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS).with(user(noAuditPermission())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("auditor can search without filters (default window)")
		void defaultWindow() throws Exception {
			long before = auditEventCount();
			createAndSubmitRequest(); // creates CREATE + SUBMIT events
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS).with(user(auditor())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.items").isArray())
					.andExpect(jsonPath("$.data.totalElements").isNumber());
			// Reading must not itself create audit rows.
			long after = auditEventCount();
			org.assertj.core.api.Assertions.assertThat(after - before).isGreaterThanOrEqualTo(2);
			long recount = auditEventCount();
			// Re-issue to prove a read does not add rows.
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS).with(user(auditor())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk());
			org.assertj.core.api.Assertions.assertThat(auditEventCount()).isEqualTo(recount);
		}

		@Test
		@DisplayName("search by actor id returns only their events")
		void filterByActor() throws Exception {
			createAndSubmitRequest();
			Instant now = Instant.now().plusSeconds(5);
			Instant from = now.minus(1, ChronoUnit.HOURS);
			String url = AUDIT_EVENTS + "?actorId={actorId}&from={from}&to={to}";
			String body = AuditReadApiTest.this.mockMvc
					.perform(get(url, AuditReadApiTest.this.requester.getId(), from, now)
							.with(user(auditor())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			// Every returned row must be by the filtered actor; and at least the CREATE/SUBMIT
			// events should match.
			com.jayway.jsonpath.DocumentContext parsed = com.jayway.jsonpath.JsonPath.parse(body);
			List<String> actors = parsed.read("$.data.items[*].performedBy");
			assertThat(actors).isNotEmpty();
			assertThat(actors).allMatch(id -> id.equals(AuditReadApiTest.this.requester.getId().toString()));
		}

		@Test
		@DisplayName("search by action filter")
		void filterByAction() throws Exception {
			createAndSubmitRequest();
			Instant now = Instant.now().plusSeconds(5);
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "?action=SUBMIT&from={from}&to={to}",
							now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS))
							.with(user(auditor())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items[0].action").value("SUBMIT"));
		}

		@Test
		@DisplayName("date range: from >= to is rejected")
		void invalidRange() throws Exception {
			Instant now = Instant.now().plusSeconds(5);
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "?from={to}&to={from}", now, now.minusSeconds(1))
							.with(user(auditor())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("date range exceeding 31 days is rejected")
		void rangeTooWide() throws Exception {
			Instant now = Instant.now().plusSeconds(5);
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "?from={from}&to={to}", now.minus(32, ChronoUnit.DAYS), now)
							.with(user(auditor())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("page size above MAX_SIZE is rejected with EDAM-1003")
		void pageSizeRejectedWhenOverMax() throws Exception {
			createAndSubmitRequest();
			Instant to = Instant.now().plusSeconds(5);
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "?size=500&from={from}&to={to}",
							to.minus(1, ChronoUnit.HOURS), to)
							.with(user(auditor())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("page size at MAX_SIZE is accepted")
		void pageSizeAtMaxIsAccepted() throws Exception {
			createAndSubmitRequest();
			Instant to = Instant.now().plusSeconds(5);
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "?size=200&from={from}&to={to}",
							to.minus(1, ChronoUnit.HOURS), to)
							.with(user(auditor())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.size").value(200));
		}

		@Test
		@DisplayName("malformed instant returns 400")
		void malformedDate() throws Exception {
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "?from=not-a-date").with(user(auditor()))
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("list rows do not expose old/new values or ip address")
		void listIsSummaryOnly() throws Exception {
			createAndSubmitRequest();
			Instant to = Instant.now().plusSeconds(5);
			String body = AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "?action=SUBMIT&from={from}&to={to}",
							to.minus(1, ChronoUnit.HOURS), to)
							.with(user(auditor())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			assertThat(body).doesNotContain("oldValues", "newValues", "ipAddress");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Audit detail
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("GET /api/v1/audit-events/{id}")
	class Detail {

		@Test
		@DisplayName("request-scoped event is not visible to an auditor who cannot see the request")
		void requestEventEnforcesVisibility() throws Exception {
			UUID requestId = createAndSubmitRequest();
			Instant to = Instant.now().plusSeconds(5);
			// Find the SUBMIT event id from search.
			String body = AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "?action=SUBMIT&entityType=ExpenseRequest&entityId={id}&from={from}&to={to}",
							requestId.toString(), to.minus(1, ChronoUnit.HOURS), to)
							.with(user(auditorNoScope())).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			// The list returns the event (installation-wide metadata) but detail must 404 because
			// the auditor has no request-scope.
			String eventId = com.jayway.jsonpath.JsonPath.read(body, "$.data.items[0].id");
			AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "/{id}", eventId).with(user(auditorNoScope()))
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("sensitive values are redacted in the diff")
		void redactsSensitiveValues() throws Exception {
			// Insert a synthetic audit event through the repository so we can guarantee the
			// payload contains a sensitive key.
			AuditEvent event = AuditEvent.builder(AuditAction.UPDATE, "ExpenseRequest")
					.entityId(UUID.randomUUID())
					.newValues("{\"password\":\"topsecret\",\"note\":\"hello\"}")
					.oldValues("{\"token\":\"abcd1234abcd1234abcd1234abcd1234abcd1234\"}")
					.build();
			AuditEvent saved = AuditReadApiTest.this.auditRepository.save(event);
			// Use an ALL-scoped auditor so they can see the non-existent "request" id — but
			// there's no request, so the visibility branch falls through to "not visible" and
			// returns 404. Use entityType=Role instead to avoid the request-visibility gate.
			AuditEvent nonRequest = AuditEvent.builder(AuditAction.UPDATE, "Role")
					.entityId(UUID.randomUUID())
					.newValues("{\"password\":\"topsecret\",\"name\":\"Auditor\"}")
					.build();
			AuditEvent savedRole = AuditReadApiTest.this.auditRepository.save(nonRequest);
			String body = AuditReadApiTest.this.mockMvc
					.perform(get(AUDIT_EVENTS + "/{id}", savedRole.getId()).with(user(auditor()))
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(body).contains(AuditValueRedactor.REDACTED);
			assertThat(body).doesNotContain("topsecret");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Activity timeline
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("GET /api/v1/expense-requests/{id}/activity")
	class Timeline {

		@Test
		@DisplayName("requester sees creation, submission, assignment, and approval in order")
		void mergedTimeline() throws Exception {
			UUID requestId = createAndSubmitRequest();
			WorkflowInstance instance = AuditReadApiTest.this.instances.findByExpenseRequestId(requestId).orElseThrow();
			WorkflowTask open = AuditReadApiTest.this.tasks
					.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId()).stream()
					.filter(t -> t.getStatus() == WorkflowTask.TaskStatus.PENDING).findFirst().orElseThrow();
			// Approve through the service to produce a real WorkflowAction row.
			AuditReadApiTest.this.approvalService.approve(open.getId(), "Looks good", approverPrincipal());

			String body = AuditReadApiTest.this.mockMvc
					.perform(get(REQUESTS + "/{id}/activity", requestId).with(user(requesterPrincipal()))
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andReturn().getResponse().getContentAsString();
			List<String> actions = com.jayway.jsonpath.JsonPath.read(body, "$.data.items[*].action");
			assertThat(actions).contains("CREATE", "SUBMIT", "ASSIGNED", "APPROVE");
			// Chronological order check.
			List<Long> timestamps = com.jayway.jsonpath.JsonPath.read(body, "$.data.items[*].timestamp");
			assertThat(timestamps).isSorted();
		}

		@Test
		@DisplayName("outsider from a different department cannot read the timeline")
		void outOfScopeIsNotFound() throws Exception {
			UUID requestId = createAndSubmitRequest();
			AuditReadApiTest.this.mockMvc
					.perform(get(REQUESTS + "/{id}/activity", requestId).with(user(outsiderPrincipal()))
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("unknown request returns 404")
		void unknown() throws Exception {
			AuditReadApiTest.this.mockMvc
					.perform(get(REQUESTS + "/{id}/activity", UUID.randomUUID()).with(user(requesterPrincipal()))
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("a read does not add new workflow actions or audit rows")
		void readDoesNotWrite() throws Exception {
			UUID requestId = createAndSubmitRequest();
			long auditsBefore = auditEventCount();
			long actionsBefore = AuditReadApiTest.this.actions.count();
			AuditReadApiTest.this.mockMvc
					.perform(get(REQUESTS + "/{id}/activity", requestId).with(user(requesterPrincipal()))
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk());
			assertThat(auditEventCount()).isEqualTo(auditsBefore);
			assertThat(AuditReadApiTest.this.actions.count()).isEqualTo(actionsBefore);
		}
	}

}
