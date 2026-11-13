package com.edam.approvallimits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.edam.approvallimits.model.DepartmentApprovalLimit;
import com.edam.approvallimits.model.RecommendingAuthority;
import com.edam.approvallimits.repository.DepartmentApprovalLimitRepository;
import com.edam.approvallimits.repository.RecommendingAuthorityRepository;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The approval-limit and recommending-authority endpoints, over HTTP, against real PostgreSQL.
 *
 * <h2>What this class exists to prove, and why it was missing</h2>
 * Phase 2 shipped both controllers with service-level tests only. That left three things unproven: that the
 * endpoints are reachable and authorized as declared, that a payload is bound and validated as intended, and
 * that a write actually persists through the whole stack. A service test proves the second half of each and
 * none of the first.
 *
 * <h2>Two permissions, tested separately</h2>
 * Reading limits needs {@code approval-limit:view}; writing them needs {@code approval-limit:manage}. The
 * tests below assert both directions: a viewer cannot write, and a writer's read still works. Asserting only
 * the refusal would not catch an endpoint that refused everyone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Approval limit and recommending authority HTTP surface")
class ApprovalLimitApiTest extends AbstractPostgreSqlIntegrationTest {

	private static final String LIMITS = "/api/v1/approval-limits";

	private static final String AUTHORITIES = "/api/v1/recommending-authorities";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DepartmentApprovalLimitRepository limits;

	@Autowired
	private RecommendingAuthorityRepository authorities;

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

	private User holder;

	@BeforeEach
	void setUp() {
		OrganizationProfile profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.currency = this.currencies.findById(profile.getDefaultCurrencyId()).orElseThrow();
		this.department = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.holder = RequestFixtures.user(this.users, this.department.getId());
	}

	// ---------------------------------------------------------------------------------------------
	// Fixtures
	// ---------------------------------------------------------------------------------------------

