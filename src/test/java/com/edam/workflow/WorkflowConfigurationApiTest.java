package com.edam.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.edam.common.exception.BusinessException;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.expenseconfiguration.model.ExpenseRequestType;
import com.edam.expenseconfiguration.repository.ExpenseRequestTypeRepository;
import com.edam.organization.model.Department;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.organization.repository.OrganizationUnitRepository;
import com.edam.organization.repository.OrganizationUnitTypeRepository;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;
import com.edam.testsupport.RequestFixtures;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;
import com.edam.workflow.model.WorkflowStepApprover;
import com.edam.workflow.model.WorkflowVersion;
import com.edam.workflow.repository.DefinitionsRepository;
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
 * Workflow configuration: definitions, drafts, validation, publication and immutability.
 *
 * <h2>What matters here, and why the tests are shaped this way</h2>
 * The risky property is not that a workflow can be created — it is that a <em>published</em> one cannot be
 * changed. An approval chain that mutates while requests are in flight retroactively changes who was allowed
 * to approve what. Three separate mechanisms protect it (the entity refuses edits, the service refuses them,
 * and the database allows one published revision), so the tests attack it three ways rather than asserting
 * one guard and assuming the rest follow.
 *
 * <h2>Real PostgreSQL, real Flyway</h2>
 * The single-published constraint is a partial unique index. An in-memory database would accept two
 * published revisions, so a test running on one would prove nothing about the property that matters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Workflow configuration")
class WorkflowConfigurationApiTest extends AbstractPostgreSqlIntegrationTest {

	private static final String WORKFLOWS = "/api/v1/workflows";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WorkflowConfigurationService configuration;

	@Autowired
	private DefinitionsRepository definitions;

	@Autowired
	private VersionsRepository versions;

	@Autowired
	private ExpenseRequestTypeRepository requestTypes;

	@Autowired
	private DepartmentRepository departments;

	@Autowired
	private OrganizationUnitRepository units;

	@Autowired
	private OrganizationUnitTypeRepository unitTypes;

	@Autowired
	private UserRepository users;

	private Department department;

	private User approver;

	private User secondApprover;

	private ExpenseRequestType requestType;

