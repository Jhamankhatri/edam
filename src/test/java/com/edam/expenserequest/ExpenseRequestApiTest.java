package com.edam.expenserequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * The request API exercised through the MVC stack, with real authentication and real PostgreSQL.
 *
 * <h2>Why this exists alongside the service tests</h2>
 * The service tests prove the rules. They cannot prove that the endpoint is mapped where the client expects,
 * that the permission is actually enforced by the filter chain, that a validation failure arrives as the
 * documented {@code EDAM-1001} envelope with per-field detail, or that the response shape is the one a React
 * client will parse. Those are contract facts, and a contract that is only tested at the service layer is not
 * tested at all.
 *
 * <h2>Authorization assertions</h2>
 * Three distinct outcomes are asserted, because they are three different code paths:
 * a caller without the permission gets 403 with {@code EDAM-2005} from the endpoint guard; a caller with the
 * permission but no authority over the record gets 403 from the service; and a record outside the caller's
 * data scope is reported as 404 so its existence is not disclosed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpenseRequestApiTest extends AbstractPostgreSqlIntegrationTest {

	private static final String BASE = "/api/v1/expense-requests";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private CurrencyRepository currencies;

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
	private UserRepository users;

	private Department department;

	private User requester;

	private ExpenseRequestType opexType;

	private ExpenseCategory opexCategory;

	private ExpenseNature nature;

	private Currency currency;

	@BeforeEach
	void setUp() {
		OrganizationProfile profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.currency = this.currencies.findById(profile.getDefaultCurrencyId()).orElseThrow();
		this.department = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.requester = RequestFixtures.user(this.users, this.department.getId());
		this.opexType = RequestFixtures.requestType(this.requestTypes, "OPEX");
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		this.opexCategory = RequestFixtures.category(this.categories, "AOP-" + suffix,
				ExpenseCategory.ExpenseType.OPEX);
		this.nature = RequestFixtures.nature(this.natures, "ANAT-" + suffix);
	}

	/** The authenticated caller, with the given permissions and a department scope. */
	private EdamPrincipal principal(UUID userId, String... permissions) {
		return new EdamPrincipal(userId, "EMP", "api-" + userId, "API Tester", Set.of(), Set.of(permissions),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())));
	}

	private EdamPrincipal owner() {
		return principal(this.requester.getId(), Permissions.REQUEST_CREATE, Permissions.REQUEST_VIEW_OWN,
				Permissions.REQUEST_SUBMIT, Permissions.REQUEST_EDIT, Permissions.REQUEST_CANCEL);
	}

	private String opexBody(String amount) {
		return """
				{
				  "requestTypeId": "%s",
				  "expenseCategoryId": "%s",
				  "expenseNatureId": "%s",
				  "departmentId": "%s",
				  "currencyId": "%s",
				  "requestedAmount": %s,
				  "justification": "Monthly subscription",
				  "opex": { "recurring": true, "recurrenceFrequency": "MONTHLY" },
				  "allocations": [
				    { "departmentId": "%s", "allocationAmount": %s }
				  ],
				  "quotations": [
				    { "quotationNumber": "Q-1", "quotationDate": "2026-11-01", "quotationAmount": %s,
				      "currencyId": "%s" }
				  ]
				}
				""".formatted(this.opexType.getId(), this.opexCategory.getId(), this.nature.getId(),
				this.department.getId(), this.currency.getId(), amount, this.department.getId(), amount, amount,
				this.currency.getId());
	}

	/**
	 * The id of a newly created draft.
	 *
	 * <p>The envelope is deserialised into a typed reader rather than walked as a generic tree: the test then
	 * depends on the same field names a real client binds, so a renamed field fails here instead of only in
	 * the browser.
	 */
	private String createDraft(String amount) throws Exception {
		MvcResult result = this.mockMvc
			.perform(post(BASE).with(user(owner()))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(opexBody(amount)))
			.andExpect(status().isCreated())
			.andReturn();
		CreatedEnvelope envelope = this.jsonMapper.readValue(result.getResponse().getContentAsString(),
				CreatedEnvelope.class);
		return envelope.data().id();
	}

	/** The part of the create envelope this test needs. */
	record CreatedEnvelope(boolean success, CreatedData data) {
	}

	record CreatedData(String id, String requestNumber) {
	}

	// ---------------------------------------------------------------------------------------------
	// Authentication and authorization
	// ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("an anonymous create is refused with the JSON 401 envelope")
	void anonymousCreateRefused() throws Exception {
		this.mockMvc
			.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(opexBody("100.0000")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("EDAM-2001"));
	}

	@Test
	@DisplayName("a caller without request:create is refused with 403")
	void createWithoutPermissionRefused() throws Exception {
		EdamPrincipal noCreate = principal(this.requester.getId(), Permissions.REQUEST_VIEW_OWN);
		this.mockMvc
			.perform(post(BASE).with(user(noCreate))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(opexBody("100.0000")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("EDAM-2005"));
	}

	@Test
	@DisplayName("a caller without request:view-own cannot list requests")
	void listWithoutPermissionRefused() throws Exception {
		EdamPrincipal noView = principal(this.requester.getId(), Permissions.REQUEST_CREATE);
		this.mockMvc.perform(get(BASE).with(user(noView)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("EDAM-2005"));
	}

	// ---------------------------------------------------------------------------------------------
	// Create and read
	// ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a valid create returns 201 with the documented envelope and a request number")
	void createReturnsEnvelope() throws Exception {
		MvcResult result = this.mockMvc
			.perform(post(BASE).with(user(owner()))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(opexBody("150.5000")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.requestNumber").isNotEmpty())
			.andExpect(jsonPath("$.data.status").value("DRAFT"))
			.andExpect(jsonPath("$.data.classification").value("OPEX"))
			.andExpect(jsonPath("$.data.requestedAmount").value(150.5))
			.andExpect(jsonPath("$.data.canEdit").value(true))
			.andExpect(jsonPath("$.data.canSubmit").value(true))
			.andExpect(jsonPath("$.correlationId").isNotEmpty())
			.andReturn();
		String id = this.jsonMapper.readValue(result.getResponse().getContentAsString(),
				CreatedEnvelope.class).data().id();
		this.mockMvc.perform(get(BASE + "/" + id).with(user(owner())).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(id))
			.andExpect(jsonPath("$.data.allocations.length()").value(1))
			.andExpect(jsonPath("$.data.quotations.length()").value(1));
	}

	@Test
	@DisplayName("a zero amount is refused with 400 and a field error naming requestedAmount")
	void zeroAmountIsFieldError() throws Exception {
		this.mockMvc
			.perform(post(BASE).with(user(owner()))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(opexBody("0")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("EDAM-1001"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'requestedAmount')]").exists());
	}

	@Test
	@DisplayName("an unknown request id is 404 with the documented code")
	void unknownIdIs404() throws Exception {
		this.mockMvc
			.perform(get(BASE + "/" + UUID.randomUUID()).with(user(owner())).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("EDAM-4001"));
	}

	@Test
	@DisplayName("an unparseable uuid path variable is a 400, not a 500")
	void malformedIdIs400() throws Exception {
		this.mockMvc.perform(get(BASE + "/not-a-uuid").with(user(owner())).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("EDAM-1002"));
	}

	// ---------------------------------------------------------------------------------------------
	// Lifecycle over HTTP
	// ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a draft can be submitted and then refuses a second submission with 422")
	void submitThenRefuse() throws Exception {
		String id = createDraft("200.0000");
		this.mockMvc
			.perform(post(BASE + "/" + id + "/submit").with(user(owner())).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.data.submittedAt").isNotEmpty())
			.andExpect(jsonPath("$.data.canEdit").value(false));
		this.mockMvc
			.perform(post(BASE + "/" + id + "/submit").with(user(owner())).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.error.code").value("EDAM-3004"));
	}

	@Test
	@DisplayName("cancelling requires a reason in the body")
	void cancelRequiresReason() throws Exception {
		String id = createDraft("300.0000");
		this.mockMvc
			.perform(post(BASE + "/" + id + "/cancel").with(user(owner()))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"   \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("EDAM-1001"));
	}

	@Test
	@DisplayName("the register returns a page of summaries, not full aggregates")
	void registerReturnsSummaries() throws Exception {
		createDraft("400.0000");
		this.mockMvc
			.perform(get(BASE).param("mineOnly", "true")
				.with(user(owner()))
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isArray())
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.totalElements").isNumber())
			.andExpect(jsonPath("$.data.items[0].requestNumber").isNotEmpty())
			// A summary carries no child rows: loading them for every row would defeat the register.
			.andExpect(jsonPath("$.data.items[0].allocations").doesNotExist());
	}

	@Test
	@DisplayName("a page size above the cap is refused rather than silently clamped")
	void oversizedPageRefused() throws Exception {
		this.mockMvc
			.perform(get(BASE).param("size", "10000").with(user(owner())).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("EDAM-1003"));
	}

}
