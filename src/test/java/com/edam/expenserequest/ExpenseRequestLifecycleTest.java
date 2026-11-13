package com.edam.expenserequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ValidationFailedException;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.expenserequest.dto.ExpenseRequestDtos.AllocationPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.CreateRequest;
import com.edam.expenserequest.dto.ExpenseRequestDtos.ItemPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.OpexDetailPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.QuotationPayload;
import com.edam.expenserequest.dto.ExpenseRequestDtos.RequestResponse;
import com.edam.expenserequest.dto.ExpenseRequestDtos.StaPayload;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.expenserequest.service.ExpenseRequestService;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.model.ExpenseNature;
import com.edam.expenseconfiguration.model.ExpenseRequestType;
import com.edam.expenseconfiguration.repository.ExpenseCategoryRepository;
import com.edam.expenseconfiguration.repository.ExpenseNatureRepository;
import com.edam.expenseconfiguration.repository.ExpenseRequestTypeRepository;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.model.TaxAuthority;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.masterdata.repository.TaxAuthorityRepository;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Request creation, submission and cancellation against real PostgreSQL (Module G).
 *
 * <h2>Why these are not {@code @Transactional}</h2>
 * The service methods under test already own their transaction boundaries, and a test-level transaction would
 * mean the assertions observe the same in-memory state the service produced rather than what was actually
 * committed. A test that never commits cannot catch a constraint violation deferred to flush, a generated
 * id that was never read back, or a rollback that failed to roll back. Each test therefore commits and reads
 * back through the repository, and each builds its own fixtures with random codes so it cannot depend on
 * another test's rows.
 *
 * <h2>What is deliberately covered here rather than in the unit tests</h2>
 * The <em>rules</em> are covered exhaustively by {@code RequestValidatorTest}. This class covers the things a
 * pure test cannot: the sequence allocating a number, the generated id being read back, the aggregate's
 * children being persisted through the cascade, the base-currency conversion being resolved from the actual
 * configured profile, and the rollback leaving nothing behind.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpenseRequestLifecycleTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private ExpenseRequestService service;

	@Autowired
	private ExpenseRequestRepository requests;

	@Autowired
	private CurrencyRepository currencies;

	@Autowired
	private ExpenseRequestTypeRepository requestTypes;

	@Autowired
	private com.edam.workflow.service.WorkflowConfigurationService workflowConfiguration;

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
	private UserRepository users;

	@Autowired
	private TaxAuthorityRepository taxAuthorities;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;

	private Department department;

	private User requester;

	private ExpenseRequestType opexType;

	private ExpenseRequestType capexType;

	private User approver;

	private ExpenseCategory opexCategory;

	private ExpenseCategory capexCategory;

	private ExpenseNature nature;

	private Currency currency;

	private OrganizationProfile profile;

	private TaxAuthority taxAuthority;

	@BeforeEach
	void setUp() {
		this.profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.currency = this.currencies.findById(this.profile.getDefaultCurrencyId()).orElseThrow();
		this.department = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.requester = RequestFixtures.user(this.users, this.department.getId());
		this.opexType = RequestFixtures.requestType(this.requestTypes, "OPEX");
		this.capexType = RequestFixtures.requestType(this.requestTypes, "CAPEX");
		// Submission requires a published workflow per request type from Phase 3 onwards. Both types get
		// one because this class exercises OPEX and CAPEX side by side.
		this.approver = RequestFixtures.user(this.users, this.department.getId());
		RequestFixtures.publishedWorkflow(this.workflowConfiguration, this.opexType, this.approver.getId(),
				"LC-OPEX");
		RequestFixtures.publishedWorkflow(this.workflowConfiguration, this.capexType, this.approver.getId(),
				"LC-CAPEX");
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		this.opexCategory = RequestFixtures.category(this.categories, "OP-" + suffix, ExpenseCategory.ExpenseType.OPEX);
		this.capexCategory = RequestFixtures.category(this.categories, "CP-" + suffix,
				ExpenseCategory.ExpenseType.CAPEX);
		this.nature = RequestFixtures.nature(this.natures, "NAT-" + suffix);
		this.taxAuthority = RequestFixtures.taxAuthority(this.taxAuthorities, "STA-" + suffix);
	}

	// ---------------------------------------------------------------------------------------------
	// Fixtures for the payloads
	// ---------------------------------------------------------------------------------------------

	private EdamPrincipal principal(UUID userId) {
		return new EdamPrincipal(userId, "EMP", "user-" + userId, "Test User", Set.of(), Set.of(),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())));
	}

	private EdamPrincipal caller() {
		return principal(this.requester.getId());
	}

	private OpexDetailPayload recurringOpex() {
		return new OpexDetailPayload(true, "MONTHLY", null, null, null, null, null, "NET-30", null, null, false,
				false, false, false);
	}

	private CreateRequest opexPayload(List<QuotationPayload> quotations) {
		return new CreateRequest(this.opexType.getId(), this.opexCategory.getId(), null, this.nature.getId(),
				this.department.getId(), null, this.currency.getId(), RequestFixtures.money("1000.0000"),
				"Monthly network subscription", recurringOpex(), null,
				List.of(new AllocationPayload(this.department.getId(), null, "CC-1",
						RequestFixtures.money("1000.0000"), RequestFixtures.money("100.0000"))),
				null, quotations, null, null, null, null, null);
	}

	private QuotationPayload quotation(String number, String amount) {
		return new QuotationPayload(null, number, java.time.LocalDate.of(2026, 11, 1),
				RequestFixtures.money(amount), this.currency.getId(), null);
	}

	private CreateRequest capexPayload() {
		return new CreateRequest(this.capexType.getId(), this.capexCategory.getId(), null, this.nature.getId(),
				this.department.getId(), null, this.currency.getId(), RequestFixtures.money("5000.0000"),
				"Server purchase",
				null,
				new com.edam.expenserequest.dto.ExpenseRequestDtos.CapexDetailPayload("PURCHASE",
						RequestFixtures.money("5000.0000"), RequestFixtures.money("5000.0000"),
						RequestFixtures.money("5000.0000"), true, RequestFixtures.money("10000.0000"), null, null,
						"IT_EQUIPMENT", null, null, null, null, null, null),
				List.of(new AllocationPayload(this.department.getId(), null, "CC-1",
						RequestFixtures.money("5000.0000"), RequestFixtures.money("100.0000"))),
				List.of(new ItemPayload("Server", RequestFixtures.money("1.0000"),
						RequestFixtures.money("5000.0000"), null)),
				// quotations, invoices, previous summaries, sales tax, budget summaries, documents
				null, null, null,
				// BRD 8.3: at least one sales tax authority is required before submission.
				List.of(new StaPayload(this.taxAuthority.getId(), "STA-1", RequestFixtures.money("250.0000"),
						"Sales tax at 5%")),
				null, null);
	}

	// ---------------------------------------------------------------------------------------------
	// Creation
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("creation")
	class Creation {

		@Test
		@DisplayName("an OPEX draft is persisted with its number, allocation and base-currency amount")
		void opexDraftPersisted() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			assertThat(created.id()).isNotNull();
			assertThat(created.requestNumber()).startsWith("OP-");
			assertThat(created.status()).isEqualTo("DRAFT");
			assertThat(created.classification()).isEqualTo("OPEX");
			assertThat(created.baseCurrencyAmount()).isEqualByComparingTo("1000.0000");
			// Same currency as the base: the identity applies and no rate row is referenced.
			assertThat(created.exchangeRateId()).isNull();
			assertThat(created.allocations()).hasSize(1);

			// Read through the service inside its transaction. A bare findById returns a detached entity
			// whose child collections are lazy, and touching one outside a session fails - that is a
			// property of the mapping, not of the write that the test is about.
			RequestResponse reread = ExpenseRequestLifecycleTest.this.service.get(created.id(), caller());
			assertThat(reread.quotations()).hasSize(1);
			assertThat(reread.allocations()).hasSize(1);
			assertThat(reread.opex()).isNotNull();
			assertThat(reread.capex()).isNull();
		}

		@Test
		@DisplayName("each request receives a distinct number from the sequence")
		void numbersAreUnique() {
			RequestResponse first = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			RequestResponse second = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-2", "1000.0000"))), caller());
			assertThat(first.requestNumber()).isNotEqualTo(second.requestNumber());
		}

		@Test
		@DisplayName("a CAPEX request persists its detail, items and budget figures")
		void capexPersisted() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(capexPayload(), caller());
			assertThat(created.classification()).isEqualTo("CAPEX");
			assertThat(created.capex()).isNotNull();
			assertThat(created.capex().approvalAmount()).isEqualByComparingTo("5000.0000");
			assertThat(created.items()).hasSize(1);
			// The item total is computed, and the computed value is what the schema's CHECK accepted.
			assertThat(created.items().get(0).totalAmount()).isEqualByComparingTo("5000.0000");
			assertThat(created.opex()).isNull();
		}

		@Test
		@DisplayName("a category of the other classification is refused")
		void wrongCategoryRefused() {
			CreateRequest payload = new CreateRequest(ExpenseRequestLifecycleTest.this.opexType.getId(), ExpenseRequestLifecycleTest.this.capexCategory.getId(), null,
					ExpenseRequestLifecycleTest.this.nature.getId(), ExpenseRequestLifecycleTest.this.department.getId(), null, ExpenseRequestLifecycleTest.this.currency.getId(),
					RequestFixtures.money("1000.0000"), "x", recurringOpex(), null, List.of(), null, null, null,
					null, null, null, null);
			assertThatThrownBy(() -> ExpenseRequestLifecycleTest.this.service.create(payload, caller()))
				.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("supplying both OPEX and CAPEX detail is refused")
		void bothDetailsRefused() {
			CreateRequest payload = new CreateRequest(ExpenseRequestLifecycleTest.this.opexType.getId(), ExpenseRequestLifecycleTest.this.opexCategory.getId(), null,
					ExpenseRequestLifecycleTest.this.nature.getId(), ExpenseRequestLifecycleTest.this.department.getId(), null, ExpenseRequestLifecycleTest.this.currency.getId(),
					RequestFixtures.money("1000.0000"), "x", recurringOpex(),
					new com.edam.expenserequest.dto.ExpenseRequestDtos.CapexDetailPayload("PURCHASE", null, null,
							null, true, null, null, null, null, null, null, null, null, null, null),
					List.of(), null, null, null, null, null, null, null);
			assertThatThrownBy(() -> ExpenseRequestLifecycleTest.this.service.create(payload, caller()))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("either OPEX or CAPEX");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Submission
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("submission")
	class Submission {

		@Test
		@DisplayName("a valid OPEX request submits and records the submitted instant")
		void validOpexSubmits() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			RequestResponse submitted = ExpenseRequestLifecycleTest.this.service.submit(created.id(), caller());
			assertThat(submitted.status()).isEqualTo("SUBMITTED");
			assertThat(submitted.submittedAt()).isNotNull();
			assertThat(submitted.canEdit()).isFalse();
		}

		@Test
		@DisplayName("an invalid OPEX request is refused with field errors and stays a draft")
		void invalidOpexRefused() {
			// No quotation: the BRD requires quotation 1 always. A draft is allowed, submission is not.
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(opexPayload(List.of()), caller());
			assertThatThrownBy(() -> ExpenseRequestLifecycleTest.this.service.submit(created.id(), caller()))
				.isInstanceOf(ValidationFailedException.class)
				.hasMessageContaining("invalid");
			ExpenseRequest reloaded = ExpenseRequestLifecycleTest.this.requests.findById(created.id()).orElseThrow();
			assertThat(reloaded.getCurrentStatusCode()).isEqualTo(ExpenseRequest.Status.DRAFT);
		}

		@Test
		@DisplayName("a valid CAPEX request submits")
		void validCapexSubmits() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(capexPayload(), caller());
			RequestResponse submitted = ExpenseRequestLifecycleTest.this.service.submit(created.id(), caller());
			assertThat(submitted.status()).isEqualTo("SUBMITTED");
		}

		@Test
		@DisplayName("a CAPEX request without items is refused at submission")
		void capexWithoutItemsRefused() {
			CreateRequest payload = new CreateRequest(ExpenseRequestLifecycleTest.this.capexType.getId(), ExpenseRequestLifecycleTest.this.capexCategory.getId(), null,
					ExpenseRequestLifecycleTest.this.nature.getId(), ExpenseRequestLifecycleTest.this.department.getId(), null, ExpenseRequestLifecycleTest.this.currency.getId(),
					RequestFixtures.money("5000.0000"), "Server purchase", null,
					new com.edam.expenserequest.dto.ExpenseRequestDtos.CapexDetailPayload("PURCHASE",
							RequestFixtures.money("5000.0000"), RequestFixtures.money("5000.0000"),
							RequestFixtures.money("5000.0000"), true, RequestFixtures.money("10000.0000"), null, null,
							"IT", null, null, null, null, null, null),
					List.of(new AllocationPayload(ExpenseRequestLifecycleTest.this.department.getId(), null, "CC-1",
							RequestFixtures.money("5000.0000"), RequestFixtures.money("100.0000"))),
					null, null, null, null, null, null, null);
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(payload, caller());
			assertThatThrownBy(() -> ExpenseRequestLifecycleTest.this.service.submit(created.id(), caller()))
				.isInstanceOf(ValidationFailedException.class);
		}

		@Test
		@DisplayName("a submitted request cannot be submitted again")
		void doubleSubmissionRefused() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			ExpenseRequestLifecycleTest.this.service.submit(created.id(), caller());
			assertThatThrownBy(() -> ExpenseRequestLifecycleTest.this.service.submit(created.id(), caller()))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("cannot be submitted");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Authorization
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("authorization")
	class Authorization {

		@Test
		@DisplayName("another user cannot submit someone else's request")
		void strangerCannotSubmit() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			User stranger = RequestFixtures.user(ExpenseRequestLifecycleTest.this.users,
					ExpenseRequestLifecycleTest.this.department.getId());
			EdamPrincipal strangerCaller = principal(stranger.getId());
			assertThatThrownBy(
					() -> ExpenseRequestLifecycleTest.this.service.submit(created.id(), strangerCaller))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Only the requester");
		}

		@Test
		@DisplayName("another user cannot cancel someone else's request")
		void strangerCannotCancel() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			User stranger = RequestFixtures.user(ExpenseRequestLifecycleTest.this.users,
					ExpenseRequestLifecycleTest.this.department.getId());
			EdamPrincipal strangerCaller = principal(stranger.getId());
			assertThatThrownBy(
					() -> ExpenseRequestLifecycleTest.this.service.cancel(created.id(), "not mine", strangerCaller))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Only the requester");
		}

		@Test
		@DisplayName("a draft is not visible to a colleague in the same department")
		void draftHiddenFromColleague() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			User colleague = RequestFixtures.user(ExpenseRequestLifecycleTest.this.users,
					ExpenseRequestLifecycleTest.this.department.getId());
			EdamPrincipal colleagueCaller = principal(colleague.getId());
			// 404 rather than 403: confirming existence would itself be a disclosure.
			assertThatThrownBy(
					() -> ExpenseRequestLifecycleTest.this.service.get(created.id(), colleagueCaller))
				.isInstanceOf(com.edam.common.exception.ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("a submitted request is visible within the department scope")
		void submittedVisibleInScope() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			ExpenseRequestLifecycleTest.this.service.submit(created.id(), caller());
			User colleague = RequestFixtures.user(ExpenseRequestLifecycleTest.this.users,
					ExpenseRequestLifecycleTest.this.department.getId());
			EdamPrincipal colleagueCaller = principal(colleague.getId());
			assertThat(ExpenseRequestLifecycleTest.this.service.get(created.id(), colleagueCaller).id())
				.isEqualTo(created.id());
		}

		@Test
		@DisplayName("a caller outside the department scope cannot see a submitted request")
		void outOfScopeHidden() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			ExpenseRequestLifecycleTest.this.service.submit(created.id(), caller());
			User outsider = RequestFixtures.user(ExpenseRequestLifecycleTest.this.users, null);
			EdamPrincipal outsiderCaller = new EdamPrincipal(outsider.getId(), "E", "outsider", "Outsider", Set.of(),
					Set.of(), Set.of());
			assertThatThrownBy(() -> ExpenseRequestLifecycleTest.this.service.get(created.id(), outsiderCaller))
				.isInstanceOf(com.edam.common.exception.ResourceNotFoundException.class);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Cancellation and terminal states
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("cancellation")
	class Cancellation {

		@Test
		@DisplayName("the requester can cancel a draft, recording the reason")
		void cancelDraft() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			RequestResponse cancelled = ExpenseRequestLifecycleTest.this.service.cancel(created.id(),
					"Raised in error", caller());
			assertThat(cancelled.status()).isEqualTo("CANCELLED");
			assertThat(cancelled.terminal()).isTrue();
			ExpenseRequest reloaded = ExpenseRequestLifecycleTest.this.requests.findById(created.id()).orElseThrow();
			assertThat(reloaded.getCancelReason()).isEqualTo("Raised in error");
			assertThat(reloaded.getCancelledAt()).isNotNull();
			assertThat(reloaded.getCancelledBy()).isEqualTo(ExpenseRequestLifecycleTest.this.requester.getId());
		}

		@Test
		@DisplayName("a cancelled request cannot be submitted")
		void cancelledCannotSubmit() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			ExpenseRequestLifecycleTest.this.service.cancel(created.id(), "Raised in error", caller());
			assertThatThrownBy(() -> ExpenseRequestLifecycleTest.this.service.submit(created.id(), caller()))
				.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("a blank cancellation reason is refused")
		void blankReasonRefused() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			assertThatThrownBy(() -> ExpenseRequestLifecycleTest.this.service.cancel(created.id(), "   ", caller()))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("reason is required");
		}

		@Test
		@DisplayName("cancelling twice is refused")
		void doubleCancelRefused() {
			RequestResponse created = ExpenseRequestLifecycleTest.this.service.create(
					opexPayload(List.of(quotation("Q-1", "1000.0000"))), caller());
			ExpenseRequestLifecycleTest.this.service.cancel(created.id(), "Raised in error", caller());
			assertThatThrownBy(
					() -> ExpenseRequestLifecycleTest.this.service.cancel(created.id(), "Again", caller()))
				.isInstanceOf(BusinessException.class);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Rollback
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("transactional integrity")
	class Integrity {

		/**
		 * A failure during creation must leave no header behind.
		 *
		 * <p>The failure is provoked with a duplicate document reference: the second reference to the same
		 * media asset is refused by the service after the header has been flushed, which is precisely the
		 * situation that would leave an orphan header if the transaction boundary were wrong.
		 */
		@Test
		@DisplayName("a failed create leaves no request behind")
		void failedCreateLeavesNothingBehind() {
			UUID media = RequestFixtures.mediaAsset(ExpenseRequestLifecycleTest.this.jdbc,
					ExpenseRequestLifecycleTest.this.requester.getId());
			CreateRequest payload = new CreateRequest(ExpenseRequestLifecycleTest.this.opexType.getId(), ExpenseRequestLifecycleTest.this.opexCategory.getId(), null,
					ExpenseRequestLifecycleTest.this.nature.getId(), ExpenseRequestLifecycleTest.this.department.getId(), null, ExpenseRequestLifecycleTest.this.currency.getId(),
					RequestFixtures.money("1000.0000"), "x", recurringOpex(), null,
					List.of(new AllocationPayload(ExpenseRequestLifecycleTest.this.department.getId(), null, null,
							RequestFixtures.money("1000.0000"), RequestFixtures.money("100.0000"))),
					null, List.of(quotation("Q-1", "1000.0000")), null, null, null, null,
					List.of(new com.edam.expenserequest.dto.ExpenseRequestDtos.DocumentReferencePayload(media,
							"OTHER", false),
							new com.edam.expenserequest.dto.ExpenseRequestDtos.DocumentReferencePayload(media, "OTHER",
									false)));
			long before = ExpenseRequestLifecycleTest.this.requests.count();
			assertThatThrownBy(() -> ExpenseRequestLifecycleTest.this.service.create(payload, caller()))
				.isInstanceOf(BusinessException.class);
			// Not wrapped in a test transaction, so this observes what was actually committed: the service's
			// own transaction rolled back and no header survived.
			assertThat(ExpenseRequestLifecycleTest.this.requests.count()).isEqualTo(before);
		}
	}

}
