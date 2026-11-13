package com.edam.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.model.ExpenseNature;
import com.edam.expenseconfiguration.model.ExpenseRequestType;
import com.edam.expenseconfiguration.repository.ExpenseCategoryRepository;
import com.edam.expenseconfiguration.repository.ExpenseNatureRepository;
import com.edam.expenseconfiguration.repository.ExpenseRequestTypeRepository;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.expenserequest.service.ExpenseRequestService;
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
 * Approval actions over HTTP: the inbox, the case file, and approve/reject/revert/forward.
 *
 * <h2>The rule under test throughout</h2>
 * BRD 24 is explicit that a permission is not an assignment. Holding {@code approval:act} lets a user reach
 * the endpoints; it confers no authority over anyone else's task. Most tests below therefore hold the
 * permission and still expect a refusal, because the refusal is the requirement — a test that only checked
 * "a user with the permission can approve their own task" would pass on an implementation with no
 * assignment check at all.
 *
 * <h2>Setup</h2>
 * Each test builds a real chain through the configuration service and submits a real request through the
 * request service. Nothing is inserted by hand, because an approval flow driven by fabricated rows proves
 * nothing about the flow the application builds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Approval actions")
class ApprovalActionsApiTest extends AbstractPostgreSqlIntegrationTest {

	private static final String APPROVALS = "/api/v1/approvals";

	private static final String REQUESTS = "/api/v1/expense-requests";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WorkflowConfigurationService configuration;

	@Autowired
	private ExpenseRequestService requests;

	@Autowired
	private ExpenseRequestRepository requestRows;

	@Autowired
	private InstancesRepository instances;

	@Autowired
	private TasksRepository tasks;

	@Autowired
	private ActionsRepository actions;

	@Autowired
	private VersionsRepository versions;

	@Autowired
	private ExpenseRequestTypeRepository requestTypes;

	@Autowired
	private ExpenseCategoryRepository categories;

	@Autowired
	private ExpenseNatureRepository natures;

	@Autowired
	private DepartmentRepository departments;

	@Autowired
	private OrganizationUnitRepository units;

	@Autowired
	private OrganizationUnitTypeRepository unitTypes;

	@Autowired
	private OrganizationProfileRepository profiles;

	@Autowired
	private CurrencyRepository currencies;

	@Autowired
	private UserRepository users;

	private Department department;

	private Currency currency;

	private ExpenseCategory category;

	private ExpenseNature nature;

	private ExpenseRequestType requestType;

	private User requester;

	private User approver;

	private User outsider;

