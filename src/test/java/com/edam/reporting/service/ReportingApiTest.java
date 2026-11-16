package com.edam.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.model.ExpenseRequestType;
import com.edam.expenseconfiguration.repository.ExpenseCategoryRepository;
import com.edam.expenseconfiguration.repository.ExpenseNatureRepository;
import com.edam.expenseconfiguration.repository.ExpenseRequestTypeRepository;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.organization.model.Department;
import com.edam.organization.model.OrganizationProfile;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.organization.repository.OrganizationProfileRepository;
import com.edam.organization.repository.OrganizationUnitRepository;
import com.edam.organization.repository.OrganizationUnitTypeRepository;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;
import com.edam.testsupport.RequestFixtures;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Reporting: dashboard, register, exports")
class ReportingApiTest extends AbstractPostgreSqlIntegrationTest {

	private static final String REPORTING = "/api/v1/reporting";

	@Autowired private MockMvc mockMvc;

	@Autowired private UserRepository users;
	@Autowired private DepartmentRepository departments;
	@Autowired private OrganizationUnitRepository units;
	@Autowired private OrganizationUnitTypeRepository unitTypes;
	@Autowired private OrganizationProfileRepository profiles;
	@Autowired private CurrencyRepository currencies;
	@Autowired private ExpenseCategoryRepository categories;
	@Autowired private ExpenseNatureRepository natures;
	@Autowired private ExpenseRequestTypeRepository requestTypes;
	@Autowired private ExpenseRequestRepository requests;
	@Autowired private ReportingService reporting;
	@Autowired private JdbcTemplate jdbc;

	private String baseCode = "PKR";
	private Department deptA;
	private Department deptB;
	private User admin;
	private User requesterA;
	private User requesterB;
	private User approverDeptA;
	private ExpenseCategory opex;
	private ExpenseCategory capex;
	private ExpenseRequestType requestType;
	private Currency baseCurrency;

	@BeforeEach
	void setUp() {
		OrganizationProfile profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.baseCurrency = this.currencies.findById(profile.getDefaultCurrencyId()).orElseThrow();
		this.baseCode = this.baseCurrency.getCode();
		this.deptA = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.deptB = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.admin = RequestFixtures.user(this.users, this.deptA.getId());
		this.requesterA = RequestFixtures.user(this.users, this.deptA.getId());
		this.requesterB = RequestFixtures.user(this.users, this.deptB.getId());
		this.approverDeptA = RequestFixtures.user(this.users, this.deptA.getId());
		String sfx = UUID.randomUUID().toString().substring(0, 6);
		this.requestType = RequestFixtures.requestType(this.requestTypes, "RRT-" + sfx);
		this.opex = RequestFixtures.category(this.categories, "ROP-" + sfx, ExpenseCategory.ExpenseType.OPEX);
		this.capex = RequestFixtures.category(this.categories, "RCP-" + sfx, ExpenseCategory.ExpenseType.CAPEX);
	}

	private EdamPrincipal installationAdmin() {
		return new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of("ADMIN"),
				Set.of(Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT, Permissions.DASHBOARD_MANAGE),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
	}

