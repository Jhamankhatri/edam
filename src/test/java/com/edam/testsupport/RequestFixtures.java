package com.edam.testsupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.model.ExpenseCategory.ExpenseType;
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
import com.edam.organization.model.OrganizationUnit;
import com.edam.organization.model.OrganizationUnitType;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.organization.repository.OrganizationProfileRepository;
import com.edam.organization.repository.OrganizationUnitRepository;
import com.edam.organization.repository.OrganizationUnitTypeRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

/**
 * Builders for the configuration a request needs.
 *
 * <h2>Why each test builds its own fixtures</h2>
 * The migrations deliberately ship no business seed data — a bank's categories are its own decision — so
 * there is nothing to select from. Fixtures use random codes so tests neither collide with each other nor
 * depend on a row another test happened to leave behind. Nothing is cleaned up: the container is ephemeral,
 * and a delete would risk testing against a partially removed graph.
 *
 * <h2>The organization profile is created once</h2>
 * A partial unique index permits exactly one active profile, so it is created if absent. The base currency it
 * names is what every request conversion uses, which means the profile must exist before any request can be
 * created — that dependency is real, not a test artefact.
 *
 * <h2>Departments need their own organization unit</h2>
 * {@code uk_departments_unit} makes {@code organization_unit_id} unique, so two departments cannot share a
 * unit. The builder therefore creates a unit per department rather than reusing one.
 */
public final class RequestFixtures {

	private RequestFixtures() {
	}

	/**
	 * A sales tax authority, created if the code is new.
	 *
	 * <p>{@code capex_sta_details.tax_authority_id} is a foreign key, so a CAPEX submission that claims a
	 * sales tax authority (§8.3 makes one mandatory) needs a real row to point at.
	 */
	public static TaxAuthority taxAuthority(TaxAuthorityRepository authorities, String code) {
		return authorities.findByCodeIgnoreCase(code).orElseGet(() -> {
			TaxAuthority authority = new TaxAuthority();
			authority.setName("Fixture " + code);
			authority.setCode(code);
			return authorities.save(authority);
		});
	}

	/**
	 * Inserts a media asset and returns its id.
	 *
	 * <p>{@code request_documents.media_asset_id} is a foreign key, so a document can only be attached to an
	 * asset that exists. There is no {@code MediaAsset} entity yet — the upload path is Phase 4 — so the row
	 * is written with plain SQL here rather than through a repository that would exist only for tests.
	 */
	public static UUID mediaAsset(JdbcTemplate jdbc, UUID uploadedBy) {
		UUID id = UUID.randomUUID();
		new NamedParameterJdbcTemplate(jdbc).update("""
				insert into media_assets (id, original_file_name, storage_provider, storage_reference, \
				mime_type, file_size, uploaded_by, uploaded_at)
				values (:id, :name, 'LOCAL', :reference, 'application/pdf', 1024, :uploadedBy, CURRENT_TIMESTAMP)
				""", new MapSqlParameterSource().addValue("id", id)
			.addValue("name", "fixture-" + id + ".pdf")
			.addValue("reference", "file:///tmp/" + id + ".pdf")
			.addValue("uploadedBy", uploadedBy));
		return id;
	}

	private static String randomSuffix() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	/**
	 * A currency with a unique three-letter code.
	 *
	 * <p>The column is {@code VARCHAR(3)} with {@code CHECK (currency_code ~ '^[A-Z]{3}$')}, so the code must
	 * be exactly three <em>letters</em>. Deriving the third character from a random hex suffix produced a
	 * digit roughly ten times in sixteen, and the schema rejected it — a fixture bug that looked like a
	 * product failure. Letters are drawn explicitly instead.
	 */
	public static Currency currency(CurrencyRepository currencies, String prefix) {
		java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
		StringBuilder code = new StringBuilder();
		// Keep the caller's prefix where it is already alphabetic, then pad to exactly three letters.
		String seed = (prefix == null) ? "" : prefix.replaceAll("[^A-Za-z]", "").toUpperCase(java.util.Locale.ROOT);
		if (!seed.isEmpty()) {
			code.append(seed.charAt(0));
		}
		while (code.length() < 3) {
			code.append((char) ('A' + random.nextInt(26)));
		}
		String finalCode = code.substring(0, 3);
		return currencies.findByCode(finalCode).orElseGet(() -> {
			Currency currency = new Currency();
			currency.setCode(finalCode);
			currency.setName("Test currency " + finalCode);
			currency.setDecimalPlaces((short) 2);
			return currencies.save(currency);
		});
	}

	public static ExpenseRequestType requestType(ExpenseRequestTypeRepository types, String code) {
		return types.findByCodeIgnoreCase(code).orElseGet(() -> {
			ExpenseRequestType type = new ExpenseRequestType();
			type.setCode(code);
			type.setName("Test request type " + code);
			return types.save(type);
		});
	}

	public static ExpenseNature nature(ExpenseNatureRepository natures, String code) {
		return natures.findByCodeIgnoreCase(code).orElseGet(() -> {
			ExpenseNature nature = new ExpenseNature();
			nature.setCode(code);
			nature.setName("Test nature " + code);
			return natures.save(nature);
		});
	}

	public static ExpenseCategory category(ExpenseCategoryRepository categories, String code, ExpenseType type) {
		return categories.findByCodeIgnoreCase(code).orElseGet(() -> {
			ExpenseCategory category = new ExpenseCategory();
			category.setCode(code);
			category.setName("Test category " + code);
			category.setExpenseType(type);
			return categories.save(category);
		});
	}

