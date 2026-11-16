package com.edam.workflow.controller;

import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.workflow.dto.WorkflowDtos.DefinitionResponse;
import com.edam.workflow.dto.WorkflowDtos.PublishRequest;
import com.edam.workflow.dto.WorkflowDtos.RetireRequest;
import com.edam.workflow.dto.WorkflowDtos.SaveDefinitionRequest;
import com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest;
import com.edam.workflow.dto.WorkflowDtos.ValidationResponse;
import com.edam.workflow.dto.WorkflowDtos.VersionCompareResponse;
import com.edam.workflow.dto.WorkflowDtos.VersionResponse;
import com.edam.workflow.service.WorkflowConfigurationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workflow configuration: definitions, draft revisions, validation and publication (Module I).
 *
 * <h2>Three permissions, mapping to three different risks</h2>
 * <ul>
 *   <li>{@code workflow:view} — read the configuration. Harmless, and needed by anyone who has to explain
 *       why a request went where it did.</li>
 *   <li>{@code workflow:manage} — author drafts. A draft cannot route anything, so an author who is not a
 *       publisher can prepare a chain for review. This separation is what makes four-eyes work on the
 *       configuration itself.</li>
 *   <li>{@code workflow:publish} — make a draft live. Held by fewer people, because this is the operation
 *       that changes how money is approved.</li>
 * </ul>
 *
 * <h2>No endpoint edits a published revision</h2>
 * There is no {@code PUT} on a version and no status setter. Publication is the only state change, and it is
 * one-way; a change to a live chain is a new revision. That is not an API omission, it is the guarantee.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/workflows", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Workflow configuration",
		description = "Author, validate and publish workflow definitions and immutable versions")
public class WorkflowConfigurationController {

	private final WorkflowConfigurationService service;

	public WorkflowConfigurationController(WorkflowConfigurationService service) {
		this.service = service;
	}

	// ---------------------------------------------------------------------------------------------
	// Definitions
	// ---------------------------------------------------------------------------------------------

	@GetMapping
	@EdamPermission(Permissions.WORKFLOW_VIEW)
	@Operation(summary = "List workflow definitions",
			description = "Each definition reports its published revision, if any, and its version history.")
	public ApiResponse<List<DefinitionResponse>> list(
			@RequestParam(defaultValue = "false") boolean includeInactive, HttpServletRequest request) {
		return ok(this.service.listDefinitions(includeInactive), request);
	}

