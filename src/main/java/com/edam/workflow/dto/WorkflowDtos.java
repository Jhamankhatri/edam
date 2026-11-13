package com.edam.workflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.workflow.model.WorkflowDefinition;
import com.edam.workflow.model.WorkflowState;
import com.edam.workflow.model.WorkflowStep;
import com.edam.workflow.model.WorkflowStepApprover;
import com.edam.workflow.model.WorkflowTransition;
import com.edam.workflow.model.WorkflowVersion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Workflow configuration contracts (Module I).
 *
 * <h2>Why the draft payload replaces the whole graph</h2>
 * A draft revision is edited as a unit: steps, their approvers, states and transitions. Applying a diff
 * would need a stable identity for every nested row, and a mis-applied diff on an approval chain is a
 * routing change nobody noticed. Sending the whole graph and replacing it makes "what will be published"
 * exactly what the caller last sent.
 *
 * <h2>Why published versions have no update contract at all</h2>
 * There is no {@code UpdatePublishedVersionRequest} because that operation does not exist. A published
 * revision is immutable, enforced by the service, and the absence of a request type for it is the clearest
 * possible statement of that.
 */
public final class WorkflowDtos {

	private WorkflowDtos() {
	}

	// ---------------------------------------------------------------------------------------------
	// Definitions
	// ---------------------------------------------------------------------------------------------

	/** Creates or renames a workflow definition. */
	public record SaveDefinitionRequest(@NotBlank @Size(max = 64) String code,
			@NotBlank @Size(max = 200) String name, @Size(max = 4000) String description, UUID requestTypeId) {
	}

	/** A definition with its revisions, newest first. */
	public record DefinitionResponse(UUID id, String code, String name, String description, UUID requestTypeId,
			boolean active, UUID publishedVersionId, Integer publishedVersionNumber, int draftVersionCount,
			List<VersionSummary> versions) {

		public static DefinitionResponse from(WorkflowDefinition definition, List<WorkflowVersion> versions) {
			UUID publishedId = null;
			Integer publishedNumber = null;
			int drafts = 0;
			for (WorkflowVersion version : versions) {
				if (version.getStatus() == WorkflowVersion.Status.PUBLISHED) {
					publishedId = version.getId();
					publishedNumber = version.getVersionNumber();
				}
				else if (version.getStatus() == WorkflowVersion.Status.DRAFT) {
					drafts++;
				}
			}
			return new DefinitionResponse(definition.getId(), definition.getCode(), definition.getName(),
					definition.getDescription(), definition.getRequestTypeId(), definition.isActive(), publishedId,
					publishedNumber, drafts,
					versions.stream().map(VersionSummary::from).toList());
		}
	}