	/**
	 * A department, with its own organization unit.
	 *
	 * <p>The unit type {@code DEPARTMENT} is seeded by {@code V12}; the unit itself is the bank's data and is
	 * created here because a department cannot exist without one.
	 */
	public static Department department(DepartmentRepository departments, OrganizationUnitRepository units,
			OrganizationUnitTypeRepository unitTypes) {
		OrganizationUnitType type = unitTypes.findByCode(OrganizationUnitType.DEPARTMENT)
			.orElseThrow(() -> new IllegalStateException(
					"The DEPARTMENT organization unit type is seeded by V12 and must exist."));
		String suffix = randomSuffix();
		OrganizationUnit unit = new OrganizationUnit();
		unit.setUnitTypeId(type.getId());
		unit.setCode("U-" + suffix);
		unit.setName("Unit " + suffix);
		OrganizationUnit savedUnit = units.save(unit);
		Department department = new Department();
		department.setOrganizationUnitId(savedUnit.getId());
		department.setCode("D-" + suffix);
		department.setName("Department " + suffix);
		return departments.save(department);
	}

	/** Ensures an active organization profile exists, naming a base currency. */
	public static OrganizationProfile organizationProfile(OrganizationProfileRepository profiles,
			CurrencyRepository currencies) {
		return profiles.findFirstByActiveTrue().orElseGet(() -> {
			Currency base = currency(currencies, "PK");
			OrganizationProfile profile = new OrganizationProfile();
			profile.setOrganizationName("Test Bank");
			profile.setDefaultCurrencyId(base.getId());
			return profiles.save(profile);
		});
	}

	/** A unique active user, optionally placed in a department. */
	public static User user(UserRepository users, UUID departmentId) {
		String suffix = randomSuffix();
		User user = new User();
		user.setUsername("p2-" + suffix);
		user.setFirstName("Phase");
		user.setLastName("Two");
		user.setEmail("p2-" + suffix + "@bank.example");
		user.setEmployeeCode("P2-" + suffix);
		user.setActive(true);
		user.setDepartmentId(departmentId);
		// A hash is required by the schema; these users never authenticate, and no password in the test
		// suite is a real credential.
		user.changePassword("{noop}not-a-real-credential", Instant.now());
		return users.save(user);
	}

	/** A monetary helper so tests read as amounts rather than BigDecimal plumbing. */
	public static BigDecimal money(String value) {
		return new BigDecimal(value);
	}

	/**
	 * Publishes a one-step workflow for a request type, through the real configuration service.
	 *
	 * <h2>Why tests need this now</h2>
	 * Phase 3 made submission refuse a request type with no published workflow, which is the behaviour
	 * BRD 24.1 asks for. Before that, a Phase 2 test could submit without configuring anything. The correct
	 * response is to give the tests a configured workflow, not to relax the rule.
	 *
	 * <h2>Why it goes through the service rather than SQL</h2>
	 * A workflow inserted with hand-written SQL would not prove that the configuration path produces something
	 * submittable. Driving {@code createDefinition → createDraft → publish} means every test that submits a
	 * request also exercises the real publication rules — the derived states, the validation pass and the
	 * single-published index. If configuration regresses, every submission test fails with it, which is the
	 * coupling the BRD describes.
	 *
	 * <p>The step is a single configured individual so that resolution is deterministic and needs no
	 * department-hierarchy data. Tests that care about resolution strategies configure their own workflow.
	 *
	 * @return the published version's id, so a test can assert the request pinned it
	 */
	public static java.util.UUID publishedWorkflow(
			com.edam.workflow.service.WorkflowConfigurationService configuration,
			ExpenseRequestType requestType, java.util.UUID approverId, String codeSuffix) {
		// Idempotent on purpose. Several tests share one request type, and two active definitions for the
		// same request type would make the lookup that selects a workflow ambiguous — it returns the first
		// match, so the second test would silently route through the first test's chain. Publishing only
		// when the submission lookup would otherwise refuse keeps the fixture consistent with the rule it
		// is satisfying.
		try {
			return configuration.requirePublishedForRequestType(requestType.getId()).getId();
		}
		catch (com.edam.common.exception.BusinessException alreadyConfigured) {
			// No published revision yet: fall through and create one.
		}
		com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = configuration.createDefinition(
				new com.edam.workflow.dto.WorkflowDtos.SaveDefinitionRequest("WF-" + codeSuffix,
						"Fixture workflow " + codeSuffix, "Created by RequestFixtures", requestType.getId()));
		com.edam.workflow.dto.WorkflowDtos.VersionResponse draft = configuration.createDraft(definition.id(),
				new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
						java.util.List.of(new com.edam.workflow.dto.WorkflowDtos.StepRequest("ANY-APPROVER",
								"Any approver", (short) 1,
								com.edam.workflow.model.WorkflowStep.ApprovalMode.PARALLEL, (short) 1,
								java.util.List.of(new com.edam.workflow.dto.WorkflowDtos.ApproverRequest(
										com.edam.workflow.model.WorkflowStepApprover.ApproverType.USER, null,
										approverId, null)))),
						java.util.List.of(), null));
		return configuration.publish(draft.id(),
				new com.edam.workflow.dto.WorkflowDtos.PublishRequest(null), approverId).id();
	}

}
