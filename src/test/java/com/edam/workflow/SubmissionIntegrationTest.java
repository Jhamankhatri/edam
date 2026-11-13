package com.edam.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.edam.expenserequest.dto.ExpenseRequestDtos;
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
import com.edam.workflow.model.WorkflowInstance;
import com.edam.workflow.model.WorkflowStep;
import com.edam.workflow.model.WorkflowStepApprover;
import com.edam.workflow.repository.ActionsRepository;
import com.edam.workflow.repository.InstancesRepository;
import com.edam.workflow.repository.TasksRepository;
import com.edam.workflow.service.WorkflowConfigurationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Submission integration (BRD 24.1, Step 3).
 *
 * <h2>The two properties this class exists for</h2>
 * <ol>
 *   <li><b>All-or-nothing.</b> A request is never recorded as submitted without a live workflow instance.
 *       The window between "mark submitted" and "start the workflow" is the exact place where a crash
 *       produces a request that no approver can see and its author can no longer edit.</li>
 *   <li><b>The version is pinned.</b> A request that started under v3 keeps v3 for its whole life, even
 *       after v4 is published. Otherwise publishing a workflow would retroactively change the rules that
 *       already in-flight requests were submitted under.</li>
 * </ol>
 *
 * <p>The second property cannot be tested by asserting the stored foreign key alone — that would only prove
 * a column was written. These tests publish a new revision afterwards and assert that the instance still
 * reports the original version number.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Submission integration")
class SubmissionIntegrationTest extends AbstractPostgreSqlIntegrationTest {

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

	@BeforeEach
	void setUp() {
		OrganizationProfile profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.currency = this.currencies.findById(profile.getDefaultCurrencyId()).orElseThrow();
		this.department = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.requester = RequestFixtures.user(this.users, this.department.getId());
		this.approver = RequestFixtures.user(this.users, this.department.getId());
		this.requestType = RequestFixtures.requestType(this.requestTypes,
				"SUB-" + UUID.randomUUID().toString().substring(0, 8));
		this.category = RequestFixtures.category(this.categories, "SBC-" + UUID.randomUUID().toString().substring(0, 8),
				ExpenseCategory.ExpenseType.OPEX);
		this.nature = RequestFixtures.nature(this.natures, "SBN-" + UUID.randomUUID().toString().substring(0, 8));
	}

	// ---------------------------------------------------------------------------------------------
	// Fixtures
	// ---------------------------------------------------------------------------------------------