	/** A revision's headline facts, without its graph. */
	public record VersionSummary(UUID id, int versionNumber, String status, Instant publishedAt, UUID publishedBy,
			Instant effectiveFrom, Instant retiredAt, UUID supersededByVersionId) {

		public static VersionSummary from(WorkflowVersion version) {
			return new VersionSummary(version.getId(), version.getVersionNumber(),
					version.getStatus().name(), version.getPublishedAt(), version.getPublishedBy(),
					version.getEffectiveFrom(), version.getRetiredAt(), version.getSupersededByVersionId());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Draft authoring
	// ---------------------------------------------------------------------------------------------

	/** One approver designation. Exactly one reference column is meaningful for a given type. */
	public record ApproverRequest(@NotNull WorkflowStepApprover.ApproverType approverType, UUID roleId, UUID userId,
			UUID committeeLevelId) {
	}

	/** One step of the chain. */
	public record StepRequest(@NotBlank @Size(max = 64) String stepCode,
			@NotBlank @Size(max = 150) String stepName, @Min(1) @Max(1000) short stepOrder,
			@NotNull WorkflowStep.ApprovalMode approvalMode, @Min(1) @Max(100) short minimumApprovals,
			@NotEmpty @Valid List<ApproverRequest> approvers) {
	}

	/** One state. {@code stateType} decides the engine's behaviour, never the code. */
	public record StateRequest(@NotBlank @Size(max = 64) String stateCode,
			@NotBlank @Size(max = 150) String stateName, @NotNull WorkflowState.StateType stateType) {
	}

	/** One declared transition, addressed by state code because ids do not exist before the draft is saved. */
	public record TransitionRequest(@NotBlank @Size(max = 64) String fromStateCode, @Size(max = 64) String toStateCode,
			@NotNull WorkflowTransition.ActionCode actionCode, UUID conditionRuleId) {
	}

	/**
	 * Replaces a draft revision's whole graph.
	 *
	 * <p>{@code initialised} states exist in the schema but are not part of this payload: the four states
	 * the engine requires are derived by the validator, so a caller cannot accidentally omit one and produce
	 * a workflow that cannot start.
	 */
	public record SaveDraftRequest(@Min(1) @NotEmpty @Valid List<StepRequest> steps,
			@Valid List<TransitionRequest> transitions, @Valid List<StateRequest> states) {

		/** A draft with no explicit states: the five the engine requires are derived. */
		public SaveDraftRequest(List<StepRequest> steps, List<TransitionRequest> transitions) {
			this(steps, transitions, null);
		}
	}

	/** The full graph of one revision. */
	public record VersionResponse(UUID id, UUID definitionId, int versionNumber, String status, Instant publishedAt,
			UUID publishedBy, Instant effectiveFrom, Instant retiredAt, UUID retiredBy, UUID supersededByVersionId,
			List<StepResponse> steps, List<StateResponse> states, List<TransitionResponse> transitions) {

		public static VersionResponse from(WorkflowVersion version) {
			return new VersionResponse(version.getId(), version.getWorkflowDefinitionId(),
					version.getVersionNumber(), version.getStatus().name(), version.getPublishedAt(),
					version.getPublishedBy(), version.getEffectiveFrom(), version.getRetiredAt(),
					version.getRetiredBy(), version.getSupersededByVersionId(),
					version.getSteps().stream().map(StepResponse::from).toList(),
					version.getStates().stream()
						.map(state -> new StateResponse(state.getId(), state.getStateCode(), state.getStateName(),
								state.getStateType().name(), state.isInitial(), state.isTerminal()))
						.toList(),
					version.getTransitions().stream()
						.map(transition -> new TransitionResponse(transition.getId(), transition.getFromStateId(),
								transition.getToStateId(), transition.getActionCode().name(),
								transition.getConditionRuleId()))
						.toList());
		}
	}

	/** One step with its designations. */
	public record StepResponse(UUID id, String stepCode, String stepName, short stepOrder, String approvalMode,
			short minimumApprovals, boolean mandatory, List<ApproverResponse> approvers) {

		public static StepResponse from(WorkflowStep step) {
			return new StepResponse(step.getId(), step.getStepCode(), step.getStepName(), step.getStepOrder(),
					step.getApprovalMode().name(), step.getMinimumApprovals(), step.isMandatory(),
					step.getApprovers().stream().map(ApproverResponse::from).toList());
		}
	}

	/** One designation, with the reference that gives it meaning. */
	public record ApproverResponse(UUID id, String approverType, UUID roleId, UUID userId, UUID committeeLevelId) {

		public static ApproverResponse from(WorkflowStepApprover approver) {
			return new ApproverResponse(approver.getId(), approver.getApproverType().name(), approver.getRoleId(),
					approver.getUserId(), approver.getCommitteeLevelId());
		}
	}

	/**
	 * A state. {@code stateId} is a UUID rather than a code because transitions are stored by id; a client
	 * that wants to render the graph maps codes to ids through this list.
	 */
	public record StateResponse(UUID id, String stateCode, String stateName, String stateType, boolean initial,
			boolean terminal) {
	}

	/** A declared transition, by state id. */
	public record TransitionResponse(UUID id, UUID fromStateId, UUID toStateId, String actionCode,
			UUID conditionRuleId) {
	}

	// ---------------------------------------------------------------------------------------------
	// Validation and publication
	// ---------------------------------------------------------------------------------------------

	/**
	 * One finding from validation.
	 *
	 * <p>{@code severity} distinguishes an error from a warning. An error blocks publication; a warning is
	 * something an administrator should look at that does not make the workflow unroutable — for example a
	 * step whose approval resolves to nobody today.
	 */
	public record ValidationIssue(String severity, String field, String message) {

		public static ValidationIssue error(String field, String message) {
			return new ValidationIssue("ERROR", field, message);
		}

		public static ValidationIssue warning(String field, String message) {
			return new ValidationIssue("WARNING", field, message);
		}
	}

	/** The outcome of validating a draft. */
	public record ValidationResponse(UUID versionId, boolean valid, List<ValidationIssue> issues) {
	}

	/** Publishes a draft revision. */
	public record PublishRequest(Instant effectiveFrom) {
	}

	/** Retires a published revision. */
	public record RetireRequest(@Size(max = 500) String reason) {
	}
}
