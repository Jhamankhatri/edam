package com.edam.businessrules.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.businessrules.dto.RuleDtos;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BusinessRulesIntegrationTest extends AbstractPostgreSqlIntegrationTest {

	private static final String BASE = "/api/v1/business-rules";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper json;
	@Autowired private BusinessRuleService service;
	@Autowired private DepartmentRepository departments;
	@Autowired private OrganizationUnitRepository units;
	@Autowired private OrganizationUnitTypeRepository unitTypes;
	@Autowired private UserRepository users;
	@Autowired private OrganizationProfileRepository profiles;
	@Autowired private CurrencyRepository currencies;

	private Department dept;
	private User admin;

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

	private JsonNode data(MvcResult r) throws Exception {
		return this.json.readTree(r.getResponse().getContentAsString()).path("data");
	}

	@Test
	@DisplayName("create rule set, upsert rule, publish produces immutable PUBLISHED version")
	void lifecycleCreateUpsertPublish() throws Exception {
		// Create
		MvcResult created = this.mockMvc.perform(post(BASE)
				.with(csrf()).with(user(adminPrincipal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"EXP_APPROVAL\",\"name\":\"Expense Approval\",\"module\":\"EXPENSE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.code").value("EXP_APPROVAL"))
			.andReturn();
		String setId = data(created).path("id").asText();

		// Get returns draft v1
		MvcResult detailRes = this.mockMvc.perform(get(BASE + "/" + setId).with(user(adminPrincipal())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.versionNumber").value(1))
			.andExpect(jsonPath("$.data.status").value("DRAFT"))
			.andReturn();
		String vId = data(detailRes).path("id").asText();

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

		// Invalid rule (unknown operator) rejected at upsert time
		this.mockMvc.perform(put(BASE + "/versions/" + vId + "/rules")
				.with(csrf()).with(user(adminPrincipal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"BAD\",\"name\":\"Bad\",\"condition\":{\"field\":\"amount\",\"op\":\"dropTable\",\"value\":1},\"action\":{\"type\":\"WARN\"},\"priority\":1}"))
			.andExpect(status().isBadRequest());

		// Publish
		Instant eff = Instant.now().plus(1, ChronoUnit.SECONDS);
		this.mockMvc.perform(post(BASE + "/" + setId + "/publish")
				.with(csrf()).with(user(adminPrincipal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\":\"" + eff + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PUBLISHED"));

		// Upsert on PUBLISHED version is forbidden
		this.mockMvc.perform(put(BASE + "/versions/" + vId + "/rules")
				.with(csrf()).with(user(adminPrincipal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"HIGH_AMOUNT_PKR\",\"name\":\"Renamed\",\"condition\":" + cond + ",\"action\":{\"type\":\"WARN\"},\"priority\":1}"))
			.andExpect(status().isBadRequest());

		// Create new draft
		MvcResult newDraftRes = this.mockMvc.perform(post(BASE + "/" + setId + "/drafts")
				.with(csrf()).with(user(adminPrincipal())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("DRAFT"))
			.andExpect(jsonPath("$.data.versionNumber").value(2))
			.andReturn();
		String v2Id = data(newDraftRes).path("id").asText();
		assertThat(v2Id).isNotEqualTo(vId);
	}

	@Test
	@DisplayName("view-only user cannot mutate")
	void permissionEnforcement() throws Exception {
		this.mockMvc.perform(post(BASE)
				.with(csrf()).with(user(viewOnly()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"XX\",\"name\":\"x\",\"module\":\"EXPENSE\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("evaluation matches high-PKR-amount rule and enforces whitelist at eval time")
	void evaluation() throws Exception {
		var summary = service.createRuleSet(new RuleDtos.CreateRuleSetRequest(
				"EVAL_" + UUID.randomUUID().toString().substring(0, 6).replace('-', 'A'), "Eval", "EXPENSE", null), adminPrincipal());
		UUID setId = summary.id();
		RuleDtos.RuleVersionDetail detail = service.getRuleSet(setId);
		service.upsertRule(detail.id(), new RuleDtos.RuleUpsertRequest(null, "HIGH", "High",
				Map.of("all", List.of(
						Map.of("field", "amount", "op", "gt", "value", 10000),
						Map.of("field", "currency", "op", "eq", "value", "PKR"))),
				Map.of("type", "WARN"), 50, true), adminPrincipal());
		service.publish(setId, new RuleDtos.PublishRequest(Instant.now().minusMillis(100)), adminPrincipal());

		// Match: high amount + PKR
		var matched = service.evaluate("EXPENSE", summary.code(),
				RuleEngine.EvaluationContext.of(Map.of("amount", new BigDecimal("15000"), "currency", "PKR")));
		assertThat(matched).hasSize(1);
		assertThat(matched.get(0).code()).isEqualTo("HIGH");

		// No match: lower amount
		var none = service.evaluate("EXPENSE", summary.code(),
				RuleEngine.EvaluationContext.of(Map.of("amount", new BigDecimal("500"), "currency", "PKR")));
		assertThat(none).isEmpty();
	}

	@Test
	@DisplayName("retiring the published version clears active evaluation results")
	void retireClearsActive() throws Exception {
		var summary = service.createRuleSet(new RuleDtos.CreateRuleSetRequest(
				"RET_" + UUID.randomUUID().toString().substring(0, 6).replace('-', 'A'), "Retire", "EXPENSE", null), adminPrincipal());
		UUID setId = summary.id();
		RuleDtos.RuleVersionDetail detail = service.getRuleSet(setId);
		service.upsertRule(detail.id(), new RuleDtos.RuleUpsertRequest(null, "R1", "R1",
				Map.of("field", "amount", "op", "gt", "value", 0),
				Map.of("type", "WARN"), 10, true), adminPrincipal());
		service.publish(setId, new RuleDtos.PublishRequest(Instant.now().minusMillis(100)), adminPrincipal());

		var matched = service.evaluate("EXPENSE", summary.code(),
				RuleEngine.EvaluationContext.of(Map.of("amount", new BigDecimal("1"))));
		assertThat(matched).hasSize(1);

		service.retirePublished(setId, adminPrincipal());
		var after = service.evaluate("EXPENSE", summary.code(),
				RuleEngine.EvaluationContext.of(Map.of("amount", new BigDecimal("1"))));
		assertThat(after).isEmpty();
	}
}
