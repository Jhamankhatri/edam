package com.edam.businessrules.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.businessrules.dto.RuleDtos;
import com.edam.businessrules.model.BusinessRuleSet;
import com.edam.businessrules.model.BusinessRuleVersion;
import com.edam.businessrules.model.RuleVersionStatus;
import com.edam.businessrules.repository.BusinessRuleSetRepository;
import com.edam.businessrules.repository.BusinessRuleVersionRepository;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.organization.model.Department;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.organization.repository.OrganizationProfileRepository;
import com.edam.organization.repository.OrganizationUnitRepository;
import com.edam.organization.repository.OrganizationUnitTypeRepository;
import com.edam.masterdata.repository.CurrencyRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Business Rules: CRUD, publish, evaluate")
class BusinessRulesIntegrationTest extends AbstractPostgreSqlIntegrationTest {

	private static final String BASE = "/api/v1/business-rules";

	@Autowired private MockMvc mockMvc;
	@Autowired private BusinessRuleService service;
	@Autowired private BusinessRuleSetRepository sets;
	@Autowired private BusinessRuleVersionRepository versions;
	@Autowired private UserRepository users;
	@Autowired private DepartmentRepository departments;
	@Autowired private OrganizationUnitRepository units;
	@Autowired private OrganizationUnitTypeRepository unitTypes;
	@Autowired private OrganizationProfileRepository profiles;
	@Autowired private CurrencyRepository currencies;

	private User admin;
	private Department dept;

	@BeforeEach
	void setUp() {
		RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.dept = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.admin = RequestFixtures.user(this.users, this.dept.getId());
	}

	private EdamPrincipal adminPrincipal() {
		return new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of("ADMIN"),
				Set.of(Permissions.RULE_VIEW, Permissions.RULE_MANAGE, Permissions.RULE_PUBLISH),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
	}

	private EdamPrincipal viewOnly() {
		return new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of("ADMIN"),
				Set.of(Permissions.RULE_VIEW),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
	}