	private EdamPrincipal principal(UUID userId, String... permissions) {
		return new EdamPrincipal(userId, "EMP", "limits-" + userId, "Limit Tester", Set.of(),
				Set.of(permissions), Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())));
	}

	private EdamPrincipal manager() {
		return principal(this.holder.getId(), Permissions.APPROVAL_LIMIT_VIEW,
				Permissions.APPROVAL_LIMIT_MANAGE, Permissions.RECOMMENDING_AUTHORITY_VIEW,
				Permissions.RECOMMENDING_AUTHORITY_MANAGE);
	}

	private EdamPrincipal viewer() {
		return principal(this.holder.getId(), Permissions.APPROVAL_LIMIT_VIEW);
	}

	private String limitBody(String amount, UUID userId) {
		return """
				{
				  "departmentId": "%s",
				  "userId": %s,
				  "expenseType": "BOTH",
				  "currencyId": "%s",
				  "approvalLimit": %s
				}
				""".formatted(this.department.getId(),
				userId == null ? "null" : "\"" + userId + "\"", this.currency.getId(), amount);
	}

	// ---------------------------------------------------------------------------------------------
	// Authorization
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("authorization")
	class Authorization {

		@Test
		@DisplayName("listing limits requires the view permission")
		void listRequiresPermission() throws Exception {
			ApprovalLimitApiTest.this.mockMvc
				.perform(get(LIMITS).with(user(principal(UUID.randomUUID(), Permissions.REQUEST_VIEW_OWN)))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("EDAM-2005"));
		}

		@Test
		@DisplayName("a viewer may read but not create")
		void viewerCannotWrite() throws Exception {
			ApprovalLimitApiTest.this.mockMvc
				.perform(get(LIMITS).with(user(viewer())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());

			ApprovalLimitApiTest.this.mockMvc
				.perform(post(LIMITS).with(csrf()).with(user(viewer()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(limitBody("100000.0000", null))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("EDAM-2005"));
		}

		@Test
		@DisplayName("an anonymous caller is refused")
		void anonymousRefused() throws Exception {
			ApprovalLimitApiTest.this.mockMvc
				.perform(get(LIMITS).with(csrf()).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Validation
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("validation")
	class Validation {

		@Test
		@DisplayName("a limit of zero is refused before any row is written")
		void zeroLimitRefused() throws Exception {
			long before = ApprovalLimitApiTest.this.limits.count();
			ApprovalLimitApiTest.this.mockMvc
				.perform(post(LIMITS).with(csrf()).with(user(manager()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(limitBody("0.0000", null))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("EDAM-1001"));
			assertThat(ApprovalLimitApiTest.this.limits.count()).isEqualTo(before);
		}

		@Test
		@DisplayName("a missing currency is refused")
		void missingCurrencyRefused() throws Exception {
			String body = """
					{
					  "departmentId": "%s",
					  "expenseType": "OPEX",
					  "approvalLimit": 5000.0000
					}
					""".formatted(this.department.getId());
			ApprovalLimitApiTest.this.mockMvc
				.perform(post(LIMITS).with(csrf()).with(user(manager()))
					.contentType(MediaType.APPLICATION_JSON).content(body)
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("EDAM-1001"));
		}

		@Test
		@DisplayName("an unknown department is a 404, not a validation error")
		void unknownDepartmentNotFound() throws Exception {
			String body = """
					{
					  "departmentId": "%s",
					  "expenseType": "OPEX",
					  "currencyId": "%s",
					  "approvalLimit": 5000.0000
					}
					""".formatted(UUID.randomUUID(), this.currency.getId());
			ApprovalLimitApiTest.this.mockMvc
				.perform(post(LIMITS).with(csrf()).with(user(manager()))
					.contentType(MediaType.APPLICATION_JSON).content(body)
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Persistence
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("persistence")
	class Persistence {

		@Test
		@DisplayName("a created department-wide limit is readable afterwards")
		void createThenRead() throws Exception {
			String created = ApprovalLimitApiTest.this.mockMvc
				.perform(post(LIMITS).with(csrf()).with(user(manager()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(limitBody("250000.0000", null))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.approvalLimit").value(250000.0000))
				.andReturn()
				.getResponse()
				.getContentAsString();

			String id = created.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
			ApprovalLimitApiTest.this.mockMvc
				.perform(get(LIMITS + "/" + id).with(user(viewer())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.departmentId").value(this.department.getId().toString()))
				.andExpect(jsonPath("$.data.userId").doesNotExist());

			// The row is committed, not merely returned: read it back from the repository without MockMvc.
			assertThat(ApprovalLimitApiTest.this.limits.findById(UUID.fromString(id))).isPresent();
		}

		@Test
		@DisplayName("a personal limit records its holder")
		void personalLimitRecordsHolder() throws Exception {
			ApprovalLimitApiTest.this.mockMvc
				.perform(post(LIMITS).with(csrf()).with(user(manager()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(limitBody("75000.0000", this.holder.getId()))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.userId").value(this.holder.getId().toString()));
		}

		@Test
		@DisplayName("updating an amount is persisted")
		void updatePersists() throws Exception {
			DepartmentApprovalLimit existing = ApprovalLimitApiTest.this.limits
				.save(new DepartmentApprovalLimit(this.department.getId(), null,
						DepartmentApprovalLimit.ExpenseType.OPEX, this.currency.getId(),
						new BigDecimal("1000.0000"), Instant.now().minusSeconds(60), null));

			ApprovalLimitApiTest.this.mockMvc
				.perform(put(LIMITS + "/" + existing.getId()).with(csrf()).with(user(manager()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "departmentId": "%s",
							  "expenseType": "OPEX",
							  "currencyId": "%s",
							  "approvalLimit": 2500.0000
							}
							""".formatted(this.department.getId(), this.currency.getId()))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.approvalLimit").value(2500.0000));

			DepartmentApprovalLimit reloaded = ApprovalLimitApiTest.this.limits.findById(existing.getId())
				.orElseThrow();
			assertThat(reloaded.getApprovalLimit()).isEqualByComparingTo("2500.0000");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Authority check
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("authority check")
	class AuthorityCheckEndpoint {

		@Test
		@DisplayName("reports a candidate as permitted when their limit covers the amount")
		void permittedCandidate() throws Exception {
			ApprovalLimitApiTest.this.limits.save(new DepartmentApprovalLimit(this.department.getId(),
					this.holder.getId(), DepartmentApprovalLimit.ExpenseType.BOTH, this.currency.getId(),
					new BigDecimal("500000.0000"), Instant.now().minusSeconds(60), null));
			String body = """
					{
					  "departmentId": "%s",
					  "expenseType": "OPEX",
					  "currencyId": "%s",
					  "amount": 1000.0000,
					  "candidateUserIds": ["%s"]
					}
					""".formatted(this.department.getId(), this.currency.getId(), this.holder.getId());
			ApprovalLimitApiTest.this.mockMvc
				.perform(post(LIMITS + "/authority-check").with(csrf()).with(user(viewer()))
					.contentType(MediaType.APPLICATION_JSON).content(body)
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.permittedUserIds[0]").value(this.holder.getId().toString()));
		}

		@Test
		@DisplayName("a candidate with no limit is reported with a reason, never silently permitted")
		void unconfiguredCandidateExplained() throws Exception {
			String body = """
					{
					  "departmentId": "%s",
					  "expenseType": "OPEX",
					  "currencyId": "%s",
					  "amount": 1000.0000,
					  "candidateUserIds": ["%s"]
					}
					""".formatted(this.department.getId(), this.currency.getId(), UUID.randomUUID());
			ApprovalLimitApiTest.this.mockMvc
				.perform(post(LIMITS + "/authority-check").with(csrf()).with(user(viewer()))
					.contentType(MediaType.APPLICATION_JSON).content(body)
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.permittedUserIds").isEmpty())
				.andExpect(jsonPath("$.data.reasons[0]").value(org.hamcrest.Matchers
					.containsString("No approval limit is configured")));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Recommending authorities
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("recommending authorities")
	class Authorities {

		@Test
		@DisplayName("a designations's holder and classification are persisted and returned")
		void createAndRead() throws Exception {
			String body = """
					{
					  "departmentId": "%s",
					  "userId": "%s",
					  "expenseType": "CAPEX"
					}
					""".formatted(this.department.getId(), this.holder.getId());
			String created = ApprovalLimitApiTest.this.mockMvc
				.perform(post(AUTHORITIES).with(csrf()).with(user(manager()))
					.contentType(MediaType.APPLICATION_JSON).content(body)
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.userId").value(this.holder.getId().toString()))
				.andExpect(jsonPath("$.data.expenseType").value("CAPEX"))
				.andReturn()
				.getResponse()
				.getContentAsString();

			String id = created.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
			RecommendingAuthority reloaded = ApprovalLimitApiTest.this.authorities.findById(UUID.fromString(id))
				.orElseThrow();
			assertThat(reloaded.getUserId()).isEqualTo(this.holder.getId());
			assertThat(reloaded.getExpenseType().name()).isEqualTo("CAPEX");
		}

		@Test
		@DisplayName("reading authorities needs the view permission")
		void viewPermissionRequired() throws Exception {
			ApprovalLimitApiTest.this.mockMvc
				.perform(get(AUTHORITIES).with(user(principal(UUID.randomUUID(), Permissions.APPROVAL_LIMIT_VIEW)))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("a missing holder is refused")
		void missingHolderRefused() throws Exception {
			ApprovalLimitApiTest.this.mockMvc
				.perform(post(AUTHORITIES).with(csrf()).with(user(manager()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "departmentId": "%s",
							  "expenseType": "OPEX"
							}
							""".formatted(this.department.getId()))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("EDAM-1001"));
		}
	}
}