	private EdamPrincipal owner() {
		return new EdamPrincipal(this.requester.getId(), "EMP", "sub-" + this.requester.getId(), "Requester",
				Set.of(), Set.of(Permissions.REQUEST_CREATE, Permissions.REQUEST_VIEW_OWN,
						Permissions.REQUEST_SUBMIT, Permissions.REQUEST_EDIT, Permissions.REQUEST_CANCEL),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())));
	}

	private ExpenseRequestDtos.RequestResponse draft() {
		return this.requests.create(new ExpenseRequestDtos.CreateRequest(this.requestType.getId(),
				this.category.getId(), null, this.nature.getId(), this.department.getId(), null,
				this.currency.getId(), new java.math.BigDecimal("1200.0000"), "Submission test",
				new ExpenseRequestDtos.OpexDetailPayload(true, "MONTHLY", null, null, null, null, null, null,
						null, null, null, null, null, null),
				null,
				List.of(new ExpenseRequestDtos.AllocationPayload(this.department.getId(), null, null,
						new java.math.BigDecimal("1200.0000"), null)),
				List.of(),
				List.of(new ExpenseRequestDtos.QuotationPayload(null, "Q-SUB-1", java.time.LocalDate.now(),
						new java.math.BigDecimal("1200.0000"), this.currency.getId(), null)),
				List.of(), List.of(), List.of(), List.of(), List.of()),
				owner());
	}

	/** Publishes a chain of the given number of steps, each naming the same configured individual. */
	private UUID publishChain(int steps) {
		var definition = this.configuration
			.createDefinition(new com.edam.workflow.dto.WorkflowDtos.SaveDefinitionRequest(
					"SUB-" + UUID.randomUUID().toString().substring(0, 8), "Submission chain", null,
					this.requestType.getId()));
		List<com.edam.workflow.dto.WorkflowDtos.StepRequest> stepRequests = new java.util.ArrayList<>();
		for (int n = 1; n <= steps; n++) {
			stepRequests.add(new com.edam.workflow.dto.WorkflowDtos.StepRequest("STEP-" + n,
					"Step " + n, (short) n, WorkflowStep.ApprovalMode.PARALLEL, (short) 1,
					List.of(new com.edam.workflow.dto.WorkflowDtos.ApproverRequest(
							WorkflowStepApprover.ApproverType.USER, null, this.approver.getId(), null))));
		}
		var version = this.configuration.createDraft(definition.getId(),
				new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(stepRequests, List.of(), null));
		return this.configuration.publish(version.id(),
				new com.edam.workflow.dto.WorkflowDtos.PublishRequest(null), this.approver.getId()).id();
	}

	// ---------------------------------------------------------------------------------------------
	// Starting a workflow
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("starting")
	class Starting {

		@Test
		@DisplayName("submission creates one instance, one task and marks the request submitted")
		void submissionStartsRealWorkflow() {
			UUID versionId = SubmissionIntegrationTest.this.publishChain(1);

			ExpenseRequestDtos.RequestResponse submitted = SubmissionIntegrationTest.this.requests.submit(SubmissionIntegrationTest.this.draft().id(), SubmissionIntegrationTest.this.owner());

			ExpenseRequest reloaded = SubmissionIntegrationTest.this.requestRows.findById(submitted.id()).orElseThrow();
			assertThat(reloaded.getCurrentStatusCode()).isEqualTo(ExpenseRequest.Status.SUBMITTED);
			assertThat(reloaded.getCurrentWorkflowInstanceId()).isNotNull();

			WorkflowInstance instance = SubmissionIntegrationTest.this.instances.findById(reloaded.getCurrentWorkflowInstanceId())
				.orElseThrow();
			assertThat(instance.getWorkflowVersionId()).isEqualTo(versionId);
			assertThat(instance.getExpenseRequestId()).isEqualTo(reloaded.getId());
			assertThat(SubmissionIntegrationTest.this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId())).hasSize(1);
		}

		@Test
		@DisplayName("a draft with no published workflow stays a draft and gains no instance")
		void failedStartLeavesAnEditableDraft() {
			// No workflow is published for this request type at all.
			UUID draftId = SubmissionIntegrationTest.this.draft().id();

			assertThatThrownBy(() -> SubmissionIntegrationTest.this.requests.submit(draftId, SubmissionIntegrationTest.this.owner())).isInstanceOf(RuntimeException.class);

			ExpenseRequest reloaded = SubmissionIntegrationTest.this.requestRows.findById(draftId).orElseThrow();
			assertThat(reloaded.getCurrentStatusCode()).isEqualTo(ExpenseRequest.Status.DRAFT);
			assertThat(reloaded.getCurrentWorkflowInstanceId()).isNull();
			assertThat(reloaded.isEditable()).isTrue();
			assertThat(SubmissionIntegrationTest.this.instances.findByExpenseRequestId(draftId)).isEmpty();
		}

		@Test
		@DisplayName("submitting the same request twice does not start a second workflow")
		void doubleSubmissionRefused() {
			SubmissionIntegrationTest.this.publishChain(1);
			UUID draftId = SubmissionIntegrationTest.this.draft().id();
			SubmissionIntegrationTest.this.requests.submit(draftId, SubmissionIntegrationTest.this.owner());

			assertThatThrownBy(() -> SubmissionIntegrationTest.this.requests.submit(draftId, SubmissionIntegrationTest.this.owner())).isInstanceOf(RuntimeException.class);

			// One instance, not two: a duplicate would give the same request two independent approval
			// tracks, and whichever finished second would overwrite the first.
			assertThat(SubmissionIntegrationTest.this.instances.findByExpenseRequestId(draftId)).isPresent();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Version pinning
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("version pinning")
	class VersionPinning {

		@Test
		@DisplayName("publishing a new revision does not move an in-flight request onto it")
		void inFlightKeepsItsVersion() {
			UUID firstVersion = SubmissionIntegrationTest.this.publishChain(1);
			ExpenseRequestDtos.RequestResponse submitted = SubmissionIntegrationTest.this.requests.submit(SubmissionIntegrationTest.this.draft().id(), SubmissionIntegrationTest.this.owner());

			// A new revision is published while the request is in approval.
			UUID secondVersion = SubmissionIntegrationTest.this.publishChain(1);
			assertThat(secondVersion).isNotEqualTo(firstVersion);

			WorkflowInstance instance = SubmissionIntegrationTest.this.instances.findByExpenseRequestId(submitted.id()).orElseThrow();
			assertThat(instance.getWorkflowVersionId())
				.as("the in-flight instance must stay on the revision it started under")
				.isEqualTo(firstVersion);

			// And the published pointer has genuinely moved, so the assertion above is not vacuous.
			assertThat(SubmissionIntegrationTest.this.configuration.requirePublishedForRequestType(SubmissionIntegrationTest.this.requestType.getId()).getId())
				.isEqualTo(secondVersion);
		}

		@Test
		@DisplayName("a request submitted after republication uses the new revision")
		void newSubmissionUsesNewRevision() {
			SubmissionIntegrationTest.this.publishChain(1);
			SubmissionIntegrationTest.this.requests.submit(SubmissionIntegrationTest.this.draft().id(), SubmissionIntegrationTest.this.owner());

			UUID secondVersion = SubmissionIntegrationTest.this.publishChain(1);
			ExpenseRequestDtos.RequestResponse later = SubmissionIntegrationTest.this.requests.submit(SubmissionIntegrationTest.this.draft().id(), SubmissionIntegrationTest.this.owner());

			assertThat(SubmissionIntegrationTest.this.instances.findByExpenseRequestId(later.id()).orElseThrow().getWorkflowVersionId())
				.isEqualTo(secondVersion);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Withdrawal
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("withdrawal")
	class Withdrawal {

		@Test
		@DisplayName("cancelling ends the workflow and leaves no open task in any inbox")
		void cancelEndsTheWorkflow() {
			SubmissionIntegrationTest.this.publishChain(1);
			ExpenseRequestDtos.RequestResponse submitted = SubmissionIntegrationTest.this.requests.submit(SubmissionIntegrationTest.this.draft().id(), SubmissionIntegrationTest.this.owner());
			WorkflowInstance instance = SubmissionIntegrationTest.this.instances.findByExpenseRequestId(submitted.id()).orElseThrow();

			SubmissionIntegrationTest.this.requests.cancel(submitted.id(), "No longer required", SubmissionIntegrationTest.this.owner());

			ExpenseRequest reloaded = SubmissionIntegrationTest.this.requestRows.findById(submitted.id()).orElseThrow();
			assertThat(reloaded.getCurrentStatusCode()).isEqualTo(ExpenseRequest.Status.CANCELLED);

			// No task remains that an approver could still act on. A cancelled request with pending tasks
			// would keep appearing in an inbox and could still be approved.
			assertThat(SubmissionIntegrationTest.this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId()))
				.allMatch(task -> task.getStatus() != com.edam.workflow.model.WorkflowTask.TaskStatus.PENDING);
		}

		@Test
		@DisplayName("cancellation is recorded as an action, not just a status change")
		void cancelIsAudited() {
			SubmissionIntegrationTest.this.publishChain(1);
			ExpenseRequestDtos.RequestResponse submitted = SubmissionIntegrationTest.this.requests.submit(SubmissionIntegrationTest.this.draft().id(), SubmissionIntegrationTest.this.owner());
			WorkflowInstance instance = SubmissionIntegrationTest.this.instances.findByExpenseRequestId(submitted.id()).orElseThrow();

			// Actions are keyed by task, not by instance, so the instance's own task ids are the join.
			Set<UUID> taskIds = SubmissionIntegrationTest.this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId()).stream()
				.map(com.edam.workflow.model.WorkflowTask::getId).collect(java.util.stream.Collectors.toSet());
			long before = SubmissionIntegrationTest.this.actions.findAll().stream()
				.filter(action -> action.getWorkflowTaskId() != null && taskIds.contains(action.getWorkflowTaskId()))
				.count();
			SubmissionIntegrationTest.this.requests.cancel(submitted.id(), "Budget withdrawn", SubmissionIntegrationTest.this.owner());
			long after = SubmissionIntegrationTest.this.actions.findAll().stream()
				.filter(action -> action.getWorkflowTaskId() != null && taskIds.contains(action.getWorkflowTaskId()))
				.count();

			assertThat(after).as("a withdrawal must leave a trace an auditor can read").isGreaterThan(before);
		}
	}
}