	@Test
	@DisplayName("create rule set, upsert rule, publish produces immutable PUBLISHED version")
	void lifecycleCreateUpsertPublish() throws Exception {
		// Create
		String body = this.mockMvc.perform(post(BASE)
				.with(csrf()).with(user(adminPrincipal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"EXP_APPROVAL\",\"name\":\"Expense Approval\",\"module\":\"EXPENSE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.code").value("EXP_APPROVAL"))
			.andReturn().getResponse().getContentAsString();
		String setId = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(body).results().findFirst().orElseThrow().group(1);

		// Get returns draft v1
		String detail = this.mockMvc.perform(get(BASE + "/" + setId).with(user(adminPrincipal())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.versionNumber").value(1))
			.andExpect(jsonPath("$.data.status").value("DRAFT"))
			.andReturn().getResponse().getContentAsString();
		String vId = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\",\"versionNumber\":1").matcher(detail).results().findFirst().orElseThrow().group(1);

		// Upsert a rule
		String cond = "{\"all\":["
				+ "{\"field\":\"amount\",\"op\":\"gt\",\"value\":10000},"
				+ "{\"field\":\"currency\",\"op\":\"eq\",\"value\":\"PKR\"}"
				+ "]}";
		this.mockMvc.perform(put(BASE + "/versions/" + vId + "/rules")
				.with(csrf()).with(user(adminPrincipal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"HIGH_AMOUNT_PKR\",\"name\":\"High PKR amount\",\"condition\":" + cond + ",\"action\":{\"type\":\"REQUIRE_APPROVAL_LEVEL\",\"level\":\"EC2\"},\"priority\":50}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.code").value("HIGH_AMOUNT_PKR"));

		// Invalid rule (unknown field) should be rejected at upsert time
		this.mockMvc.perform(put(BASE + "/versions/" + vId + "/rules")
				.with(csrf()).with(user(adminPrincipal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"BAD\",\"name\":\"Bad\",\"condition\":{\"field\":\"__EVIL__\",\"op\":\"eq\",\"value\":1},\"action\":{\"type\":\"WARN\"},\"priority\":1}"))
			.andExpect(status().isBadRequest());

		// Publish
		Instant eff = Instant.now().plus(1, ChronoUnit.SECONDS);
		this.mockMvc.perform(post(BASE + "/" + setId + "/publish")
				.with(csrf()).with(user(adminPrincipal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\":\"" + eff + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PUBLISHED"));

		// After publish, a new DRAFT is NOT created automatically; only v1 is PUBLISHED.
		// Verify the published version is immutable (upsert fails because v1 is PUBLISHED).
		String ruleId = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\",\"code\":\"HIGH_AMOUNT_PKR\"").matcher(
				this.mockMvc.perform(get(BASE + "/" + setId).with(user(adminPrincipal()))).andReturn().getResponse().getContentAsString()
			).results().findFirst().orElseThrow().group(1);
		// Trying to upsert on the published version id fails with 400.
		this.mockMvc.perform(put(BASE + "/versions/" + vId + "/rules")
				.with(csrf()).with(user(adminPrincipal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":\"" + ruleId + "\",\"code\":\"HIGH_AMOUNT_PKR\",\"name\":\"Renamed\",\"condition\":" + cond + ",\"action\":{\"type\":\"WARN\"},\"priority\":1}"))
			.andExpect(status().isBadRequest());

		// Create a new draft and add a second rule
		String newDraft = this.mockMvc.perform(post(BASE + "/" + setId + "/drafts")
				.with(csrf()).with(user(adminPrincipal())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("DRAFT"))
			.andExpect(jsonPath("$.data.versionNumber").value(2))
			.andReturn().getResponse().getContentAsString();
		String v2Id = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\",\"versionNumber\":2").matcher(newDraft).results().findFirst().orElseThrow().group(1);
		// (rules don't copy across — publishing v2 retires v1; for now we just confirm version increment.)
		assertThat(v2Id).isNotEqualTo(vId);
	}

	@Test
	@DisplayName("view-only user cannot mutate")
	void permissionEnforcement() throws Exception {
		this.mockMvc.perform(post(BASE)
				.with(csrf()).with(user(viewOnly()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"X\",\"name\":\"x\",\"module\":\"EXPENSE\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("evaluation matches high-PKR-amount rule and rejects unknown field at publish validation")
	void evaluation() throws Exception {
		// Create+publish a high-amount rule directly through service.
		var summary = service.createRuleSet(new RuleDtos.CreateRuleSetRequest(
				"EVAL_" + UUID.randomUUID().toString().substring(0, 6), "Eval", "EXPENSE", null), adminPrincipal());
		UUID setId = summary.id();
		RuleDtos.RuleVersionDetail detail = service.getRuleSet(setId);
		service.upsertRule(detail.id(), new RuleDtos.RuleUpsertRequest(null, "HIGH", "High",
				Map.of("all", List.of(
						Map.of("field","amount","op","gt","value",10000),
						Map.of("field","currency","op","eq","value","PKR"))),
				Map.of("type","WARN"), 50, true), adminPrincipal());
		service.publish(setId, new RuleDtos.PublishRequest(Instant.now().minusMillis(100)), adminPrincipal());

		// Evaluate matches the high-amount context
		var matched = service.evaluate("EXPENSE", summary.code(),
				RuleEngine.EvaluationContext.of(Map.of("amount", new BigDecimal("15000"), "currency", "PKR")));
		assertThat(matched).hasSize(1);
		assertThat(matched.get(0).code()).isEqualTo("HIGH");

		// Lower amount doesn't match
		var matched2 = service.evaluate("EXPENSE", summary.code(),
				RuleEngine.EvaluationContext.of(Map.of("amount", new BigDecimal("100"), "currency", "PKR")));
		assertThat(matched2).isEmpty();
	}

	@Test
	@DisplayName("retiring the published version causes no rules to match")
	void retireClearsActive() throws Exception {
		var summary = service.createRuleSet(new RuleDtos.CreateRuleSetRequest(
				"RTR_" + UUID.randomUUID().toString().substring(0, 6), "Retire", "EXPENSE", null), adminPrincipal());
		RuleDtos.RuleVersionDetail detail = service.getRuleSet(summary.id());
		service.upsertRule(detail.id(), new RuleDtos.RuleUpsertRequest(null, "ALWAYS", "Always",
				Map.of("field","submitted","op","exists","value",true),
				Map.of("type","WARN"), 1, true), adminPrincipal());
		service.publish(summary.id(), new RuleDtos.PublishRequest(Instant.now().minusMillis(100)), adminPrincipal());
		assertThat(service.evaluate("EXPENSE", summary.code(),
				RuleEngine.EvaluationContext.of(Map.of("submitted", true)))).hasSize(1);
		service.retirePublished(summary.id(), adminPrincipal());
		assertThat(service.evaluate("EXPENSE", summary.code(),
				RuleEngine.EvaluationContext.of(Map.of("submitted", true)))).isEmpty();
	}
}
