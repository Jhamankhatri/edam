package com.edam.workflow.api;

import java.time.Instant;
import java.util.List;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.workflow.WorkflowDefinition;
import com.edam.workflow.WorkflowService;
import com.edam.workflow.WorkflowVersion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workflow configuration API (BRD FM-01..FM-06, §8.3, §8.6).
 *
 * <h2>Draft → validate → publish → rollback</h2>
 * The endpoints mirror the lifecycle the BRD requires. Two behaviours are worth calling out because
 * they are security-relevant rather than merely convenient:
 * <ul>
 *   <li><strong>Validate is separate from save.</strong> An administrator can check a document without
 *       creating a version, so a broken model is caught before it exists in any form.</li>
 *   <li><strong>Publish is a distinct permission</strong> ({@code workflow:publish}) from edit
 *       ({@code workflow:manage}). Separating them is what makes four-eyes review possible: the person
 *       who authors a workflow need not be the person who can put it into production.</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/api/v1/workflows", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Workflows", description = "Versioned approval workflows (FM-01..FM-06)")
public class WorkflowController {

	private final WorkflowService workflows;

	private final tools.jackson.databind.json.JsonMapper jsonMapper;

	public WorkflowController(WorkflowService workflows, tools.jackson.databind.json.JsonMapper jsonMapper) {
		this.workflows = workflows;
		this.jsonMapper = jsonMapper;
	}

	@GetMapping
	@EdamPermission(Permissions.WORKFLOW_VIEW)
	@Operation(summary = "List workflow definitions")
	public ApiResponse<List<DefinitionResponse>> list(HttpServletRequest request) {
		return ApiResponse.success(this.workflows.listDefinitions()
			.stream()
			.map(WorkflowController::toResponse)
			.toList(), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.WORKFLOW_MANAGE)
	@Operation(summary = "Create a workflow definition")
	public ApiResponse<DefinitionResponse> create(@Valid @RequestBody CreateDefinitionRequest body,
			HttpServletRequest request) {
		WorkflowDefinition definition = this.workflows.createDefinition(body.code(), body.name(),
				body.description());
		return ApiResponse.success(toResponse(definition), CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/{definitionId}/versions")
	@EdamPermission(Permissions.WORKFLOW_VIEW)
	@Operation(summary = "List the versions of a workflow")
	public ApiResponse<List<VersionResponse>> versions(@PathVariable Long definitionId, HttpServletRequest request) {
		return ApiResponse.success(this.workflows.listVersions(definitionId)
			.stream()
			.map(WorkflowController::toResponse)
			.toList(), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/{definitionId}/versions", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.WORKFLOW_MANAGE)
	@Operation(summary = "Create a draft version (validated on save)")
	public ApiResponse<VersionResponse> createDraft(@PathVariable Long definitionId,
			@Valid @RequestBody DraftRequest body, HttpServletRequest request) {
		WorkflowVersion draft = this.workflows.createDraft(definitionId, body.definitionJson(), body.changeNote());
		return ApiResponse.success(toResponse(draft), CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping(path = "/versions/{versionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.WORKFLOW_MANAGE)
	@Operation(summary = "Update a draft version")
	public ApiResponse<VersionResponse> updateDraft(@PathVariable Long versionId,
			@Valid @RequestBody DraftRequest body, HttpServletRequest request) {
		WorkflowVersion draft = this.workflows.updateDraft(versionId, body.definitionJson(), body.changeNote());
		return ApiResponse.success(toResponse(draft), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.WORKFLOW_VALIDATE)
	@Operation(summary = "Validate a workflow document without storing it")
	public ApiResponse<ValidationResponse> validate(@Valid @RequestBody ValidateRequest body,
			HttpServletRequest request) {
		var steps = com.edam.workflow.WorkflowSteps.parse(this.jsonMapper, body.definitionJson());
		List<StepResponse> described = steps.steps()
			.stream()
			.map(step -> new StepResponse(step.code(), step.label(), step.approver().name(), step.sequence(),
					step.quorum()))
			.toList();
		return ApiResponse.success(new ValidationResponse(true, described, "The workflow document is valid."),
				CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/versions/{versionId}/publish")
	@EdamPermission(Permissions.WORKFLOW_PUBLISH)
	@Operation(summary = "Publish a draft, retiring the previously published version")
	public ApiResponse<VersionResponse> publish(@PathVariable Long versionId, HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.workflows.publish(versionId)), CorrelationId.current(),
				request.getRequestURI());
	}

	@PostMapping("/{definitionId}/rollback")
	@EdamPermission(Permissions.WORKFLOW_PUBLISH)
	@Operation(summary = "Roll back by publishing an earlier version as a new version")
	public ApiResponse<VersionResponse> rollback(@PathVariable Long definitionId, @RequestParam int versionNumber,
			HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.workflows.rollback(definitionId, versionNumber)),
				CorrelationId.current(), request.getRequestURI());
	}

	static DefinitionResponse toResponse(WorkflowDefinition definition) {
		return new DefinitionResponse(definition.getId(), definition.getCode(), definition.getName(),
				definition.getDescription());
	}

	static VersionResponse toResponse(WorkflowVersion version) {
		return new VersionResponse(version.getId(), version.getDefinition().getId(),
				version.getVersionNumber(), version.getStatus().name(), version.getChecksum(),
				version.getChangeNote(), version.getDefinitionJson(), version.getPublishedBy(),
				version.getPublishedAt());
	}

	public record DefinitionResponse(Long id, String code, String name, String description) {
	}

	public record VersionResponse(Long id, Long definitionId, int versionNumber, String status, String checksum,
			String changeNote, String definitionJson, String publishedBy, Instant publishedAt) {
	}

	public record CreateDefinitionRequest(
			@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 200) String name,
			@Size(max = 1000) String description) {
	}

	public record DraftRequest(@NotBlank @Size(max = 20000) String definitionJson,
			@Size(max = 1000) String changeNote) {
	}

	/**
	 * Validation-only request: the document is parsed and checked without being stored.
	 *
	 * <p>Exists so an administrator can iterate on a model in the editor, and so a CI pipeline can
	 * validate a workflow file before promoting it.
	 */
	public record ValidateRequest(@NotNull @NotBlank @Size(max = 20000) String definitionJson) {
	}

	public record StepResponse(String code, String label, String approver, int sequence, int quorum) {
	}

	public record ValidationResponse(boolean valid, List<StepResponse> steps, String message) {
	}

}