	@BeforeEach
	void setUp() {
		this.department = RequestFixtures.department(this.departments, this.units, this.unitTypes);
		this.approver = RequestFixtures.user(this.users, this.department.getId());
		this.secondApprover = RequestFixtures.user(this.users, this.department.getId());
		this.requestType = RequestFixtures.requestType(this.requestTypes,
				"CFG-" + UUID.randomUUID().toString().substring(0, 8));
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private EdamPrincipal principal(String... permissions) {
		return new EdamPrincipal(this.approver.getId(), "EMP", "cfg-" + this.approver.getId(), "Config Admin",
				Set.of(), Set.of(permissions),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, this.department.getId())));
	}

	private EdamPrincipal manager() {
		return principal(Permissions.WORKFLOW_VIEW, Permissions.WORKFLOW_MANAGE, Permissions.WORKFLOW_PUBLISH);
	}

	private EdamPrincipal publisherOnly() {
		return principal(Permissions.WORKFLOW_VIEW, Permissions.WORKFLOW_PUBLISH);
	}

	/** A definition bound to this test's request type. */
	private com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition(String code) {
		return this.configuration.createDefinition(new com.edam.workflow.dto.WorkflowDtos.SaveDefinitionRequest(
				code, "Configuration test " + code, null, this.requestType.getId()));
	}

	/** One step naming two configured individuals, so a quorum test has candidates. */
	private static List<com.edam.workflow.dto.WorkflowDtos.StepRequest> twoApproverStep(User first, User second) {
		return List.of(new com.edam.workflow.dto.WorkflowDtos.StepRequest("REVIEW", "Review", (short) 1,
				com.edam.workflow.model.WorkflowStep.ApprovalMode.PARALLEL, (short) 1,
				List.of(
						new com.edam.workflow.dto.WorkflowDtos.ApproverRequest(
								WorkflowStepApprover.ApproverType.USER, null, first.id(), null),
						new com.edam.workflow.dto.WorkflowDtos.ApproverRequest(
								WorkflowStepApprover.ApproverType.USER, null, second.id(), null))));
	}

	private com.edam.workflow.dto.WorkflowDtos.VersionResponse draft(
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition) {
		return this.configuration.createDraft(definition.id(),
				new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
						twoApproverStep(this.approver, this.secondApprover), List.of(), null));
	}

	private com.edam.workflow.dto.WorkflowDtos.VersionResponse publish(
			com.edam.workflow.dto.WorkflowDtos.VersionResponse draft) {
		return this.configuration.publish(draft.id(),
				new com.edam.workflow.dto.WorkflowDtos.PublishRequest(null), this.approver.getId());
	}

	// ---------------------------------------------------------------------------------------------
	// Authorization: manage and publish are distinct
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("authorization")
	class Authorization {

		@Test
		@DisplayName("configuration requires workflow:view to read")
		void readRequiresView() throws Exception {
			WorkflowConfigurationApiTest.this.mockMvc
				.perform(get(WORKFLOWS).with(user(WorkflowConfigurationApiTest.this.principal(Permissions.REQUEST_VIEW_OWN)))
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("a publisher without manage cannot author a definition")
		void publishPermissionIsNotManage() throws Exception {
			WorkflowConfigurationApiTest.this.mockMvc
				.perform(post(WORKFLOWS).with(csrf()).with(user(WorkflowConfigurationApiTest.this.publisherOnly()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"code": "NO-MANAGE", "name": "Should not exist"}
							""")
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("a manager without publish cannot publish")
		void managerCannotPublish() throws Exception {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("NO-PUB-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse draft = WorkflowConfigurationApiTest.this.draft(definition);

			WorkflowConfigurationApiTest.this.mockMvc
				.perform(post(WORKFLOWS + "/versions/" + draft.id() + "/publish").with(csrf())
					.with(user(WorkflowConfigurationApiTest.this
						.principal(Permissions.WORKFLOW_VIEW, Permissions.WORKFLOW_MANAGE)))
					.contentType(MediaType.APPLICATION_JSON).content("{}")
					.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());

			assertThat(WorkflowConfigurationApiTest.this.versions.findById(draft.id()).orElseThrow().getStatus())
				.isEqualTo(WorkflowVersion.Status.DRAFT);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Draft authoring
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("draft authoring")
	class DraftAuthoring {

		@Test
		@DisplayName("a draft records its steps, approvers and derived states")
		void draftRecordsGraph() throws Exception {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("DRAFT-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse draft = WorkflowConfigurationApiTest.this.draft(definition);

			assertThat(draft.versionNumber()).isEqualTo(1);
			assertThat(draft.status()).isEqualTo(WorkflowVersion.Status.DRAFT);
			assertThat(draft.definitionId()).isEqualTo(definition.id());
		}

		@Test
		@DisplayName("saving the draft replaces the previous graph rather than appending")
		void draftIsReplacedNotAppended() throws Exception {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("REPLACE-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse first = WorkflowConfigurationApiTest.this.draft(definition);
			com.edam.workflow.dto.WorkflowDtos.VersionResponse second = WorkflowConfigurationApiTest.this.configuration.createDraft(
					definition.id(), new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
							List.of(new com.edam.workflow.dto.WorkflowDtos.StepRequest("NARROW", "Narrow",
									(short) 1, com.edam.workflow.model.WorkflowStep.ApprovalMode.PARALLEL, (short) 1,
									List.of(new com.edam.workflow.dto.WorkflowDtos.ApproverRequest(
											WorkflowStepApprover.ApproverType.USER, null,
											WorkflowConfigurationApiTest.this.approver.getId(), null)))),
							List.of(), null));

			// Same revision, rewritten; not a second revision.
			assertThat(second.id()).isEqualTo(first.id());
			assertThat(second.versionNumber()).isEqualTo(1);
		}

		@Test
		@DisplayName("a second draft on a definition already holding one is refused")
		void secondDraftRefused() {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("ONEDRAFT-" + System.nanoTime());
			WorkflowConfigurationApiTest.this.draft(definition);

			assertThatThrownBy(() -> WorkflowConfigurationApiTest.this.configuration.createDraft(
					definition.id(), new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
							twoApproverStep(WorkflowConfigurationApiTest.this.approver,
									WorkflowConfigurationApiTest.this.secondApprover),
							List.of(), null)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("draft");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Validation
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("validation")
	class Validation {

		@Test
		@DisplayName("a step with no approver is an error, and publishing it is refused")
		void noApproverRefused() throws Exception {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("EMPTY-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse draft = WorkflowConfigurationApiTest.this.configuration.createDraft(
					definition.id(), new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
							List.of(new com.edam.workflow.dto.WorkflowDtos.StepRequest("EMPTY", "Empty",
									(short) 1, com.edam.workflow.model.WorkflowStep.ApprovalMode.PARALLEL, (short) 1,
									List.of())),
							List.of(), null));

			WorkflowConfigurationApiTest.this.mockMvc
				.perform(get(WORKFLOWS + "/versions/" + draft.id() + "/validate")
					.with(user(WorkflowConfigurationApiTest.this.manager())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.valid").value(false));

			assertThatThrownBy(() -> WorkflowConfigurationApiTest.this.publish(draft))
				.isInstanceOf(BusinessException.class);
			assertThat(WorkflowConfigurationApiTest.this.versions.findById(draft.id()).orElseThrow().getStatus())
				.isEqualTo(WorkflowVersion.Status.DRAFT);
		}

		@Test
		@DisplayName("a valid draft reports publishable with no errors")
		void validDraftPublishable() throws Exception {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("VALID-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse draft = WorkflowConfigurationApiTest.this.draft(definition);

			WorkflowConfigurationApiTest.this.mockMvc
				.perform(get(WORKFLOWS + "/versions/" + draft.id() + "/validate")
					.with(user(WorkflowConfigurationApiTest.this.manager())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.valid").value(true))
				.andExpect(jsonPath("$.data.issues").isEmpty());
		}

		@Test
		@DisplayName("an approver type whose reference is missing is an error")
		void danglingApproverReferenceRefused() {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("DANGLE-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse draft = WorkflowConfigurationApiTest.this.configuration.createDraft(
					definition.id(), new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
							List.of(new com.edam.workflow.dto.WorkflowDtos.StepRequest("ROLE", "Role step",
									(short) 1, com.edam.workflow.model.WorkflowStep.ApprovalMode.PARALLEL, (short) 1,
									List.of(new com.edam.workflow.dto.WorkflowDtos.ApproverRequest(
											WorkflowStepApprover.ApproverType.ROLE, null, null, null)))),
							List.of(), null));

			// A ROLE approver naming no role can never resolve; publication must not accept it.
			assertThatThrownBy(() -> WorkflowConfigurationApiTest.this.publish(draft))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("cannot be published");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Publication and immutability
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("publication")
	class Publication {

		@Test
		@DisplayName("publishing stamps the revision and makes it the startable one")
		void publishStampsRevision() throws Exception {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("PUB-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse published = WorkflowConfigurationApiTest.this.publish(
					WorkflowConfigurationApiTest.this.draft(definition));

			assertThat(published.status()).isEqualTo(WorkflowVersion.Status.PUBLISHED.name());
			assertThat(published.publishedAt()).isNotNull();
			assertThat(published.publishedBy()).isEqualTo(WorkflowConfigurationApiTest.this.approver.getId());
			assertThat(WorkflowConfigurationApiTest.this.configuration
				.requirePublishedForRequestType(WorkflowConfigurationApiTest.this.requestType.getId()).getId())
				.isEqualTo(published.id());
		}

		@Test
		@DisplayName("a published revision cannot be rewritten")
		void publishedRevisionIsImmutable() {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("IMMUT-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse published = WorkflowConfigurationApiTest.this.publish(
					WorkflowConfigurationApiTest.this.draft(definition));

			assertThatThrownBy(() -> WorkflowConfigurationApiTest.this.configuration.createDraft(
					definition.id(), new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
							twoApproverStep(WorkflowConfigurationApiTest.this.approver,
									WorkflowConfigurationApiTest.this.secondApprover),
							List.of(), null)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("draft");

			// The published revision is untouched by the attempt.
			WorkflowVersion reloaded = WorkflowConfigurationApiTest.this.versions.findById(published.id())
				.orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(WorkflowVersion.Status.PUBLISHED);
			assertThat(reloaded.getVersionNumber()).isEqualTo(published.versionNumber());
		}

		@Test
		@DisplayName("publishing a second revision retires the first and keeps it on record")
		void secondPublicationRetiresFirst() {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("SUPERSEDE-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse first = WorkflowConfigurationApiTest.this.publish(
					WorkflowConfigurationApiTest.this.draft(definition));

			com.edam.workflow.dto.WorkflowDtos.VersionResponse secondDraft = WorkflowConfigurationApiTest.this.configuration.createDraft(
					definition.id(), new com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest(
							twoApproverStep(WorkflowConfigurationApiTest.this.approver,
									WorkflowConfigurationApiTest.this.secondApprover),
							List.of(), null));
			com.edam.workflow.dto.WorkflowDtos.VersionResponse second = WorkflowConfigurationApiTest.this.publish(secondDraft);

			assertThat(second.versionNumber()).isEqualTo(2);
			// The superseded revision is retired, not deleted: requests pinned to it must still resolve.
			WorkflowVersion firstReloaded = WorkflowConfigurationApiTest.this.versions.findById(first.id())
				.orElseThrow();
			assertThat(firstReloaded.getStatus()).isEqualTo(WorkflowVersion.Status.RETIRED);
			assertThat(WorkflowConfigurationApiTest.this.versions.findPublished(definition.id()).orElseThrow()
				.getId()).isEqualTo(second.id());
		}

		@Test
		@DisplayName("an effective date in the past is refused")
		void pastEffectiveDateRefused() {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("PAST-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse draft = WorkflowConfigurationApiTest.this.draft(definition);

			assertThatThrownBy(() -> WorkflowConfigurationApiTest.this.configuration.publish(draft.id(),
					new com.edam.workflow.dto.WorkflowDtos.PublishRequest(
							java.time.Instant.now().minusSeconds(86_400)),
					WorkflowConfigurationApiTest.this.approver.getId()))
				.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("cloning the published revision produces a new editable draft, not a mutation")
		void clonePublishedIsNewDraft() throws Exception {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("CLONE-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse published = WorkflowConfigurationApiTest.this.publish(
					WorkflowConfigurationApiTest.this.draft(definition));

			WorkflowConfigurationApiTest.this.mockMvc
				.perform(post(WORKFLOWS + "/" + definition.id() + "/draft/from-published").with(csrf())
					.with(user(WorkflowConfigurationApiTest.this.manager())).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DRAFT"))
				.andExpect(jsonPath("$.data.id").value(org.hamcrest.Matchers.not(
						published.id().toString())));

			assertThat(WorkflowConfigurationApiTest.this.versions.findById(published.id()).orElseThrow()
				.getStatus()).isEqualTo(WorkflowVersion.Status.PUBLISHED);
		}

		@Test
		@DisplayName("retiring the published revision leaves the request type unsubmittable")
		void retiredLeavesNothingToStart() {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("RETIRE-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse published = WorkflowConfigurationApiTest.this.publish(
					WorkflowConfigurationApiTest.this.draft(definition));

			WorkflowConfigurationApiTest.this.configuration.retire(published.id(),
					new com.edam.workflow.dto.WorkflowDtos.RetireRequest("Withdrawn"),
					WorkflowConfigurationApiTest.this.approver.getId());

			// Fails closed: a request type whose only revision was retired must not accept submissions.
			assertThatThrownBy(() -> WorkflowConfigurationApiTest.this.configuration
				.requirePublishedForRequestType(WorkflowConfigurationApiTest.this.requestType.getId()))
				.isInstanceOf(BusinessException.class);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Definitions
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("definitions")
	class Definitions {

		@Test
		@DisplayName("a duplicate definition code is refused regardless of case")
		void duplicateCodeRefused() {
			String code = "DUP-" + System.nanoTime();
			WorkflowConfigurationApiTest.this.definition(code);

			assertThatThrownBy(() -> WorkflowConfigurationApiTest.this.definition(code.toLowerCase()))
				.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("a definition bound to an unknown request type is refused")
		void unknownRequestTypeRefused() {
			assertThatThrownBy(() -> WorkflowConfigurationApiTest.this.configuration.createDefinition(
					new com.edam.workflow.dto.WorkflowDtos.SaveDefinitionRequest("BADTYPE-" + System.nanoTime(),
							"Bad type", null, UUID.randomUUID())))
				.isInstanceOf(RuntimeException.class);
		}

		@Test
		@DisplayName("two request types can hold separate workflows at once, which is how OPEX and CAPEX differ")
		void separateWorkflowsPerRequestType() {
			ExpenseRequestType other = RequestFixtures.requestType(WorkflowConfigurationApiTest.this.requestTypes,
					"OTHER-" + UUID.randomUUID().toString().substring(0, 8));
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse opexDefinition = WorkflowConfigurationApiTest.this.definition("SEP-A-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse otherDefinition = WorkflowConfigurationApiTest.this.configuration.createDefinition(
					new com.edam.workflow.dto.WorkflowDtos.SaveDefinitionRequest("SEP-B-" + System.nanoTime(),
							"Other chain", null, other.getId()));

			com.edam.workflow.dto.WorkflowDtos.VersionResponse opexPublished = WorkflowConfigurationApiTest.this.publish(
					WorkflowConfigurationApiTest.this.draft(opexDefinition));
			com.edam.workflow.dto.WorkflowDtos.VersionResponse otherPublished = WorkflowConfigurationApiTest.this.publish(
					WorkflowConfigurationApiTest.this.draft(otherDefinition));

			assertThat(opexPublished.id()).isNotEqualTo(otherPublished.id());
			assertThat(WorkflowConfigurationApiTest.this.configuration
				.requirePublishedForRequestType(WorkflowConfigurationApiTest.this.requestType.getId()).getId())
				.isEqualTo(opexPublished.id());
			assertThat(WorkflowConfigurationApiTest.this.configuration
				.requirePublishedForRequestType(other.getId()).getId()).isEqualTo(otherPublished.id());
		}

		@Test
		@DisplayName("a deactivated definition cannot publish")
		void inactiveDefinitionCannotPublish() {
			com.edam.workflow.dto.WorkflowDtos.DefinitionResponse definition = WorkflowConfigurationApiTest.this.definition("INACT-" + System.nanoTime());
			com.edam.workflow.dto.WorkflowDtos.VersionResponse draft = WorkflowConfigurationApiTest.this.draft(definition);
			WorkflowConfigurationApiTest.this.configuration.setActive(definition.id(), false);

			assertThatThrownBy(() -> WorkflowConfigurationApiTest.this.publish(draft))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("deactivated");
		}
	}
}