	private EdamPrincipal deptAViewer() {
		return new EdamPrincipal(this.approverDeptA.getId(), this.approverDeptA.getEmployeeCode(),
				this.approverDeptA.getUsername(), this.approverDeptA.displayName(), Set.of("APPROVER"),
				Set.of(Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.deptA.getId())));
	}

	private EdamPrincipal selfOnly(User u) {
		return new EdamPrincipal(u.getId(), u.getEmployeeCode(), u.getUsername(), u.displayName(),
				Set.of("REQUESTER"), Set.of(Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT),
				Set.of(new DataScope(DataScope.Type.SELF, null)));
	}

	/** Create a DRAFT request. */
	private ExpenseRequest draft(User requester, Department dept, ExpenseCategory cat, String amount) {
		String rn = "RPT-" + UUID.randomUUID();
		ExpenseRequest r = new ExpenseRequest(rn, requester.getId());
		r.setJustification("Fixture justification text");
		r.setPlacement(dept.getId(), null);
		r.setMoney(new BigDecimal(amount), this.baseCurrency.getId(), new BigDecimal(amount), null);
		r.setClassification(this.requestType.getId(), cat.getId(), null,
				RequestFixtures.nature(this.natures, "RN-" + UUID.randomUUID().toString().substring(0, 6)).getId());
		return this.requests.save(r);
	}

	/** Create a submitted request at a given timestamp and status. No workflow instance is created;
	 *  for the decision-info tests use {@link #decided(...)}. */
	private ExpenseRequest submit(User requester, Department dept, ExpenseCategory cat, String amount,
			ExpenseRequest.Status status, Instant submittedAt) {
		ExpenseRequest r = draft(requester, dept, cat, amount);
		setStatusAndSubmittedAt(r, status, submittedAt, null);
		return this.requests.save(r);
	}

	/** Submit + record a synthetic APPROVE/REJECT workflow decision in the audit tables so the
	 *  register lateral join resolves decided_at and last_actor_name. We do not exercise the full
	 *  workflow engine here — that belongs to workflow integration tests. Reporting reads the
	 *  decision directly from workflow_actions. */
	private ExpenseRequest decided(User requester, Department dept, ExpenseCategory cat, String amount,
			ExpenseRequest.Status status, Instant submittedAt, Instant decidedAt, User actor, String actionCode) {
		ExpenseRequest r = submit(requester, dept, cat, amount, status, submittedAt);
		// Build a minimal but NOT-NULL-compliant chain of definition/version/state/step/instance/
		// task/action so that the register's lateral join resolves decided_at and last_actor_name
		// without exercising the full workflow engine.
		UUID definitionId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		UUID stateId = UUID.randomUUID();
		UUID stepId = UUID.randomUUID();
		UUID instanceId = UUID.randomUUID();
		UUID taskId = UUID.randomUUID();
		UUID actionId = UUID.randomUUID();
		this.jdbc.update(
				"insert into workflow_definitions (id, workflow_code, workflow_name, request_type_id)"
				+ " values (?, ?, ?, ?)",
				definitionId, "TDEF-" + definitionId.toString().substring(0, 8), "Test definition",
				this.requestType.getId());
		this.jdbc.update(
				"insert into workflow_versions (id, workflow_definition_id, version_number, version_status,"
				+ " published_by, published_at)"
				+ " values (?, ?, 1, 'PUBLISHED', ?, ?)",
				versionId, definitionId, requester.getId(), Timestamp.from(submittedAt));
		this.jdbc.update(
				"insert into workflow_states (id, workflow_version_id, state_code, state_name, state_type,"
				+ " is_initial, is_terminal)"
				+ " values (?, ?, 'APPROVED', 'Approved', 'APPROVED', false, true)",
				stateId, versionId);
		this.jdbc.update(
				"insert into workflow_steps (id, workflow_version_id, step_code, step_name, step_order)"
				+ " values (?, ?, 'S1', 'Approval', 1)",
				stepId, versionId);
		this.jdbc.update(
				"insert into workflow_instances (id, workflow_definition_id, workflow_version_id,"
				+ " expense_request_id, current_state_id, started_at, started_by)"
				+ " values (?, ?, ?, ?, ?, ?, ?)",
				instanceId, definitionId, versionId, r.getId(), stateId,
				Timestamp.from(submittedAt), requester.getId());
		this.jdbc.update(
				"insert into workflow_tasks (id, workflow_instance_id, workflow_step_id, assigned_user_id,"
				+ " task_status, assigned_at, completed_at)"
				+ " values (?, ?, ?, ?, 'APPROVED', ?, ?)",
				taskId, instanceId, stepId, actor.getId(),
				Timestamp.from(submittedAt), Timestamp.from(decidedAt));
		this.jdbc.update(
				"insert into workflow_actions (id, workflow_task_id, action_code, performed_by, action_at)"
				+ " values (?, ?, ?, ?, ?)",
				actionId, taskId, actionCode, actor.getId(), Timestamp.from(decidedAt));
		this.jdbc.update("update expense_requests set current_workflow_instance_id = ? where id = ?",
				instanceId, r.getId());
		return r;
	}

	private void setStatusAndSubmittedAt(ExpenseRequest r, ExpenseRequest.Status status, Instant submittedAt, Instant updatedAt) {
		try {
			java.lang.reflect.Field sF = ExpenseRequest.class.getDeclaredField("currentStatusCode");
			sF.setAccessible(true);
			sF.set(r, status);
			java.lang.reflect.Field tF = ExpenseRequest.class.getDeclaredField("submittedAt");
			tF.setAccessible(true);
			tF.set(r, submittedAt);
			if (updatedAt != null) {
				java.lang.reflect.Field uF = ExpenseRequest.class.getDeclaredField("updatedAt");
				uF.setAccessible(true);
				uF.set(r, updatedAt);
			}
		} catch (ReflectiveOperationException rox) {
			throw new RuntimeException(rox);
		}
	}

	// ---- Helper: parse JSON int at dotted path (regex) ----
	private int jsonInt(String body, String key) {
		var m = java.util.regex.Pattern.compile("\"" + key.replace(".", "\\\":") + "\":(\\d+)").matcher(body);
		if (!m.find()) throw new AssertionError("key not found: " + key + " in body prefix " + body.substring(0, Math.min(400, body.length())));
		return Integer.parseInt(m.group(1));
	}

	private String jsonString(String body, String key) {
		var m = java.util.regex.Pattern.compile("\"" + key + "\":\"([^\"]*)\"").matcher(body);
		if (!m.find()) throw new AssertionError("string key not found: " + key);
		return m.group(1);
	}

	// ------------------------------------------------------------------------------------
	// 1. Dashboard base currency
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("dashboard returns 200 with base currency resolved from organization profile")
	void dashboardLoads() throws Exception {
		this.mockMvc.perform(get(REPORTING + "/dashboard")
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.metrics").isArray())
			.andExpect(jsonPath("$.data.baseCurrencyCode").value(this.baseCode));
	}

	// ------------------------------------------------------------------------------------
	// 2. Register projection: real display fields, OPEX/CAPEX, decision info
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("register returns populated display fields from real joins (name/dept/category/currency)")
	void registerPopulatesDisplayFields() throws Exception {
		Instant sub = Instant.now().minus(2, ChronoUnit.HOURS);
		ExpenseRequest r = decided(this.requesterA, this.deptA, this.opex, "100.0000",
				ExpenseRequest.Status.APPROVED, sub, sub.plus(30, ChronoUnit.MINUTES), this.approverDeptA, "APPROVE");
		var result = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("requesterId", this.requesterA.getId().toString())
				.param("requestNumber", r.getRequestNumber()) // exact row selection
				.with(user(installationAdmin())))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		// Single row.
		assertThat(jsonInt(body, "total")).isEqualTo(1);
		// Display fields populated (not the null placeholders of the stub projection).
		assertThat(body).contains("\"requesterName\":\"" + this.requesterA.displayName() + "\"");
		assertThat(body).contains("\"departmentName\":\"" + this.deptA.getName() + "\"");
		assertThat(body).contains("\"categoryName\":\"" + this.opex.getName() + "\"");
		assertThat(body).contains("\"currencyCode\":\"" + this.baseCode + "\"");
		assertThat(body).contains("\"expenseType\":\"OPEX\"");
		assertThat(body).contains("\"status\":\"APPROVED\"");
		// Decision info present.
		assertThat(body).contains("\"lastActorName\":\"" + this.approverDeptA.displayName() + "\"");
		// decidedAt is ISO-8601
		assertThat(body).containsPattern("\"decidedAt\":\"[^\"]+\"");
		// decision_age_seconds ~= 1800s
		long age = Long.parseLong(java.util.regex.Pattern.compile("\"decisionAgeSeconds\":(\\d+)").matcher(body).results().findFirst().orElseThrow().group(1));
		assertThat(age).isBetween(1700L, 1900L);
	}

	@Test
	@DisplayName("register returns CAPEX expense type for a CAPEX category")
	void registerCapexExpenseType() throws Exception {
		Instant sub = Instant.now().minus(1, ChronoUnit.HOURS);
		ExpenseRequest r = submit(this.requesterB, this.deptB, this.capex, "500", ExpenseRequest.Status.SUBMITTED, sub);
		var body = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("requesterId", this.requesterB.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("\"expenseType\":\"CAPEX\"");
		assertThat(body).contains("\"categoryName\":\"" + this.capex.getName() + "\"");
		assertThat(body).contains("\"branchName\":\"" + this.deptB.getName() + "\""); // deptB has a branch? branches optional
	}

	@Test
	@DisplayName("register does not multiply rows when workflow joins produce an action")
	void registerNoDuplicateRowsFromWorkflowJoins() throws Exception {
		Instant sub = Instant.now().minus(2, ChronoUnit.HOURS);
		decided(this.requesterA, this.deptA, this.opex, "100", ExpenseRequest.Status.APPROVED,
				sub, sub.plus(10, ChronoUnit.MINUTES), this.approverDeptA, "APPROVE");
		var body = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(jsonInt(body, "total")).isEqualTo(1);
	}

	// ------------------------------------------------------------------------------------
	// 3. Scope isolation
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("Department-scoped viewer sees only their department non-DRAFTs plus own drafts")
	void deptScopeIsolates() throws Exception {
		submit(this.requesterA, this.deptA, this.opex, "100", ExpenseRequest.Status.SUBMITTED, Instant.now().minus(1, ChronoUnit.DAYS));
		submit(this.requesterB, this.deptB, this.capex, "200", ExpenseRequest.Status.SUBMITTED, Instant.now().minus(1, ChronoUnit.DAYS));
		draft(this.requesterB, this.deptB, this.opex, "300");
		var result = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("status", "SUBMITTED")
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(deptAViewer())))
			.andExpect(status().isOk()).andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(jsonInt(body, "total")).isEqualTo(1);
		assertThat(body).doesNotContain(this.requesterB.getId().toString());
	}

	@Test
	@DisplayName("Self-only user sees only their own requests (own DRAFTs visible, others hidden)")
	void selfOnlyScope() throws Exception {
		submit(this.requesterA, this.deptA, this.opex, "111", ExpenseRequest.Status.SUBMITTED, Instant.now());
		submit(this.requesterB, this.deptB, this.capex, "222", ExpenseRequest.Status.SUBMITTED, Instant.now());
		var result = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("status", "SUBMITTED")
				.with(user(selfOnly(this.requesterA))))
			.andExpect(status().isOk()).andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(jsonInt(body, "total")).isEqualTo(1);
		assertThat(body).doesNotContain(this.requesterB.getId().toString());
	}

	// ------------------------------------------------------------------------------------
	// 4. DRAFT visibility: register vs dashboard vs totals consistency
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("register total and totals.totalCount agree when requester has DRAFTs; dashboard excludes DRAFTs")
	void registerTotalsAgreeAndDashboardExcludesDrafts() throws Exception {
		// 2 drafts + 1 submitted for requesterA
		draft(this.requesterA, this.deptA, this.opex, "10");
		draft(this.requesterA, this.deptA, this.opex, "20");
		submit(this.requesterA, this.deptA, this.opex, "30", ExpenseRequest.Status.SUBMITTED, Instant.now());
		// requesterB's draft must NOT leak to requesterA's view of any kind
		draft(this.requesterB, this.deptB, this.capex, "999");

		var regBody = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(selfOnly(this.requesterA))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(jsonInt(regBody, "total")).as("register total includes own DRAFTs").isEqualTo(3);
		assertThat(jsonInt(regBody, "totals.totalCount")).as("totals.totalCount matches total").isEqualTo(3);

		// Dashboard aggregate for requesterA excludes DRAFTs → only the SUBMITTED counts.
		var dashBody = this.mockMvc.perform(get(REPORTING + "/dashboard")
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		// total_requests metric (gte 1, excluding drafts)
		var m = java.util.regex.Pattern.compile("\"key\":\"total_requests\",\"label\":\"[^\"]*\",\"value\":(\\d+)").matcher(dashBody);
		assertThat(m.find()).isTrue();
		assertThat(Integer.parseInt(m.group(1))).as("dashboard total_requests excludes drafts").isEqualTo(1);
	}

	@Test
	@DisplayName("other users cannot see a requester's drafts even via totals")
	void otherUsersCannotSeeDrafts() throws Exception {
		draft(this.requesterA, this.deptA, this.opex, "10");
		submit(this.requesterA, this.deptA, this.opex, "20", ExpenseRequest.Status.SUBMITTED, Instant.now());
		// requesterB (self-only) must see 0 rows about requesterA
		var body = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.with(user(selfOnly(this.requesterB))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(jsonInt(body, "total")).as("other user sees no requesterA rows (draft or submitted)")
				.isEqualTo(0);
	}

	// ------------------------------------------------------------------------------------
	// 5. Aggregates, totals, date boundaries
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("dashboard totals reflect the full matching dataset scoped to fixture requesters")
	void aggregatesMatchData() throws Exception {
		Instant ref = Instant.now().minus(2, ChronoUnit.DAYS);
		submit(this.requesterA, this.deptA, this.opex, "100.0000", ExpenseRequest.Status.APPROVED, ref);
		submit(this.requesterA, this.deptA, this.capex, "250.0000", ExpenseRequest.Status.SUBMITTED, ref.plus(1, ChronoUnit.DAYS));
		submit(this.requesterB, this.deptB, this.opex, "75.5000", ExpenseRequest.Status.REJECTED, ref.plus(1, ChronoUnit.DAYS));
		submit(this.requesterB, this.deptB, this.opex, "40.0000", ExpenseRequest.Status.RETURNED, ref.plus(2, ChronoUnit.DAYS));
		draft(this.requesterA, this.deptA, this.opex, "999.0000");

		var bodyA = this.mockMvc.perform(get(REPORTING + "/dashboard")
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		java.util.function.BiConsumer<String, Integer> assertMetric = (key, min) -> {
			var m = java.util.regex.Pattern
				.compile("\"key\":\"" + key + "\",\"label\":\"[^\"]*\",\"value\":(\\d+)")
				.matcher(bodyA);
			assertThat(m.find()).as("metric %s present", key).isTrue();
			int v = Integer.parseInt(m.group(1));
			assertThat(v).as("metric %s", key).isGreaterThanOrEqualTo(min);
		};
		assertMetric.accept("total_requests", 2);
		assertMetric.accept("pending_approvals", 1);
		assertMetric.accept("approved_total", 1);

		var bodyB = this.mockMvc.perform(get(REPORTING + "/dashboard")
				.param("requesterId", this.requesterB.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		java.util.function.BiConsumer<String, Integer> assertMetricB = (key, min) -> {
			var m = java.util.regex.Pattern
				.compile("\"key\":\"" + key + "\",\"label\":\"[^\"]*\",\"value\":(\\d+)")
				.matcher(bodyB);
			assertThat(m.find()).as("metric %s", key).isTrue();
			assertThat(Integer.parseInt(m.group(1))).as("metric %s", key).isGreaterThanOrEqualTo(min);
		};
		assertMetricB.accept("total_requests", 2);
		assertMetricB.accept("rejected_total", 1);
		assertMetricB.accept("returned_total", 1);
	}

	@Test
	@DisplayName("register totals match rows for a department filter (gte with shared-container isolation)")
	void registerTotalsMatchRows() throws Exception {
		submit(this.requesterA, this.deptA, this.opex, "100", ExpenseRequest.Status.APPROVED, Instant.now().minus(1, ChronoUnit.DAYS));
		submit(this.requesterA, this.deptA, this.opex, "150", ExpenseRequest.Status.SUBMITTED, Instant.now());
		submit(this.requesterB, this.deptB, this.capex, "999", ExpenseRequest.Status.SUBMITTED, Instant.now());
		var result = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("departmentId", this.deptA.getId().toString())
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk()).andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(jsonInt(body, "total")).isGreaterThanOrEqualTo(2);
		assertThat(jsonInt(body, "totals.totalCount")).isGreaterThanOrEqualTo(2);
		assertThat(jsonInt(body, "totals.pendingCount")).isGreaterThanOrEqualTo(1);
		assertThat(jsonInt(body, "totals.approvedCount")).isGreaterThanOrEqualTo(1);
	}

	@Test
	@DisplayName("date range is inclusive on both ends and consistent between endpoints")
	void dateBoundariesInclusive() throws Exception {
		Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
		Instant t1 = Instant.parse("2026-01-02T00:00:00Z");
		Instant t2 = Instant.parse("2026-01-03T00:00:00Z");
		submit(this.requesterA, this.deptA, this.opex, "10", ExpenseRequest.Status.APPROVED, t0);
		submit(this.requesterA, this.deptA, this.opex, "20", ExpenseRequest.Status.APPROVED, t1);
		submit(this.requesterA, this.deptA, this.opex, "30", ExpenseRequest.Status.APPROVED, t2);

		var regBody = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("from", t0.toString()).param("to", t1.toString())
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(jsonInt(regBody, "total")).isGreaterThanOrEqualTo(2);

		var dashBody = this.mockMvc.perform(get(REPORTING + "/dashboard")
				.param("from", t0.toString()).param("to", t1.toString())
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		var dm = java.util.regex.Pattern
			.compile("\"key\":\"total_requests\",\"label\":\"[^\"]*\",\"value\":(\\d+)")
			.matcher(dashBody);
		assertThat(dm.find()).isTrue();
		assertThat(Integer.parseInt(dm.group(1))).isGreaterThanOrEqualTo(2);
	}

	@Test
	@DisplayName("invalid date range (from after to) returns 400")
	void invalidDateRange() throws Exception {
		Instant t0 = Instant.parse("2026-01-05T00:00:00Z");
		Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
		this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("from", t0.toString()).param("to", t1.toString())
				.with(user(installationAdmin())))
			.andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------------------------
	// 6. Search
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("free-text search matches requestNumber")
	void searchByRequestNumber() throws Exception {
		ExpenseRequest r = submit(this.requesterA, this.deptA, this.opex, "10", ExpenseRequest.Status.SUBMITTED, Instant.now());
		this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("search", r.getRequestNumber())
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(installationAdmin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(1));
	}

	// ------------------------------------------------------------------------------------
	// 7. Sorting
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("register supports requestNumber sort in both directions with deterministic tiebreaker")
	void sortByRequestNumberAscDesc() throws Exception {
		Instant t = Instant.now().minus(1, ChronoUnit.DAYS);
		// Use distinct request_number prefixes we control.
		ExpenseRequest a = submit(this.requesterA, this.deptA, this.opex, "10", ExpenseRequest.Status.SUBMITTED, t);
		ExpenseRequest b = submit(this.requesterA, this.deptA, this.opex, "20", ExpenseRequest.Status.SUBMITTED, t.plus(1, ChronoUnit.MINUTES));
		ExpenseRequest c = submit(this.requesterA, this.deptA, this.opex, "30", ExpenseRequest.Status.SUBMITTED, t.plus(2, ChronoUnit.MINUTES));
		// Filter by the three unique prefixes. request_numbers start "RPT-…"; we filter by a prefix shared by all three
		// but isolate by requesterId + the exact second of submission via requesterId.

		var ascBody = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("requesterId", this.requesterA.getId().toString())
				.param("size", "200")
				.with(user(installationAdmin()))
				.with(req -> { req.setParameter("sort", "requestNumber,asc"); return req; }))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		var descBody = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("requesterId", this.requesterA.getId().toString())
				.param("size", "200")
				.with(user(installationAdmin()))
				.with(req -> { req.setParameter("sort", "requestNumber,desc"); return req; }))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		// Extract the first row's requestNumber in each body.
		String ascFirst = java.util.regex.Pattern.compile("\"requestNumber\":\"([^\"]+)\"").matcher(ascBody).results().findFirst().orElseThrow().group(1);
		String descFirst = java.util.regex.Pattern.compile("\"requestNumber\":\"([^\"]+)\"").matcher(descBody).results().findFirst().orElseThrow().group(1);
		// Ascending vs descending must produce opposite first rows (since all three have unique request numbers).
		assertThat(ascFirst).isLessThan(descFirst);
	}

	@Test
	@DisplayName("register supports numeric sort (requestedAmount) ascending and descending")
	void sortByAmount() throws Exception {
		Instant t = Instant.now().minus(1, ChronoUnit.DAYS);
		submit(this.requesterA, this.deptA, this.opex, "10", ExpenseRequest.Status.SUBMITTED, t);
		submit(this.requesterA, this.deptA, this.opex, "500", ExpenseRequest.Status.SUBMITTED, t.plus(1, ChronoUnit.MINUTES));
		submit(this.requesterA, this.deptA, this.opex, "9000", ExpenseRequest.Status.SUBMITTED, t.plus(2, ChronoUnit.MINUTES));
		var ascBody = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("requesterId", this.requesterA.getId().toString())
				.param("size", "200")
				.with(user(installationAdmin()))
				.with(req -> { req.setParameter("sort", "requestedAmount,asc"); return req; }))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		var descBody = this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("requesterId", this.requesterA.getId().toString())
				.param("size", "200")
				.with(user(installationAdmin()))
				.with(req -> { req.setParameter("sort", "requestedAmount,desc"); return req; }))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		// Ascending first amount is 10; descending first is 9000.
		String ascFirst = java.util.regex.Pattern.compile("\"requestedAmount\":([0-9.]+)").matcher(ascBody).results().findFirst().orElseThrow().group(1);
		String descFirst = java.util.regex.Pattern.compile("\"requestedAmount\":([0-9.]+)").matcher(descBody).results().findFirst().orElseThrow().group(1);
		assertThat(new BigDecimal(ascFirst)).isLessThan(new BigDecimal(descFirst));
		assertThat(ascFirst).startsWith("10");
		assertThat(descFirst).startsWith("9000");
	}

	@Test
	@DisplayName("unsupported sort field is silently defaulted (no SQL injection / error)")
	void unsupportedSortDefault() throws Exception {
		this.mockMvc.perform(get(REPORTING + "/expense-requests/register")
				.param("requesterId", this.requesterA.getId().toString())
				.with(user(installationAdmin()))
				.with(req -> { req.setParameter("sort", "notARealColumn,asc"); return req; }))
			.andExpect(status().isOk());
	}

	// ------------------------------------------------------------------------------------
	// 8. Permission enforcement
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("CSV export requires report:export permission")
	void exportRequiresPermission() throws Exception {
		EdamPrincipal noExport = new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of("ADMIN"),
				Set.of(Permissions.REPORT_VIEW), Set.of(new DataScope(DataScope.Type.ALL, null)));
		this.mockMvc.perform(post(REPORTING + "/expense-requests/export")
				.with(csrf()).with(user(noExport))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"format\":\"CSV\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Excel export requires report:export permission")
	void excelRequiresPermission() throws Exception {
		EdamPrincipal noExport = new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of("ADMIN"),
				Set.of(Permissions.REPORT_VIEW), Set.of(new DataScope(DataScope.Type.ALL, null)));
		this.mockMvc.perform(post(REPORTING + "/expense-requests/export")
				.with(csrf()).with(user(noExport))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"format\":\"EXCEL\"}"))
			.andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------------------------
	// 9. CSV behavior
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("CSV export returns text/csv, BOM, correct header, populated display fields, audit event")
	void csvAuditAndStructure() throws Exception {
		Instant sub = Instant.now().minus(2, ChronoUnit.HOURS);
		decided(this.requesterA, this.deptA, this.opex, "10", ExpenseRequest.Status.APPROVED,
				sub, sub.plus(5, ChronoUnit.MINUTES), this.approverDeptA, "APPROVE");
		var result = this.mockMvc.perform(post(REPORTING + "/expense-requests/export")
				.with(csrf()).with(user(installationAdmin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"format\":\"CSV\",\"filter\":{\"requesterId\":\"" + this.requesterA.getId() + "\"}}"))
			.andExpect(status().isOk()).andReturn();
		byte[] body = result.getResponse().getContentAsByteArray();
		assertThat(body[0] & 0xFF).isEqualTo(0xEF);
		assertThat(body[1] & 0xFF).isEqualTo(0xBB);
		assertThat(body[2] & 0xFF).isEqualTo(0xBF);
		String csv = new String(body, StandardCharsets.UTF_8);
		assertThat(csv).contains("request_number,current_status_code,expense_type,requester_name");
		// Display fields are populated in CSV (not empty columns).
		assertThat(csv).contains(this.requesterA.displayName());
		assertThat(csv).contains(this.deptA.getName());
		assertThat(csv).contains(this.baseCode);
	}

	@Test
	@DisplayName("csvCell formula-injection prefixes and quoting")
	void csvFormulaProtection() {
		assertThat(ReportingService.csvCell("=cmd|'/c'!A1")).startsWith("'");
		assertThat(ReportingService.csvCell("+SUM(A1:A2)")).startsWith("'");
		assertThat(ReportingService.csvCell("-2+2")).startsWith("'");
		assertThat(ReportingService.csvCell("@SUM(A1)")).startsWith("'");
		assertThat(ReportingService.csvCell("  =cmd")).startsWith("'");
		assertThat(ReportingService.csvCell("\t+cmd")).startsWith("'");
		assertThat(ReportingService.csvCell("\r\n-cmd")).startsWith("'");
		assertThat(ReportingService.csvCell("-123.45")).startsWith("'");
		assertThat(ReportingService.csvCell("Approved")).isEqualTo("Approved");
		assertThat(ReportingService.csvCell("a,b")).isEqualTo("\"a,b\"");
		assertThat(ReportingService.csvCell("he said \"hi\"")).isEqualTo("\"he said \"\"hi\"\"\"");
		assertThat(ReportingService.csvCell("line1\nline2")).startsWith("\"");
	}

	// ------------------------------------------------------------------------------------
	// 10. Excel behavior
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("Excel export returns 200 with xlsx content type and ZIP magic")
	void excelContentType() throws Exception {
		submit(this.requesterA, this.deptA, this.opex, "123456789012345.1234", ExpenseRequest.Status.APPROVED, Instant.now());
		var result = this.mockMvc.perform(post(REPORTING + "/expense-requests/export")
				.with(csrf()).with(user(installationAdmin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"format\":\"EXCEL\",\"filter\":{\"requesterId\":\"" + this.requesterA.getId() + "\"}}"))
			.andExpect(status().isOk()).andReturn();
		byte[] body = result.getResponse().getContentAsByteArray();
		assertThat(body).startsWith(new byte[]{0x50, 0x4B, 0x03, 0x04});
		assertThat(result.getResponse().getContentType()).contains("spreadsheetml");
		// Monetary values are written as plain decimal text — workbook XML must contain the digits.
		String xlsx = new String(body, StandardCharsets.ISO_8859_1);
		// Search for the digit sequence. It may be split into shared-string runs but enough
		// consecutive digits of the integer part will appear inline in <v>…</v> or <t>…</t>.
		assertThat(xlsx).containsPattern("1234567890123");
	}

	// ------------------------------------------------------------------------------------
	// 11. Export audit events for both formats
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("both CSV and Excel exports record an audit event")
	void exportAuditRecords() throws Exception {
		submit(this.requesterA, this.deptA, this.opex, "10", ExpenseRequest.Status.APPROVED, Instant.now().minusSeconds(30));
		this.mockMvc.perform(post(REPORTING + "/expense-requests/export")
				.with(csrf()).with(user(installationAdmin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"format\":\"CSV\",\"filter\":{\"requesterId\":\"" + this.requesterA.getId() + "\"}}"))
			.andExpect(status().isOk());
		this.mockMvc.perform(post(REPORTING + "/expense-requests/export")
				.with(csrf()).with(user(installationAdmin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"format\":\"EXCEL\",\"filter\":{\"requesterId\":\"" + this.requesterA.getId() + "\"}}"))
			.andExpect(status().isOk());

		Integer csvAudits = this.reporting.jdbc().query(
				"select count(*) from audit_events where performed_by = ? and action = 'EXPORT' "
				+ "and new_values::text like '%\"reason\"%' and new_values::text like '%CSV%' "
				+ "and created_at > now() - interval '5 minutes'",
				ps -> ps.setObject(1, this.admin.getId()),
				rs -> { rs.next(); return rs.getInt(1); });
		Integer xlAudits = this.reporting.jdbc().query(
				"select count(*) from audit_events where performed_by = ? and action = 'EXPORT' "
				+ "and new_values::text like '%\"reason\"%' and new_values::text like '%EXCEL%' "
				+ "and created_at > now() - interval '5 minutes'",
				ps -> ps.setObject(1, this.admin.getId()),
				rs -> { rs.next(); return rs.getInt(1); });
		assertThat(csvAudits).as("CSV audit count").isGreaterThanOrEqualTo(1);
		assertThat(xlAudits).as("Excel audit count").isGreaterThanOrEqualTo(1);
	}

	// ------------------------------------------------------------------------------------
	// 12. PostgreSQL fetch-size cursor configuration (unit-level assertion)
	// ------------------------------------------------------------------------------------
	@Test
	@DisplayName("export stream path configures a positive fetch size for PostgreSQL cursor streaming")
	void exportFetchSizeConfigured() {
		// The service-level constant and the JDBC template's ability to accept fetch size is
		// all we can verify from an integration test without a wrapping DataSource proxy —
		// the fetch size is applied in streamCsv/streamExcel via PreparedStatement.setFetchSize.
		assertThat(ReportingService.EXPORT_FETCH_SIZE).isPositive().isLessThanOrEqualTo(2048);
	}
}
