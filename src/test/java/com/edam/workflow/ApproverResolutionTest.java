package com.edam.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.edam.common.exception.BusinessException;
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
 * Approver resolution (BRD 24.1, Step 5).
 *
 * <h2>The property being protected</h2>
 * Every test here asks the same question in a different way: <em>when the configured approver cannot be
 * found, does the request go ahead anyway?</em> The answer must be no. A workflow that silently skips a
 * mandatory step produces an approval nobody gave, and the failure is invisible — the request simply moves
 * forward with fewer signatures than the bank configured. So each unresolvable case asserts two things: a
 * refusal, and that no instance or task was left behind for the request.
 *
 * <h2>Why the submissions go through the service</h2>
 * Calling the resolver directly would test a pure function. Submission is where the resolver, the engine,
 * the request state and the transaction boundary meet, and that is the behaviour the bank depends on.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Approver resolution")
class ApproverResolutionTest extends AbstractPostgreSqlIntegrationTest {

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

	@Autowired
	private com.edam.rolemanagement.repository.RoleRepository roles;

	@Autowired
	private com.edam.committee.repository.CommitteeRepository committees;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;

	private Department department;

	private Currency currency;

	private ExpenseCategory category;

	private ExpenseNature nature;

	private User requester;

	private User approver;

	@BeforeEach
	void setUp() {
		OrganizationProfile profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.currency = this.currencies.findById(profile.getDefaultCurrencyId()).orElseThrow();
		this.department = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.requester = RequestFixtures.user(this.users, this.department.getId());
		this.approver = RequestFixtures.user(this.users, this.department.getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		this.category = RequestFixtures.category(this.categories, "RSC-" + suffix, ExpenseCategory.ExpenseType.OPEX);
		this.nature = RequestFixtures.nature(this.natures, "RSN-" + suffix);
	}

	// ---------------------------------------------------------------------------------------------
	// Fixtures
	// ---------------------------------------------------------------------------------------------

	private EdamPrincipal owner() {
		return new EdamPrincipal(this.requester.getId(), "EMP", "rsc-" + this.requester.getId(), "Requester",
				Set.of(), Set.of(Permissions.REQUEST_CREATE, Permissions.REQUEST_VIEW_OWN,
						Permissions.REQUEST_SUBMIT, Permissions.REQUEST_EDIT),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())));
	}

	/**
	 * A complete draft against the given request type.
	 *
	 * <p>It takes the type as a parameter rather than reading the field: the unresolvable-designation tests
	 * deliberately work with a request whose own type is not the published one, and a helper that silently
	 * used a stale field would make those tests assert against the wrong chain.
	 */
	private ExpenseRequestDtos.RequestResponse draft(ExpenseRequestType type) {
		return this.requests.create(new ExpenseRequestDtos.CreateRequest(type.getId(),
				this.category.getId(), null, this.nature.getId(), this.department.getId(), null,
				this.currency.getId(), new java.math.BigDecimal("900.0000"), "Resolution test",
				new ExpenseRequestDtos.OpexDetailPayload(true, "MONTHLY", null, null, null, null, null, null,
						null, null, null, null, null, null),
				null,
				List.of(new ExpenseRequestDtos.AllocationPayload(this.department.getId(), null, null,
						new java.math.BigDecimal("900.0000"), null)),
				List.of(),
				List.of(new ExpenseRequestDtos.QuotationPayload(null, "Q-RSN-1", java.time.LocalDate.now(),
						new java.math.BigDecimal("900.0000"), this.currency.getId(), null)),
				List.of(), List.of(), List.of(), List.of(), List.of()),
				owner());
	}

	private ExpenseRequestType requestType;

	/** A fresh request type plus a published chain whose first step is exactly the given designations. */
	private ExpenseRequestType published(
			List<WorkflowStepApprover.ApproverType> types, UUID referenceId) {
		ExpenseRequestType type = RequestFixtures.requestType(this.requestTypes,
				"RSC-" + UUID.randomUUID().toString().substring(0, 8));
		var definition = this.configuration
			.createDefinition(new com.edam.workflow.dto.WorkflowDtos.SaveDefinitionRequest(
					"RSC-" + UUID.randomUUID().toString().substring(0, 8), "Resolution chain", null,
					type.getId()));
		var approvers = types.stream()
			.map(approverType -> new com.edam.workflow.dto.WorkflowDtos.ApproverRequest(approverType,
					approverType == WorkflowStepApprover.ApproverType.ROLE ? referenceId : null,
					approverType == WorkflowStepApprover.ApproverType.USER ? referenceId : null,
					null))
			.toList();
		var draft = this.configuration.createDraft(definition.id(),
				new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
						List.of(new com.edam.workflow.dto.WorkflowDtos.StepRequest("FIRST", "First approval",
								(short) 1, WorkflowStep.ApprovalMode.PARALLEL, (short) 1, approvers)),
						List.of(), null));
		this.configuration.publish(draft.id(),
				new com.edam.workflow.dto.WorkflowDtos.PublishRequest(null), this.approver.getId());
		return type;
	}

	private UUID submitInto(ExpenseRequestType type) {
		ExpenseRequestDtos.RequestResponse created = this.requests.create(
				new ExpenseRequestDtos.CreateRequest(type.getId(), this.category.getId(), null,
						this.nature.getId(), this.department.getId(), null, this.currency.getId(),
						new java.math.BigDecimal("900.0000"), "Resolution test",
						new ExpenseRequestDtos.OpexDetailPayload(true, "MONTHLY", null, null, null, null, null,
								null, null, null, null, null, null, null),
						null,
						List.of(new ExpenseRequestDtos.AllocationPayload(this.department.getId(), null, null,
								new java.math.BigDecimal("900.0000"), null)),
						List.of(),
						List.of(new ExpenseRequestDtos.QuotationPayload(null, "Q-RSN-2",
								java.time.LocalDate.now(), new java.math.BigDecimal("900.0000"),
								this.currency.getId(), null)),
						List.of(), List.of(), List.of(), List.of(), List.of()),
				owner());
		return this.requests.submit(created.id(), owner()).id();
	}

	// ---------------------------------------------------------------------------------------------
	// Reachable designations
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("a configured individual")
	class ConfiguredIndividual {

		@Test
		@DisplayName("resolves to that user and creates a genuine task")
		void resolvesToTheNamedUser() {
			ApproverResolutionTest.this.requestType = ApproverResolutionTest.this.published(List.of(WorkflowStepApprover.ApproverType.USER), ApproverResolutionTest.this.approver.getId());

			UUID requestId = ApproverResolutionTest.this.submitInto(ApproverResolutionTest.this.requestType);

			WorkflowInstance instance = ApproverResolutionTest.this.instances.findByExpenseRequestId(requestId).orElseThrow();
			var open = ApproverResolutionTest.this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId());
			assertThat(open).hasSize(1);
			assertThat(open.get(0).getAssignedUserId()).isEqualTo(ApproverResolutionTest.this.approver.getId());
			// The task carries the real step it belongs to, not a synthetic one.
			assertThat(open.get(0).getWorkflowStepId()).isNotNull();
			assertThat(ApproverResolutionTest.this.requestRows.findById(requestId).orElseThrow().getCurrentStatusCode())
				.isEqualTo(ExpenseRequest.Status.SUBMITTED);
		}

		@Test
		@DisplayName("an inactive user is not an approver, and submission is refused")
		void inactiveUserResolvesToNobody() {
			ApproverResolutionTest.this.requestType = ApproverResolutionTest.this.published(List.of(WorkflowStepApprover.ApproverType.USER), ApproverResolutionTest.this.approver.getId());
			ApproverResolutionTest.this.approver.setActive(false);
			ApproverResolutionTest.this.users.saveAndFlush(ApproverResolutionTest.this.approver);

			UUID draftId = ApproverResolutionTest.this.draft(ApproverResolutionTest.this.requestType).id();
			assertThatThrownBy(() -> ApproverResolutionTest.this.requests.submit(draftId, ApproverResolutionTest.this.owner()))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("First approval");

			// Nothing was created: no half-submitted request, no orphan task.
			assertThat(ApproverResolutionTest.this.instances.findByExpenseRequestId(draftId)).isEmpty();
			assertThat(ApproverResolutionTest.this.requestRows.findById(draftId).orElseThrow().getCurrentStatusCode())
				.isEqualTo(ExpenseRequest.Status.DRAFT);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Unresolvable designations
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("unresolvable designations")
	class Unresolvable {

		@Test
		@DisplayName("a committee level with nobody seated refuses submission and names the step")
		void emptyCommitteeRefuses() {
			// The level must exist for the step to reference it — the foreign key guarantees that — and it
			// must have no seats, which is the condition under test.
			ExpenseRequestType type = RequestFixtures.requestType(ApproverResolutionTest.this.requestTypes,
					"RSC-" + UUID.randomUUID().toString().substring(0, 8));
			UUID levelId = RequestFixtures.emptyCommitteeLevel(ApproverResolutionTest.this.committees,
					ApproverResolutionTest.this.jdbc, "RSC-" + UUID.randomUUID().toString().substring(0, 8),
					ApproverResolutionTest.this.currency.getId());
			var definition = ApproverResolutionTest.this.configuration
				.createDefinition(new com.edam.workflow.dto.WorkflowDtos.SaveDefinitionRequest(
						"RSC-" + UUID.randomUUID().toString().substring(0, 8), "Committee chain", null,
						type.getId()));
			var draft = ApproverResolutionTest.this.configuration.createDraft(definition.id(),
					new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
							List.of(new com.edam.workflow.dto.WorkflowDtos.StepRequest("COMMITTEE",
									"Committee approval", (short) 1, WorkflowStep.ApprovalMode.PARALLEL, (short) 1,
									List.of(new com.edam.workflow.dto.WorkflowDtos.ApproverRequest(
											WorkflowStepApprover.ApproverType.EXPENSE_COMMITTEE, null, null,
											levelId)))),
							List.of(), null));
			ApproverResolutionTest.this.configuration.publish(draft.id(),
					new com.edam.workflow.dto.WorkflowDtos.PublishRequest(null),
					ApproverResolutionTest.this.approver.getId());

			UUID draftId = ApproverResolutionTest.this.draft(type).id();
			// A committee with no members is not a committee that approves by default. The submission is
			// refused, and it names the step so an administrator knows what to staff.
			assertThatThrownBy(
					() -> ApproverResolutionTest.this.requests.submit(draftId, ApproverResolutionTest.this.owner()))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Committee approval");

			assertThat(ApproverResolutionTest.this.instances.findByExpenseRequestId(draftId)).isEmpty();
			assertThat(ApproverResolutionTest.this.requestRows.findById(draftId).orElseThrow()
				.getCurrentStatusCode()).isEqualTo(ExpenseRequest.Status.DRAFT);
		}

		@Test
		@DisplayName("a CEO step with no office holder refuses submission rather than skipping")
		void missingOfficeHolderRefuses() {
			ExpenseRequestType type = ApproverResolutionTest.this.published(List.of(WorkflowStepApprover.ApproverType.CEO), null);

			UUID draftId = ApproverResolutionTest.this.draft(type).id();
			// This deployment has no CEO configured, which is the ordinary state of a fresh installation.
			assertThatThrownBy(() -> ApproverResolutionTest.this.requests.submit(draftId, ApproverResolutionTest.this.owner()))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("First approval");

			assertThat(ApproverResolutionTest.this.instances.findByExpenseRequestId(draftId)).isEmpty();
			assertThat(ApproverResolutionTest.this.requestRows.findById(draftId).orElseThrow().getCurrentStatusCode())
				.isEqualTo(ExpenseRequest.Status.DRAFT);
		}

		@Test
		@DisplayName("a role held by nobody refuses submission rather than skipping")
		void emptyRoleRefuses() {
			// The role must exist for the step to reference it; what is missing is a holder.
			UUID emptyRoleId = RequestFixtures
				.emptyRole(ApproverResolutionTest.this.roles,
						"RSC-" + UUID.randomUUID().toString().substring(0, 8))
				.getId();
			ExpenseRequestType type = ApproverResolutionTest.this
				.published(List.of(WorkflowStepApprover.ApproverType.ROLE), emptyRoleId);

			UUID draftId = ApproverResolutionTest.this.draft(type).id();
			assertThatThrownBy(() -> ApproverResolutionTest.this.requests.submit(draftId, ApproverResolutionTest.this.owner()))
				.isInstanceOf(BusinessException.class);

			assertThat(ApproverResolutionTest.this.instances.findByExpenseRequestId(draftId)).isEmpty();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Multiple candidates
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("multiple candidates")
	class Multiple {

		@Test
		@DisplayName("several designations resolving to the same person create one task, not duplicates")
		void duplicateResolutionCreatesOneTask() {
			ApproverResolutionTest.this.requestType = ApproverResolutionTest.this.published(
					List.of(WorkflowStepApprover.ApproverType.USER, WorkflowStepApprover.ApproverType.USER),
					ApproverResolutionTest.this.approver.getId());

			UUID requestId = ApproverResolutionTest.this.submitInto(ApproverResolutionTest.this.requestType);

			WorkflowInstance instance = ApproverResolutionTest.this.instances.findByExpenseRequestId(requestId).orElseThrow();
			var open = ApproverResolutionTest.this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId());
			// Two designations, one human: assigning twice would inflate the quorum's apparent size and
			// let a single approval satisfy a two-approver minimum.
			assertThat(open).hasSize(1);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Missing configuration
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("missing configuration")
	class MissingConfiguration {

		@Test
		@DisplayName("a request type with no configured workflow cannot be submitted")
		void noWorkflowRefuses() {
			ExpenseRequestType unconfigured = RequestFixtures.requestType(ApproverResolutionTest.this.requestTypes,
					"RSC-" + UUID.randomUUID().toString().substring(0, 8));

			ExpenseRequestDtos.RequestResponse created = ApproverResolutionTest.this.requests.create(
					new ExpenseRequestDtos.CreateRequest(unconfigured.getId(), ApproverResolutionTest.this.category.getId(), null,
							ApproverResolutionTest.this.nature.getId(), ApproverResolutionTest.this.department.getId(), null, ApproverResolutionTest.this.currency.getId(),
							new java.math.BigDecimal("900.0000"), "Resolution test",
							new ExpenseRequestDtos.OpexDetailPayload(true, "MONTHLY", null, null, null, null,
									null, null, null, null, null, null, null, null),
							null,
							List.of(new ExpenseRequestDtos.AllocationPayload(ApproverResolutionTest.this.department.getId(), null,
									null, new java.math.BigDecimal("900.0000"), null)),
							List.of(),
							List.of(new ExpenseRequestDtos.QuotationPayload(null, "Q-RSN-3",
									java.time.LocalDate.now(), new java.math.BigDecimal("900.0000"),
									ApproverResolutionTest.this.currency.getId(), null)),
							List.of(), List.of(), List.of(), List.of(), List.of()),
					ApproverResolutionTest.this.owner());

			assertThatThrownBy(() -> ApproverResolutionTest.this.requests.submit(created.id(), ApproverResolutionTest.this.owner()))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("No active workflow is configured");

			assertThat(ApproverResolutionTest.this.requestRows.findById(created.id()).orElseThrow().getCurrentStatusCode())
				.isEqualTo(ExpenseRequest.Status.DRAFT);
		}
	}
}