	@BeforeEach
	void setUp() {
		OrganizationProfile profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.currency = this.currencies.findById(profile.getDefaultCurrencyId()).orElseThrow();
		this.department = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.requester = RequestFixtures.user(this.users, this.department.getId());
		this.approver = RequestFixtures.user(this.users, this.department.getId());
		this.outsider = RequestFixtures.user(this.users, this.department.getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		this.requestType = RequestFixtures.requestType(this.requestTypes, "APR-" + suffix);
		this.category = RequestFixtures.category(this.categories, "APC-" + suffix, ExpenseCategory.ExpenseType.OPEX);
		this.nature = RequestFixtures.nature(this.natures, "APN-" + suffix);
		RequestFixtures.publishedWorkflow(this.configuration, this.requestType, this.approver.getId(), "APR-" + suffix);
	}

	// ---------------------------------------------------------------------------------------------
	// Fixtures
	// ---------------------------------------------------------------------------------------------

	private EdamPrincipal principal(User user, String... permissions) {
		return new EdamPrincipal(user.getId(), "EMP", "apr-" + user.getId(), user.getFirstName() + " " + user.getLastName(), Set.of(),
				Set.of(permissions), Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())));
	}

	private EdamPrincipal owner() {
		return principal(this.requester, Permissions.REQUEST_CREATE, Permissions.REQUEST_VIEW_OWN,
				Permissions.REQUEST_SUBMIT, Permissions.REQUEST_EDIT, Permissions.APPROVAL_VIEW,
				Permissions.APPROVAL_ACT);
	}

	private EdamPrincipal approverPerformer() {
		return principal(this.approver, Permissions.REQUEST_VIEW_OWN, Permissions.APPROVAL_VIEW,
				Permissions.APPROVAL_ACT);
	}

	/**
	 * The same approver, scoped to a department that is not the request's.
	 *
	 * <p>Their task still names them, so the approval endpoints must serve the request; the register must
	 * not. Without the narrower scope the register would legitimately answer, and the test would prove
	 * nothing about assignment granting access.
	 */
	private EdamPrincipal approverOutsideTheRegisterScope() {
		Department elsewhere = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		return new EdamPrincipal(this.approver.getId(), "EMP", "apr-" + this.approver.getId(),
				"Outside Scope", Set.of(), Set.of(Permissions.REQUEST_VIEW_OWN, Permissions.APPROVAL_VIEW,
						Permissions.APPROVAL_ACT),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, elsewhere.getId())));
	}

	/** A caller with every approval permission but no task assigned to them. */
	private EdamPrincipal unassigned() {
		return principal(this.outsider, Permissions.APPROVAL_VIEW, Permissions.APPROVAL_ACT);
	}

	/** Creates and submits a request, returning the submitted entity's id. */
	private UUID submittedRequest() {
		com.edam.expenserequest.dto.ExpenseRequestDtos.RequestResponse created = this.requests
			.create(new com.edam.expenserequest.dto.ExpenseRequestDtos.CreateRequest(
				this.requestType.getId(), this.category.getId(), null, this.nature.getId(),
				this.department.getId(), null, this.currency.getId(), new java.math.BigDecimal("1500.0000"),
				"Approval test",
				// OPEX detail is required by the OPEX branch of the validator.
				// recurring, recurrenceFrequency, nonRecurringJustification, accrued, previouslyApproved,
				// ytd, mtd, paymentTerms, budgetAvailable, budgetReference, threeQuotationsSelected,
				// alreadyApproved, taxExempt, grnRequired — fourteen components.
				new com.edam.expenserequest.dto.ExpenseRequestDtos.OpexDetailPayload(true, "MONTHLY",
						null, null, null, null, null, null, null, null, null, null, null, null),
				null, // CAPEX detail is meaningless for an OPEX request
				// An allocation is mandatory at submission: an unallocated expense has no department to
				// charge. A quotation is what makes the amount defensible, so one is supplied too.
				List.of(new com.edam.expenserequest.dto.ExpenseRequestDtos.AllocationPayload(
						this.department.getId(), null, null, new java.math.BigDecimal("1500.0000"), null)),
				List.of(),
				List.of(new com.edam.expenserequest.dto.ExpenseRequestDtos.QuotationPayload(null, "Q-APR-1",
						java.time.LocalDate.now(), new java.math.BigDecimal("1500.0000"), this.currency.getId(),
						null)),
				List.of(), List.of(), List.of(), List.of(), List.of()),
				owner());
		// The envelope carries a UUID id, so no string round-trip is needed at either end.
		return this.requests.submit(created.id(), owner()).id();
	}

	/** The single open task on a request, which every fixture workflow creates exactly one of. */
	private WorkflowTask openTask(UUID requestId) {
		WorkflowInstance instance = this.instances.findByExpenseRequestId(requestId).orElseThrow();
		return this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId()).stream()
			.filter(task -> task.getStatus() == WorkflowTask.TaskStatus.PENDING)
			.findFirst()
			.orElseThrow(() -> new AssertionError("the submitted request has no open approval task"));
	}

	private String actionBody(String remarks) {
		return "{\"remarks\": " + (remarks == null ? "null" : "\"" + remarks + "\"") + "}";
	}

	// ---------------------------------------------------------------------------------------------
	// Inbox
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("inbox")
	class Inbox {

		@Test
		@DisplayName("a submission appears in the assigned approver's inbox and nowhere else")
		void submissionAppearsInAssignedInbox() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();

			ApprovalActionsApiTest.this.mockMvc
				.perform(get(APPROVALS).with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].requestId").value(requestId.toString()))
				.andExpect(jsonPath("$.data.content[0].requestStatus").value("SUBMITTED"));

			// The outsider holds approval:view and still sees nothing: the inbox is keyed by assignment.
			ApprovalActionsApiTest.this.mockMvc
				.perform(get(APPROVALS).with(user(ApprovalActionsApiTest.this.unassigned()))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty());
		}

		@Test
		@DisplayName("the count matches the inbox and drops after the action")
		void countTracksPendingWork() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(get(APPROVALS + "/count")
					.with(user(ApprovalActionsApiTest.this.approverPerformer())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(1));

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/approve").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(ApprovalActionsApiTest.this.actionBody("Looks right")))
				.andExpect(status().isOk());

			ApprovalActionsApiTest.this.mockMvc
				.perform(get(APPROVALS + "/count")
					.with(user(ApprovalActionsApiTest.this.approverPerformer())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(0));
		}

		@Test
		@DisplayName("the inbox needs approval:view")
		void inboxRequiresView() throws Exception {
			ApprovalActionsApiTest.this.mockMvc
				.perform(get(APPROVALS).with(user(ApprovalActionsApiTest.this.principal(
						ApprovalActionsApiTest.this.requester, Permissions.REQUEST_VIEW_OWN)))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Case file
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("case file")
	class CaseFile {

		@Test
		@DisplayName("the case file names the pinned revision, the state and the available actions")
		void caseFileDescribesTheTask() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);
			WorkflowInstance instance = ApprovalActionsApiTest.this.instances.findById(task.getWorkflowInstanceId())
				.orElseThrow();

			ApprovalActionsApiTest.this.mockMvc
				.perform(get(APPROVALS + "/tasks/" + task.getId())
					.with(user(ApprovalActionsApiTest.this.approverPerformer())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.requestId").value(requestId.toString()))
				.andExpect(jsonPath("$.data.workflowVersionId").value(instance.getWorkflowVersionId().toString()))
				.andExpect(jsonPath("$.data.open").value(true))
				// Approve, reject and revert are all offered; forward needs a recipient and is offered too.
				.andExpect(jsonPath("$.data.availableActions").isNotEmpty());
		}

		@Test
		@DisplayName("the approver can read the request behind their task even outside their data scope")
		void caseFileGrantsTheRequest() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			// The register endpoint refuses this caller: their data scope covers another department, and
			// nothing else entitles them to the row. Assignment is the only thing granting access.
			ApprovalActionsApiTest.this.mockMvc
				.perform(get(REQUESTS + "/" + requestId)
					.with(user(ApprovalActionsApiTest.this.approverOutsideTheRegisterScope()))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());

			ApprovalActionsApiTest.this.mockMvc
				.perform(get(APPROVALS + "/tasks/" + task.getId() + "/request")
					.with(user(ApprovalActionsApiTest.this.approverOutsideTheRegisterScope()))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(requestId.toString()));
		}

		@Test
		@DisplayName("someone else's task is a 404, not a 403 that would confirm it exists")
		void otherUsersTaskIsNotFound() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(get(APPROVALS + "/tasks/" + task.getId())
					.with(user(ApprovalActionsApiTest.this.unassigned())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Approve
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("approve")
	class Approve {

		@Test
		@DisplayName("approving closes the task, completes the workflow and approves the request")
		void approveCompletesSingleStepChain() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/approve").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(ApprovalActionsApiTest.this.actionBody("Approved for payment")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.taskStatus").value("APPROVED"))
				.andExpect(jsonPath("$.data.workflowCompleted").value(true))
				.andExpect(jsonPath("$.data.requestStatus").value("APPROVED"));

			ExpenseRequest reloaded = ApprovalActionsApiTest.this.requestRows.findById(requestId).orElseThrow();
			assertThat(reloaded.getCurrentStatusCode()).isEqualTo(ExpenseRequest.Status.APPROVED);
		}

		@Test
		@DisplayName("holding approval:act without the task is refused")
		void permissionIsNotAssignment() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/approve").with(csrf())
					.with(user(ApprovalActionsApiTest.this.unassigned()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(ApprovalActionsApiTest.this.actionBody("I approve this")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("EDAM-2002"));

			// The task is untouched, so the refusal is not merely a status code.
			assertThat(ApprovalActionsApiTest.this.tasks.findById(task.getId()).orElseThrow().getStatus())
				.isEqualTo(WorkflowTask.TaskStatus.PENDING);
		}

		@Test
		@DisplayName("a blank remark is refused and the task stays open")
		void blankRemarksRefused() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/approve").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(ApprovalActionsApiTest.this.actionBody("   ")))
				.andExpect(status().isBadRequest());

			assertThat(ApprovalActionsApiTest.this.tasks.findById(task.getId()).orElseThrow().getStatus())
				.isEqualTo(WorkflowTask.TaskStatus.PENDING);
		}

		@Test
		@DisplayName("a second approval of the same task is refused as a stale action")
		void duplicateApprovalRefused() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/approve").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(ApprovalActionsApiTest.this.actionBody("First")))
				.andExpect(status().isOk());

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/approve").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(ApprovalActionsApiTest.this.actionBody("Second")))
				// 422 rather than 409: the request is well-formed and the caller is who they say they are,
				// but the task is no longer in a state that accepts the action. 409 would suggest the client
				// should retry after a reload; there is nothing to retry.
				.andExpect(status().isUnprocessableEntity());

			// One approval was recorded, not two: the audit trail is not double-counted.
			assertThat(ApprovalActionsApiTest.this.actions
				.findAll()
				.stream()
				.filter(action -> action.getWorkflowTaskId().equals(task.getId()))
				.filter(action -> action.getActionCode() == WorkflowAction.ActionCode.APPROVE)
				.count()).isEqualTo(1);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Reject and revert
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("reject and revert")
	class RejectAndRevert {

		@Test
		@DisplayName("rejecting ends the workflow and rejects the request")
		void rejectTerminates() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/reject").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(ApprovalActionsApiTest.this.actionBody("Not budgeted")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.requestStatus").value("REJECTED"));

			assertThat(ApprovalActionsApiTest.this.requestRows.findById(requestId).orElseThrow().getCurrentStatusCode())
				.isEqualTo(ExpenseRequest.Status.REJECTED);
		}

		@Test
		@DisplayName("reverting returns the request to the requester and re-opens the first step")
		void revertReopensFirstStep() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/revert").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(ApprovalActionsApiTest.this.actionBody("Wrong cost centre")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.workflowCompleted").value(false));

			// The request is editable again, which is the point of a revert.
			ExpenseRequest reverted = ApprovalActionsApiTest.this.requestRows.findById(requestId).orElseThrow();
			assertThat(reverted.getCurrentStatusCode()).isEqualTo(ExpenseRequest.Status.RETURNED);
			assertThat(reverted.isEditable()).isTrue();

			// The step was re-opened rather than abandoned, so the chain is ready for resubmission.
			WorkflowInstance instance = ApprovalActionsApiTest.this.instances.findByExpenseRequestId(requestId)
				.orElseThrow();
			assertThat(instance.getCurrentStateId()).isNotNull();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Forward
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("forward")
	class Forward {

		@Test
		@DisplayName("forwarding closes the caller's task and opens one for the recipient")
		void forwardDelegates() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/forward").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"recipientUserId": "%s", "remarks": "Covering for me today"}
							""".formatted(ApprovalActionsApiTest.this.outsider.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.newTaskIds").isNotEmpty());

			// The recipient now holds the task, in the same step, so their decision counts toward the
			// quorum rather than starting a new one.
			WorkflowInstance instance = ApprovalActionsApiTest.this.instances.findByExpenseRequestId(requestId)
				.orElseThrow();
			List<WorkflowTask> open = ApprovalActionsApiTest.this.tasks
				.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId())
				.stream().filter(candidate -> candidate.getStatus() == WorkflowTask.TaskStatus.PENDING).toList();
			assertThat(open).hasSize(1);
			assertThat(open.get(0).getAssignedUserId())
				.isEqualTo(ApprovalActionsApiTest.this.outsider.getId());
			assertThat(open.get(0).getWorkflowStepId()).isEqualTo(task.getWorkflowStepId());
		}

		@Test
		@DisplayName("forwarding requires both a recipient and remarks")
		void forwardRequiresBoth() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			// Remarks without a recipient.
			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/forward").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"remarks": "Someone else should see this"}
							"""))
				.andExpect(status().isBadRequest());

			// A recipient without remarks.
			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/forward").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"recipientUserId": "%s"}
							""".formatted(ApprovalActionsApiTest.this.outsider.getId())))
				.andExpect(status().isBadRequest());

			assertThat(ApprovalActionsApiTest.this.tasks.findById(task.getId()).orElseThrow().getStatus())
				.isEqualTo(WorkflowTask.TaskStatus.PENDING);
		}

		@Test
		@DisplayName("forwarding to an unknown user is refused and changes nothing")
		void forwardToUnknownUserRefused() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/forward").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"recipientUserId": "%s", "remarks": "Over to you"}
							""".formatted(UUID.randomUUID())))
				.andExpect(status().isNotFound());

			assertThat(ApprovalActionsApiTest.this.tasks.findById(task.getId()).orElseThrow().getStatus())
				.isEqualTo(WorkflowTask.TaskStatus.PENDING);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// History
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("history")
	class History {

		@Test
		@DisplayName("each action is recorded once, with its actor, remarks and the state it moved from")
		void actionsAreRecorded() throws Exception {
			UUID requestId = ApprovalActionsApiTest.this.submittedRequest();
			WorkflowTask task = ApprovalActionsApiTest.this.openTask(requestId);

			ApprovalActionsApiTest.this.mockMvc
				.perform(post(APPROVALS + "/tasks/" + task.getId() + "/approve").with(csrf())
					.with(user(ApprovalActionsApiTest.this.approverPerformer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(ApprovalActionsApiTest.this.actionBody("Verified against the PO")))
				.andExpect(status().isOk());

			List<WorkflowAction> recorded = ApprovalActionsApiTest.this.actions.findAll().stream()
				.filter(action -> action.getWorkflowTaskId().equals(task.getId())).toList();
			assertThat(recorded).hasSize(1);
			WorkflowAction action = recorded.get(0);
			assertThat(action.getActionCode()).isEqualTo(WorkflowAction.ActionCode.APPROVE);
			assertThat(action.getPerformedBy()).isEqualTo(ApprovalActionsApiTest.this.approver.getId());
			assertThat(action.getRemarks()).isEqualTo("Verified against the PO");
			assertThat(action.getPreviousStateId()).isNotNull();
			assertThat(action.getNextStateId()).isNotNull();

			// The case file shows it, so the UI does not have to reconstruct the history.
			ApprovalActionsApiTest.this.mockMvc
				.perform(get(APPROVALS + "/tasks/" + task.getId())
					.with(user(ApprovalActionsApiTest.this.approverPerformer())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.history").isNotEmpty());
		}
	}
}
