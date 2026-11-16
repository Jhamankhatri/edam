package com.edam.workflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.expenseconfiguration.repository.ExpenseRequestTypeRepository;
import com.edam.workflow.dto.WorkflowDtos.ApproverRequest;
import com.edam.workflow.dto.WorkflowDtos.DefinitionResponse;
import com.edam.workflow.dto.WorkflowDtos.PublishRequest;
import com.edam.workflow.dto.WorkflowDtos.SaveDefinitionRequest;
import com.edam.workflow.dto.WorkflowDtos.SaveDraftRequest;
import com.edam.workflow.dto.WorkflowDtos.StateRequest;
import com.edam.workflow.dto.WorkflowDtos.StepRequest;
import com.edam.workflow.dto.WorkflowDtos.TransitionRequest;
import com.edam.workflow.dto.WorkflowDtos.ValidationIssue;
import com.edam.workflow.dto.WorkflowDtos.ValidationResponse;
import com.edam.workflow.dto.WorkflowDtos.VersionCompareResponse;
import com.edam.workflow.dto.WorkflowDtos.VersionDiffEntry;
import com.edam.workflow.dto.WorkflowDtos.VersionResponse;
import com.edam.workflow.model.WorkflowDefinition;
import com.edam.workflow.model.WorkflowState;
import com.edam.workflow.model.WorkflowStep;
import com.edam.workflow.model.WorkflowStepApprover;
import com.edam.workflow.model.WorkflowTransition;
import com.edam.workflow.model.WorkflowVersion;
import com.edam.workflow.repository.DefinitionsRepository;
import com.edam.workflow.repository.StatesRepository;
import com.edam.workflow.repository.StepsRepository;
import com.edam.workflow.repository.TransitionsRepository;
import com.edam.workflow.repository.VersionsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoring, validation and publication of workflow revisions (Module I, BRD 24.2).
 *
 * <h2>The one rule everything else serves</h2>
 * A published revision is immutable, and publishing a new one never changes how an in-flight request is
 * approved. Every method here is written so that this holds: drafts are the only mutable thing, publication
 * is a one-way transition, and the replacement of a published revision retires it rather than editing it.
 *
 * <h2>Validation before publication, and what "valid" means</h2>
 * BRD 24.1 lists the defects a publication must prevent: unreachable steps, missing approvers, conflicting
 * ranges, circular routes and impossible quorum requirements. {@link #validate} turns each of those into a
 * named finding. Publication refuses when any finding is an <em>error</em>; warnings are reported and do not
 * block, because a workflow whose approver is a role with no current holder is a real operational state that
 * an administrator should be told about without being unable to publish the chain they are building.
 *
 * <h2>Why the four engine states are derived, not accepted from the caller</h2>
 * The engine needs an initial state and an approved, rejected and cancelled state to function at all. If the
 * authoring payload could omit one, an administrator could publish a workflow that the engine can start but
 * never finish, and the failure would appear at the first rejection rather than at publication. The
 * validator therefore requires them and reports precisely which is missing.
 */
@Service
public class WorkflowConfigurationService {

	private static final Logger log = LoggerFactory.getLogger(WorkflowConfigurationService.class);

	private final DefinitionsRepository definitions;

	private final VersionsRepository versions;

	private final StatesRepository states;

	private final StepsRepository steps;

	private final TransitionsRepository transitions;

	private final ExpenseRequestTypeRepository requestTypes;

	private final AuditRecorder audit;

	public WorkflowConfigurationService(DefinitionsRepository definitions, VersionsRepository versions,
			StatesRepository states, StepsRepository steps, TransitionsRepository transitions,
			ExpenseRequestTypeRepository requestTypes, AuditRecorder audit) {
		this.definitions = definitions;
		this.versions = versions;
		this.states = states;
		this.steps = steps;
		this.transitions = transitions;
		this.requestTypes = requestTypes;
		this.audit = audit;
	}

	// ---------------------------------------------------------------------------------------------
	// Definitions
	// ---------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DefinitionResponse> listDefinitions(boolean includeInactive) {
		List<WorkflowDefinition> found = includeInactive ? this.definitions.findAll() : this.definitions
			.findByActiveTrueOrderByNameAsc();
		found = found.stream()
			.sorted(java.util.Comparator.comparing(WorkflowDefinition::getName,
					java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
			.toList();
		return found.stream()
			.map(definition -> DefinitionResponse.from(definition,
					this.versions.findByWorkflowDefinitionIdOrderByVersionNumberDesc(definition.getId())))
			.toList();
	}

	/**
	 * Binds a workflow to a request type.
	 *
	 * <p>This is how OPEX and CAPEX get separate chains without any code that mentions OPEX or CAPEX. The
	 * definition names a request type; a request carries one; submission looks the definition up by that id.
	 * A bank that invents a third request type gets a third workflow for free.
	 */
	@Transactional
	public DefinitionResponse createDefinition(SaveDefinitionRequest request) {
		String code = request.code().trim();
		if (this.definitions.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"A workflow definition with that code already exists.");
		}
		if (request.requestTypeId() != null && !this.requestTypes.existsById(request.requestTypeId())) {
			throw new ResourceNotFoundException("ExpenseRequestType", request.requestTypeId());
		}
		WorkflowDefinition definition = new WorkflowDefinition();
		definition.setCode(code);
		definition.setName(request.name().trim());
		definition.setDescription(request.description());
		definition.setRequestTypeId(request.requestTypeId());
		WorkflowDefinition saved = this.definitions.save(definition);
		this.audit.record(AuditAction.CREATE, "WorkflowDefinition", saved.getId());
		log.info("workflow.definition_created id={} code={} requestType={}", saved.getId(), saved.getCode(),
				saved.getRequestTypeId());
		return DefinitionResponse.from(saved, List.of());
	}

	@Transactional
	public DefinitionResponse updateDefinition(UUID id, SaveDefinitionRequest request) {
		WorkflowDefinition definition = requireDefinition(id);
		String code = request.code().trim();
		this.definitions.findByCodeIgnoreCase(code)
			.filter(existing -> !existing.getId().equals(id))
			.ifPresent(existing -> {
				throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
						"A workflow definition with that code already exists.");
			});
		// Re-binding a definition to a different request type while a version is published would silently
		// move every future request of both types onto this chain. The change is refused; the administrator
		// retires first, which is an explicit, audited act.
		if (!java.util.Objects.equals(definition.getRequestTypeId(), request.requestTypeId())) {
			this.versions.findPublished(id).ifPresent(published -> {
				throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
						"A published revision is bound to this request type. Retire it before rebinding the "
								+ "definition: rebinding would move future requests of both types onto this chain.");
			});
		}
		if (request.requestTypeId() != null && !this.requestTypes.existsById(request.requestTypeId())) {
			throw new ResourceNotFoundException("ExpenseRequestType", request.requestTypeId());
		}
		definition.setCode(code);
		definition.setName(request.name().trim());
		definition.setDescription(request.description());
		definition.setRequestTypeId(request.requestTypeId());
		this.audit.record(AuditAction.UPDATE, "WorkflowDefinition", id);
		return DefinitionResponse.from(definition,
				this.versions.findByWorkflowDefinitionIdOrderByVersionNumberDesc(id));
	}

	@Transactional
	public DefinitionResponse setActive(UUID id, boolean active) {
		WorkflowDefinition definition = requireDefinition(id);
		definition.setActive(active);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "WorkflowDefinition", id);
		return DefinitionResponse.from(definition,
				this.versions.findByWorkflowDefinitionIdOrderByVersionNumberDesc(id));
	}

	// ---------------------------------------------------------------------------------------------
	// Draft versions
	// ---------------------------------------------------------------------------------------------

	/**
	 * Creates a new draft revision, or clears the definition's existing draft.
	 *
	 * <p>A definition has at most one draft at a time. Two drafts would make "which one becomes v4"
	 * depend on which an administrator happened to publish, and the loser's configuration would vanish
	 * without anyone deciding to discard it.
	 */
	@Transactional
	public VersionResponse createDraft(UUID definitionId, SaveDraftRequest request) {
		WorkflowDefinition definition = requireDefinition(definitionId);
		WorkflowVersion draft = this.versions.findByWorkflowDefinitionIdOrderByVersionNumberDesc(definitionId).stream()
			.filter(WorkflowVersion::isEditable)
			.findFirst()
			.orElseGet(() -> {
				WorkflowVersion created = new WorkflowVersion();
				created.setWorkflowDefinitionId(definitionId);
				created.setVersionNumber(this.versions.maxVersionNumber(definitionId) + 1);
				return this.versions.save(created);
			});
		applyGraph(draft, request);
		WorkflowVersion saved = this.versions.save(draft);
		this.audit.record(AuditAction.UPDATE, "WorkflowVersion", saved.getId());
		log.info("workflow.draft_saved definitionId={} version={} steps={}", definitionId, saved.getVersionNumber(),
				saved.getSteps().size());
		return VersionResponse.from(saved);
	}

	/**
	 * Replaces a definition's single draft from an existing published revision.
	 *
	 * <p>This is the "new version based on the current one" path. Without it, every revision would be typed
	 * from scratch, and a typo in an approver designation would not be caught until publication.
	 */
	@Transactional
	public VersionResponse clonePublishedIntoDraft(UUID definitionId) {
		WorkflowVersion published = this.versions.findPublished(definitionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"This definition has no published revision to clone."));
		return cloneIntoDraft(definitionId, published);
	}

	/**
	 * Creates (or replaces) the editable draft from any existing revision of the same definition.
	 *
	 * <p>This is the rollback-as-new-version path: an administrator can rehydrate a retired revision's
	 * graph into a new draft, review it, and publish it as the next version. The retired revision itself
	 * is never mutated — it still governs any in-flight requests that pinned it — so this is an audit-safe
	 * way to revert behaviour.
	 */
	@Transactional
	public VersionResponse rollbackIntoDraft(UUID definitionId, UUID sourceVersionId) {
		WorkflowVersion source = requireVersion(sourceVersionId);
		if (!source.getWorkflowDefinitionId().equals(definitionId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The source revision does not belong to this workflow definition.");
		}
		if (source.getStatus() == WorkflowVersion.Status.DRAFT) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"Rollback source must be a published or retired revision, not a draft.");
		}
		VersionResponse draft = cloneIntoDraft(definitionId, source);
		this.audit.record(AuditAction.ROLLBACK, "WorkflowVersion", draft.id());
		log.info("workflow.rollback_into_draft definitionId={} sourceVersion={} newDraft={}",
				definitionId, source.getVersionNumber(), draft.versionNumber());
		return draft;
	}

	private VersionResponse cloneIntoDraft(UUID definitionId, WorkflowVersion source) {
		WorkflowVersion draft = this.versions.findByWorkflowDefinitionIdOrderByVersionNumberDesc(definitionId).stream()
			.filter(WorkflowVersion::isEditable)
			.findFirst()
			.orElseGet(() -> {
				WorkflowVersion created = new WorkflowVersion();
				created.setWorkflowDefinitionId(definitionId);
				created.setVersionNumber(this.versions.maxVersionNumber(definitionId) + 1);
				return this.versions.save(created);
			});
		// Clear whatever the draft held, then re-add the source graph.
		draft.getSteps().clear();
		draft.getStates().clear();
		draft.getTransitions().clear();
		this.versions.saveAndFlush(draft);

		Map<UUID, String> stateCodesById = source.getStates().stream()
			.collect(Collectors.toMap(WorkflowState::getId, WorkflowState::getStateCode));
		List<StateRequest> statesToAdd = source.getStates().stream()
			.map(state -> new StateRequest(state.getStateCode(), state.getStateName(), state.getStateType()))
			.toList();
		List<StepRequest> stepsToAdd = source.getSteps().stream()
			.map(step -> new StepRequest(step.getStepCode(), step.getStepName(), step.getStepOrder(),
					step.getApprovalMode(), step.getMinimumApprovals(),
					step.getApprovers().stream()
						.map(approver -> new ApproverRequest(approver.getApproverType(), approver.getRoleId(),
								approver.getUserId(), approver.getCommitteeLevelId()))
						.toList()))
			.toList();
		List<TransitionRequest> transitionsToAdd = source.getTransitions().stream()
			.map(transition -> new TransitionRequest(stateCodesById.get(transition.getFromStateId()),
					transition.getToStateId() == null ? null : stateCodesById.get(transition.getToStateId()),
					transition.getActionCode(), transition.getConditionRuleId()))
			.toList();
		applyGraph(draft, new SaveDraftRequest(stepsToAdd, transitionsToAdd, statesToAdd));
		WorkflowVersion saved = this.versions.save(draft);
		this.audit.record(AuditAction.UPDATE, "WorkflowVersion", saved.getId());
		return VersionResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public VersionResponse getVersion(UUID versionId) {
		return VersionResponse.from(requireVersion(versionId));
	}

	@Transactional(readOnly = true)
	public List<VersionResponse> listVersions(UUID definitionId) {
		requireDefinition(definitionId);
		return this.versions.findByWorkflowDefinitionIdOrderByVersionNumberDesc(definitionId).stream()
			.map(VersionResponse::from)
			.toList();
	}

	/**
	 * Structural diff between two revisions of the <em>same</em> definition.
	 *
	 * <p>The comparison is by step code, state code and action code so that re-orderings and approver-list
	 * edits surface as MODIFIED rather than as a delete plus add. No state is mutated; this is read-only.
	 */
	@Transactional(readOnly = true)
	public VersionCompareResponse compare(UUID fromVersionId, UUID toVersionId) {
		WorkflowVersion from = requireVersion(fromVersionId);
		WorkflowVersion to = requireVersion(toVersionId);
		if (!from.getWorkflowDefinitionId().equals(to.getWorkflowDefinitionId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Versions to compare must belong to the same workflow definition.");
		}
		List<VersionDiffEntry> entries = new ArrayList<>();

		// States: by code
		Map<String, WorkflowState> fromStates = from.getStates().stream()
			.collect(Collectors.toMap(WorkflowState::getStateCode, Function.identity()));
		Map<String, WorkflowState> toStates = to.getStates().stream()
			.collect(Collectors.toMap(WorkflowState::getStateCode, Function.identity()));
		for (String code : fromStates.keySet()) {
			if (!toStates.containsKey(code)) {
				entries.add(new VersionDiffEntry("STATE", "REMOVED", code,
						"State removed in revision " + to.getVersionNumber()));
			} else {
				WorkflowState fs = fromStates.get(code);
				WorkflowState ts = toStates.get(code);
				if (fs.getStateType() != ts.getStateType() || fs.isInitial() != ts.isInitial()
						|| fs.isTerminal() != ts.isTerminal()
						|| !java.util.Objects.equals(fs.getStateName(), ts.getStateName())) {
					entries.add(new VersionDiffEntry("STATE", "MODIFIED", code,
							"State type/initial/terminal/name differs between revisions"));
				}
			}
		}
		for (String code : toStates.keySet()) {
			if (!fromStates.containsKey(code)) {
				entries.add(new VersionDiffEntry("STATE", "ADDED", code,
						"State added in revision " + to.getVersionNumber()));
			}
		}

		// Steps: by code
		Map<String, WorkflowStep> fromSteps = from.getSteps().stream()
			.collect(Collectors.toMap(WorkflowStep::getStepCode, Function.identity()));
		Map<String, WorkflowStep> toSteps = to.getSteps().stream()
			.collect(Collectors.toMap(WorkflowStep::getStepCode, Function.identity()));
		for (String code : fromSteps.keySet()) {
			if (!toSteps.containsKey(code)) {
				entries.add(new VersionDiffEntry("STEP", "REMOVED", code,
						"Step removed in revision " + to.getVersionNumber()));
			} else {
				WorkflowStep fs = fromSteps.get(code);
				WorkflowStep ts = toSteps.get(code);
				if (fs.getStepOrder() != ts.getStepOrder() || fs.getApprovalMode() != ts.getApprovalMode()
						|| fs.getMinimumApprovals() != ts.getMinimumApprovals()
						|| fs.isMandatory() != ts.isMandatory()
						|| !java.util.Objects.equals(fs.getStepName(), ts.getStepName())
						|| approversDiffer(fs, ts)) {
					entries.add(new VersionDiffEntry("STEP", "MODIFIED", code,
							"Step order, mode, quorum, name or approvers differ between revisions"));
				}
			}
		}
		for (String code : toSteps.keySet()) {
			if (!fromSteps.containsKey(code)) {
				entries.add(new VersionDiffEntry("STEP", "ADDED", code,
						"Step added in revision " + to.getVersionNumber()));
			}
		}

		// Transitions: by (fromStateCode, actionCode)
		Map<String, String> fromStateCodes = from.getStates().stream()
			.collect(Collectors.toMap(WorkflowState::getId, WorkflowState::getStateCode));
		Map<String, String> toStateCodes = to.getStates().stream()
			.collect(Collectors.toMap(WorkflowState::getId, WorkflowState::getStateCode));
		Map<String, WorkflowTransition> fromT = transitionMap(from, fromStateCodes);
		Map<String, WorkflowTransition> toT = transitionMap(to, toStateCodes);
		for (Map.Entry<String, WorkflowTransition> e : fromT.entrySet()) {
			WorkflowTransition tt = toT.get(e.getKey());
			if (tt == null) {
				entries.add(new VersionDiffEntry("TRANSITION", "REMOVED", e.getKey(),
						"Transition removed in revision " + to.getVersionNumber()));
			} else {
				String fromTo = describeTarget(e.getValue().getToStateId(), fromStateCodes);
				String toTo = describeTarget(tt.getToStateId(), toStateCodes);
				if (!fromTo.equals(toTo)
						|| !java.util.Objects.equals(e.getValue().getConditionRuleId(), tt.getConditionRuleId())) {
					entries.add(new VersionDiffEntry("TRANSITION", "MODIFIED", e.getKey(),
							"Transition target or condition differs between revisions"));
				}
			}
		}
		for (Map.Entry<String, WorkflowTransition> e : toT.entrySet()) {
			if (!fromT.containsKey(e.getKey())) {
				entries.add(new VersionDiffEntry("TRANSITION", "ADDED", e.getKey(),
						"Transition added in revision " + to.getVersionNumber()));
			}
		}

		return new VersionCompareResponse(from.getId(), from.getVersionNumber(), to.getId(),
				to.getVersionNumber(), entries);
	}

	private static boolean approversDiffer(WorkflowStep a, WorkflowStep b) {
		java.util.Set<String> sa = a.getApprovers().stream()
			.map(ap -> ap.getApproverType().name() + "|" + ap.getRoleId() + "|" + ap.getUserId() + "|"
					+ ap.getCommitteeLevelId())
			.collect(Collectors.toSet());
		java.util.Set<String> sb = b.getApprovers().stream()
			.map(ap -> ap.getApproverType().name() + "|" + ap.getRoleId() + "|" + ap.getUserId() + "|"
					+ ap.getCommitteeLevelId())
			.collect(Collectors.toSet());
		return !sa.equals(sb);
	}

	private static Map<String, WorkflowTransition> transitionMap(WorkflowVersion v, Map<String, String> idToCode) {
		Map<String, WorkflowTransition> map = new java.util.LinkedHashMap<>();
		for (WorkflowTransition t : v.getTransitions()) {
			String key = idToCode.getOrDefault(t.getFromStateId(), "?") + "::" + t.getActionCode().name();
			map.put(key, t);
		}
		return map;
	}

	private static String describeTarget(UUID toStateId, Map<String, String> idToCode) {
		if (toStateId == null) return "<terminal>";
		return idToCode.getOrDefault(toStateId, "?");
	}

	// ---------------------------------------------------------------------------------------------
	// Validation
	// ---------------------------------------------------------------------------------------------

	/**
	 * Reports every reason a revision cannot be published, plus the ones worth knowing about anyway.
	 *
	 * <p>Read-only and side-effect free so the designer can call it on every save.
	 */
	@Transactional(readOnly = true)
	public ValidationResponse validate(UUID versionId) {
		WorkflowVersion version = requireVersion(versionId);
		List<ValidationIssue> issues = new ArrayList<>(inspect(version));
		boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
		return new ValidationResponse(versionId, valid, issues);
	}

	/**
	 * The validation rules, as one function so publication and the validate endpoint cannot disagree.
	 *
	 * <p>Ordered from structural to operational: a missing state is a harder problem than a vacant office,
	 * and the administrator should fix the former first.
	 */
	private List<ValidationIssue> inspect(WorkflowVersion version) {
		List<ValidationIssue> issues = new ArrayList<>();
		List<WorkflowStep> chain = version.getSteps();
		List<WorkflowState> versionStates = version.getStates();

		// --- structure -------------------------------------------------------------------------
		if (chain.isEmpty()) {
			issues.add(ValidationIssue.error("steps", "A workflow must have at least one approval step."));
		}
		if (versionStates.isEmpty()) {
			issues.add(ValidationIssue.error("states", "A workflow must declare its states."));
		}
		long initials = versionStates.stream().filter(WorkflowState::isInitial).count();
		if (initials != 1) {
			issues.add(ValidationIssue.error("states",
					"A workflow must have exactly one initial state; this one has " + initials + "."));
		}
		for (WorkflowState.StateType required : new WorkflowState.StateType[] { WorkflowState.StateType.APPROVED,
				WorkflowState.StateType.REJECTED, WorkflowState.StateType.CANCELLED }) {
			if (versionStates.stream().noneMatch(state -> state.getStateType() == required)) {
				issues.add(ValidationIssue.error("states",
						"A workflow must define a " + required + " state, or an action that reaches it cannot "
								+ "complete."));
			}
		}

		// --- steps -----------------------------------------------------------------------------
		Set<Short> orders = new HashSet<>();
		Set<String> codes = new HashSet<>();
		for (WorkflowStep step : chain) {
			String field = "steps[" + step.getStepCode() + "]";
			if (!codes.add(step.getStepCode())) {
				issues.add(ValidationIssue.error(field, "Two steps share the code " + step.getStepCode() + "."));
			}
			if (!orders.add(step.getStepOrder())) {
				issues.add(ValidationIssue.error(field,
						"Two steps share position " + step.getStepOrder() + ", so their order is undefined."));
			}
			if (step.getApprovers().isEmpty()) {
				issues.add(ValidationIssue.error(field,
						"No approver is designated for this step, so it could never be completed."));
			}
			// BRD 24.1: an impossible quorum must be prevented. A step that requires more approvals than
			// there are designations to resolve can never complete.
			if (step.getApprovalMode() == WorkflowStep.ApprovalMode.SEQUENTIAL && step.getMinimumApprovals() > 1) {
				issues.add(ValidationIssue.warning(field,
						"A sequential step requires " + step.getMinimumApprovals()
								+ " approvals; only one approver acts at a time, so the engine caps the quorum at "
								+ "the number of tasks opened."));
			}
			for (WorkflowStepApprover approver : step.getApprovers()) {
				// BRD 24.1: "missing approvers". A designation whose reference does not exist resolves to
				// nobody at submission, which surfaces as a failed request rather than a clear configuration
				// error. Checking it here moves the discovery to publication time.
				if (approver.getApproverType() == WorkflowStepApprover.ApproverType.EXPENSE_COMMITTEE
						&& approver.getCommitteeLevelId() != null) {
					issues.add(ValidationIssue.warning(field,
							"A committee step is configured. Committee membership is resolved at submission; if "
									+ "no member is seated the request will fail to start."));
				}
				if (approver.getApproverType() == WorkflowStepApprover.ApproverType.RECOMMENDING_AUTHORITY) {
					issues.add(ValidationIssue.warning(field,
							"This step resolves the department's recommending authority. A department with no "
									+ "designated authority will fail to start, which is the intended behaviour."));
				}
			}
		}
		// A gap in the order is not an error — steps 1, 5 and 9 route in the order 1, 5, 9 — but it is worth
		// reporting because it usually means a step was deleted and the rest were not renumbered.
		List<short[]> gaps = new ArrayList<>();
		if (!orders.isEmpty()) {
			short highest = orders.stream().max(Short::compareTo).orElse((short) 0);
			if (orders.size() != highest) {
				gaps.add(new short[] { highest });
			}
		}
		if (!gaps.isEmpty()) {
			issues.add(ValidationIssue.warning("steps",
					"Step positions are not contiguous. The chain still runs in order, but a gap usually means a "
							+ "step was removed without renumbering the rest."));
		}

		// --- transitions -----------------------------------------------------------------------
		if (!version.getTransitions().isEmpty()) {
			Map<UUID, WorkflowState> byId = versionStates.stream()
				.collect(Collectors.toMap(WorkflowState::getId, Function.identity()));
			Set<UUID> reachable = new HashSet<>();
			if (versionStates.stream().anyMatch(WorkflowState::isInitial)) {
				WorkflowState initial = versionStates.stream().filter(WorkflowState::isInitial).findFirst().orElseThrow();
				reachable.add(initial.getId());
			}
			boolean grew = true;
			while (grew) {
				grew = false;
				for (WorkflowTransition transition : version.getTransitions()) {
					if (transition.getToStateId() == null || !reachable.contains(transition.getFromStateId())) {
						continue;
					}
					if (reachable.add(transition.getToStateId())) {
						grew = true;
					}
				}
			}
			for (WorkflowTransition transition : version.getTransitions()) {
				if (!byId.containsKey(transition.getFromStateId())) {
					issues.add(ValidationIssue.error("transitions",
							"A transition leaves a state that is not part of this revision."));
				}
				if (transition.getToStateId() != null && !byId.containsKey(transition.getToStateId())) {
					issues.add(ValidationIssue.error("transitions",
							"A transition enters a state that is not part of this revision."));
				}
				if (!reachable.contains(transition.getFromStateId())) {
					issues.add(ValidationIssue.warning("transitions",
							"The " + transition.getActionCode() + " transition starts from an unreachable state."));
				}
			}
			// BRD 24.1: circular routes. A cycle that contains no terminal state can never finish, so it is an
			// error; a cycle that can exit is legitimate (revert and resubmit is exactly that shape).
			if (!hasExit(version, versionStates)) {
				issues.add(ValidationIssue.error("transitions",
						"Every route through this workflow is circular and can never reach a terminal state."));
			}
		}
		return issues;
	}

	/** True when at least one declared transition leads to a terminal state. */
	private boolean hasExit(WorkflowVersion version, List<WorkflowState> versionStates) {
		Set<UUID> terminalIds = versionStates.stream()
			.filter(WorkflowState::isTerminal)
			.map(WorkflowState::getId)
			.collect(Collectors.toSet());
		return version.getTransitions().stream()
			.anyMatch(transition -> transition.getToStateId() != null
					&& terminalIds.contains(transition.getToStateId()));
	}

	// ---------------------------------------------------------------------------------------------
	// Publication
	// ---------------------------------------------------------------------------------------------

	/**
	 * Publishes a draft revision and retires whichever one it replaces.
	 *
	 * <h2>Both halves in one transaction</h2>
	 * A partial success here is the worst outcome: the new revision published while the old one is still
	 * published violates the single-published index, and the old one retired while the new one is not
	 * published leaves the definition unable to route anything. The retire happens first because the unique
	 * index permits only one PUBLISHED row at a time.
	 */
	@Transactional
	public VersionResponse publish(UUID versionId, PublishRequest request, UUID publisher) {
		WorkflowVersion draft = requireVersion(versionId);
		if (!draft.isEditable()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"Only a draft revision can be published; this one is " + draft.getStatus() + ".");
		}
		List<ValidationIssue> issues = inspect(draft);
		List<ValidationIssue> errors = issues.stream().filter(issue -> "ERROR".equals(issue.severity())).toList();
		if (!errors.isEmpty()) {
			// The first error is named in the message; all of them are in the response body.
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"This workflow cannot be published: " + errors.get(0).message()
							+ " (" + errors.size() + " error" + (errors.size() == 1 ? "" : "s") + " in total)");
		}
		WorkflowDefinition definition = requireDefinition(draft.getWorkflowDefinitionId());
		if (!definition.isActive()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"This workflow definition is deactivated; reactivate it before publishing a revision.");
		}
		Instant now = Instant.now();
		Instant effectiveFrom = request == null || request.effectiveFrom() == null ? now : request.effectiveFrom();
		if (effectiveFrom.isBefore(now.minusSeconds(1))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A revision cannot be published with an effective date in the past.");
		}

		Optional<WorkflowVersion> incumbent = this.versions.findPublished(definition.getId());
		incumbent.ifPresent(previous -> {
			previous.retire(publisher, now, draft.getId());
			this.versions.save(previous);
			this.audit.record(AuditAction.UPDATE, "WorkflowVersion", previous.getId());
			log.info("workflow.version_retired definitionId={} version={} replacedBy={}", definition.getId(),
					previous.getVersionNumber(), draft.getVersionNumber());
		});
		draft.publish(publisher, effectiveFrom);
		WorkflowVersion published = this.versions.save(draft);
		this.audit.record(AuditAction.PUBLISH, "WorkflowVersion", published.getId());
		log.info("workflow.version_published definitionId={} version={} publisher={}", definition.getId(),
				published.getVersionNumber(), publisher);
		return VersionResponse.from(published);
	}

	/**
	 * Retires a published revision without replacing it.
	 *
	 * <p>Afterwards the definition can start nothing. This is how an administrator switches an approval
	 * route off; in-flight requests keep the version they pinned and are unaffected.
	 */
	@Transactional
	public VersionResponse retire(UUID versionId, UUID actor, String reason) {
		WorkflowVersion version = requireVersion(versionId);
		if (version.getStatus() != WorkflowVersion.Status.PUBLISHED) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"Only a published revision can be retired; this one is " + version.getStatus() + ".");
		}
		version.retire(actor, Instant.now(), null);
		this.versions.save(version);
		this.audit.record(AuditAction.UPDATE, "WorkflowVersion", versionId);
		log.info("workflow.version_retired_standalone versionId={} actor={} reason={}", versionId, actor, reason);
		return VersionResponse.from(version);
	}

	// ---------------------------------------------------------------------------------------------
	// Resolution: which revision governs a submission
	// ---------------------------------------------------------------------------------------------

	/**
	 * The revision a submission of this request type must start.
	 *
	 * <p>Fails closed. A request type with no active definition, or with a definition whose active revision
	 * was retired, cannot be submitted at all — the alternative would be to accept the request into a
	 * workflow that does not exist and leave it unsendable.
	 */
	@Transactional(readOnly = true)
	public WorkflowVersion requirePublishedForRequestType(UUID requestTypeId) {
		if (requestTypeId == null) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"This request has no request type, so no workflow can be selected.");
		}
		WorkflowDefinition definition = this.definitions.findFirstByRequestTypeIdAndActiveTrue(requestTypeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"No active workflow is configured for this request type, so it cannot be submitted."));
		return this.versions.findPublished(definition.getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The workflow for this request type has no published revision, so it cannot route requests. "
							+ "An administrator must publish one."));
	}

	// ---------------------------------------------------------------------------------------------
	// Graph application
	// ---------------------------------------------------------------------------------------------

	private void applyGraph(WorkflowVersion draft, SaveDraftRequest request) {
		if (!draft.isEditable()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"A published revision cannot be modified. Create a new revision instead.");
		}
		// Replace rather than merge: what the caller last sent is the whole graph.
		draft.getSteps().clear();
		draft.getStates().clear();
		draft.getTransitions().clear();
		this.versions.saveAndFlush(draft);

		List<StateRequest> requestedStates = requestedStates(request);
		Map<String, WorkflowState> statesByCode = new java.util.HashMap<>();
		for (StateRequest stateRequest : requestedStates) {
			WorkflowState state = new WorkflowState(draft, stateRequest.stateCode().trim(), stateRequest.stateName(),
					stateRequest.stateType());
			draft.getStates().add(state);
			statesByCode.put(state.getStateCode(), state);
		}
		for (StepRequest stepRequest : request.steps()) {
			WorkflowStep step = new WorkflowStep(draft, stepRequest.stepCode().trim(), stepRequest.stepName(),
					stepRequest.stepOrder(), stepRequest.approvalMode(), stepRequest.minimumApprovals());
			draft.getSteps().add(step);
			for (ApproverRequest approverRequest : stepRequest.approvers()) {
				WorkflowStepApprover approver = new WorkflowStepApprover(step, approverRequest.approverType(),
						approverRequest.roleId(), approverRequest.userId(), null,
						approverRequest.committeeLevelId());
				step.getApprovers().add(approver);
			}
		}
		// States must be flushed before transitions can reference their ids.
		WorkflowVersion withStates = this.versions.saveAndFlush(draft);
		if (request.transitions() != null) {
			for (TransitionRequest transitionRequest : request.transitions()) {
				WorkflowState from = statesByCode.get(transitionRequest.fromStateCode());
				if (from == null) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED,
							"A transition leaves an unknown state: " + transitionRequest.fromStateCode() + ".");
				}
				WorkflowState to = null;
				if (transitionRequest.toStateCode() != null) {
					to = statesByCode.get(transitionRequest.toStateCode());
					if (to == null) {
						throw new BusinessException(ErrorCode.VALIDATION_FAILED,
								"A transition enters an unknown state: " + transitionRequest.toStateCode() + ".");
					}
				}
				withStates.getTransitions().add(new WorkflowTransition(withStates, from.getId(),
						to == null ? null : to.getId(), transitionRequest.actionCode(),
						transitionRequest.conditionRuleId()));
			}
		}
	}

	/**
	 * The states a revision must have: whatever the caller asked for, plus the four the engine requires.
	 *
	 * <p>Derived rather than demanded so a designer cannot publish a workflow that starts but cannot finish.
	 * A caller that names these states itself keeps its own codes and names.
	 */
	private List<StateRequest> requestedStates(SaveDraftRequest request) {
		List<StateRequest> requested = new ArrayList<>();
		if (request.states() != null) {
			requested.addAll(request.states());
		}
		addIfAbsent(requested, "DRAFT", "Draft", WorkflowState.StateType.INITIAL);
		addIfAbsent(requested, "IN_APPROVAL", "In approval", WorkflowState.StateType.IN_PROGRESS);
		addIfAbsent(requested, "APPROVED", "Approved", WorkflowState.StateType.APPROVED);
		addIfAbsent(requested, "REJECTED", "Rejected", WorkflowState.StateType.REJECTED);
		addIfAbsent(requested, "CANCELLED", "Cancelled", WorkflowState.StateType.CANCELLED);
		return requested;
	}

	private void addIfAbsent(List<StateRequest> requested, String code, String name, WorkflowState.StateType type) {
		boolean typePresent = requested.stream().anyMatch(state -> state.stateType() == type);
		if (typePresent) {
			return;
		}
		boolean codeTaken = requested.stream().anyMatch(state -> code.equalsIgnoreCase(state.stateCode()));
		if (codeTaken) {
			return;
		}
		requested.add(new StateRequest(code, name, type));
	}

	// ---------------------------------------------------------------------------------------------
	// Lookups
	// ---------------------------------------------------------------------------------------------

	private WorkflowDefinition requireDefinition(UUID id) {
		return this.definitions.findById(id).orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", id));
	}

	private WorkflowVersion requireVersion(UUID id) {
		return this.versions.findById(id).orElseThrow(() -> new ResourceNotFoundException("WorkflowVersion", id));
	}

}