	@PostMapping
	@EdamPermission(Permissions.WORKFLOW_MANAGE)
	@Operation(summary = "Create a workflow definition",
			description = "Binding a definition to a request type is what gives OPEX and CAPEX their own chains.")
	public ResponseEntity<ApiResponse<DefinitionResponse>> create(@Valid @RequestBody SaveDefinitionRequest body,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ok(this.service.createDefinition(body), request));
	}

	@PutMapping("/{definitionId}")
	@EdamPermission(Permissions.WORKFLOW_MANAGE)
	@Operation(summary = "Rename or rebind a definition",
			description = "Rebinding the request type is refused while a revision is published: it would move "
					+ "future requests of both types onto this chain without an explicit decision.")
	public ApiResponse<DefinitionResponse> update(@PathVariable UUID definitionId,
			@Valid @RequestBody SaveDefinitionRequest body, HttpServletRequest request) {
		return ok(this.service.updateDefinition(definitionId, body), request);
	}

	@PostMapping("/{definitionId}/active")
	@EdamPermission(Permissions.WORKFLOW_MANAGE)
	@Operation(summary = "Activate or deactivate a definition",
			description = "A deactivated definition routes nothing, and publishing a revision on it is refused.")
	public ApiResponse<DefinitionResponse> setActive(@PathVariable UUID definitionId,
			@RequestParam boolean active, HttpServletRequest request) {
		return ok(this.service.setActive(definitionId, active), request);
	}

	// ---------------------------------------------------------------------------------------------
	// Revisions
	// ---------------------------------------------------------------------------------------------

	@GetMapping("/{definitionId}/versions")
	@EdamPermission(Permissions.WORKFLOW_VIEW)
	@Operation(summary = "Version history of a definition, newest first")
	public ApiResponse<List<VersionResponse>> versions(@PathVariable UUID definitionId, HttpServletRequest request) {
		return ok(this.service.listVersions(definitionId), request);
	}

	@GetMapping("/versions/{versionId}")
	@EdamPermission(Permissions.WORKFLOW_VIEW)
	@Operation(summary = "One revision with its steps, designations, states and transitions")
	public ApiResponse<VersionResponse> version(@PathVariable UUID versionId, HttpServletRequest request) {
		return ok(this.service.getVersion(versionId), request);
	}

	@PutMapping("/{definitionId}/draft")
	@EdamPermission(Permissions.WORKFLOW_MANAGE)
	@Operation(summary = "Create or replace the definition's single draft revision",
			description = "The whole graph is replaced: what the caller last sent is exactly what would be "
					+ "published. A definition holds at most one draft, so the document under review cannot be "
					+ "confused with another.")
	public ApiResponse<VersionResponse> saveDraft(@PathVariable UUID definitionId,
			@Valid @RequestBody SaveDraftRequest body, HttpServletRequest request) {
		return ok(this.service.createDraft(definitionId, body), request);
	}

	@PostMapping("/{definitionId}/draft/from-published")
	@EdamPermission(Permissions.WORKFLOW_MANAGE)
	@Operation(summary = "Start a new draft from the currently published revision",
			description = "The usual path for a change: copy the live chain, edit it, publish it. The live "
					+ "revision is untouched throughout.")
	public ApiResponse<VersionResponse> clonePublished(@PathVariable UUID definitionId,
			HttpServletRequest request) {
		return ok(this.service.clonePublishedIntoDraft(definitionId), request);
	}

	@PostMapping("/{definitionId}/draft/from-version/{sourceVersionId}")
	@EdamPermission(Permissions.WORKFLOW_MANAGE)
	@Operation(summary = "Roll back: start a new draft from any prior revision",
			description = "Creates (or replaces) the definition's editable draft with a copy of the source "
					+ "revision — published or retired — so that publishing it restores the prior behaviour. "
					+ "The source revision is never mutated; in-flight requests keep their pinned version.")
	public ApiResponse<VersionResponse> rollbackIntoDraft(@PathVariable UUID definitionId,
			@PathVariable UUID sourceVersionId, HttpServletRequest request) {
		return ok(this.service.rollbackIntoDraft(definitionId, sourceVersionId), request);
	}

	@GetMapping("/versions/compare")
	@EdamPermission(Permissions.WORKFLOW_VIEW)
	@Operation(summary = "Structural diff between two revisions",
			description = "Reports added, removed and modified steps, states and transitions by code so an "
					+ "administrator can review exactly what a new version would change before publishing it.")
	public ApiResponse<VersionCompareResponse> compare(@RequestParam UUID from, @RequestParam UUID to,
			HttpServletRequest request) {
		return ok(this.service.compare(from, to), request);
	}

	// ---------------------------------------------------------------------------------------------
	// Validation and publication
	// ---------------------------------------------------------------------------------------------

	@GetMapping("/versions/{versionId}/validate")
	@EdamPermission(Permissions.WORKFLOW_VIEW)
	@Operation(summary = "Validate a revision without publishing it",
			description = "Read-only, so a designer can call it on every save. Errors block publication; "
					+ "warnings describe operational risks that do not make the workflow unroutable.")
	public ApiResponse<ValidationResponse> validate(@PathVariable UUID versionId, HttpServletRequest request) {
		return ok(this.service.validate(versionId), request);
	}

	@PostMapping("/versions/{versionId}/publish")
	@EdamPermission(Permissions.WORKFLOW_PUBLISH)
	@Operation(summary = "Publish a draft revision, retiring the one it replaces",
			description = "Refused when validation reports any error. In-flight requests keep the version they "
					+ "started under; only new submissions use this one.")
	public ApiResponse<VersionResponse> publish(@PathVariable UUID versionId,
			@RequestBody(required = false) @Valid PublishRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.publish(versionId, body, caller.userId()), request);
	}

	@PostMapping("/versions/{versionId}/retire")
	@EdamPermission(Permissions.WORKFLOW_PUBLISH)
	@Operation(summary = "Retire a published revision without a replacement",
			description = "Stops new submissions of that request type. In-flight requests are unaffected because "
					+ "each pins the revision it started under.")
	public ApiResponse<VersionResponse> retire(@PathVariable UUID versionId,
			@RequestBody(required = false) @Valid RetireRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.retire(versionId, caller.userId(), body == null ? null : body.reason()), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

}
