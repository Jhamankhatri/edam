package com.edam.businessrules.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.edam.businessrules.dto.RuleDtos;
import com.edam.businessrules.model.BusinessRule;
import com.edam.businessrules.model.BusinessRuleSet;
import com.edam.businessrules.model.BusinessRuleVersion;
import com.edam.businessrules.model.RuleVersionStatus;
import com.edam.businessrules.repository.BusinessRuleEvaluationRepository;
import com.edam.businessrules.repository.BusinessRuleEvaluationRepository.ActiveRule;
import com.edam.businessrules.repository.BusinessRuleRepository;
import com.edam.businessrules.repository.BusinessRuleSetRepository;
import com.edam.businessrules.repository.BusinessRuleVersionRepository;
import com.edam.businessrules.spi.RuleFieldContributor;
import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.security.EdamPrincipal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages lifecycle of {@link BusinessRuleSet}s and their versions. The service enforces
 * transactional, optimistic-locking, and audit invariants. Complex SQL lives in the
 * repositories; the service coordinates object-graph operations and validation.
 */
@Service
public class BusinessRuleService {

	private static final Logger log = LoggerFactory.getLogger(BusinessRuleService.class);

	private final BusinessRuleSetRepository sets;
	private final BusinessRuleVersionRepository versions;
	private final BusinessRuleRepository rules;
	private final BusinessRuleEvaluationRepository eval;
	private final RuleEngine engine;
	private final AuditRecorder audit;
	private final Map<String, RuleFieldContributor> contributorsByModule;

	public BusinessRuleService(BusinessRuleSetRepository sets,
			BusinessRuleVersionRepository versions,
			BusinessRuleRepository rules,
			BusinessRuleEvaluationRepository eval,
			RuleEngine engine, AuditRecorder audit,
			List<RuleFieldContributor> contributors) {
		this.sets = sets;
		this.versions = versions;
		this.rules = rules;
		this.eval = eval;
		this.engine = engine;
		this.audit = audit;
		Map<String, RuleFieldContributor> m = new LinkedHashMap<>();
		for (RuleFieldContributor c : contributors) m.put(c.moduleCode(), c);
		this.contributorsByModule = Map.copyOf(m);
	}

	/** Returns the field/action whitelist contributor for a module, or throws if the module
	 *  has no contributor — publishing rules against an unknown module is fail-closed: we
	 *  cannot validate fields safely, so we refuse to publish. */
	private RuleFieldContributor contributorFor(String module) {
		RuleFieldContributor c = this.contributorsByModule.get(module);
		if (c == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"No field whitelist registered for module \"" + module
					+ "\". A RuleFieldContributor bean must be provided before rules can be published.");
		}
		return c;
	}

	private RuleEngine.ActionSchema schemaFor(String module) {
		RuleFieldContributor c = contributorFor(module);
		return new RuleEngine.ActionSchema() {
			@Override public boolean allowsType(String t) { return c.allowedActionTypes().contains(t); }
			@Override public Set<String> allowedParameters(String t) { return c.allowedActionParameters(t); }
		};
	}

	// --------------------------------------------------------------------------------
	// Rule-set CRUD
	// --------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<RuleDtos.RuleSetSummary> listRuleSets() {
		return this.sets.findAllWithVersions().stream().map(this::summarize).toList();
	}

	@Transactional(readOnly = true)
	public RuleDtos.RuleVersionDetail getRuleSet(UUID id) {
		BusinessRuleSet s = this.sets.findByIdWithVersions(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule set not found"));
		BusinessRuleVersion v = draftOrLatest(s);
		if (v == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "No version found for rule set");
		}
		// Reload the chosen version with rules eagerly fetched so toDetail can enumerate them.
		v = this.versions.findByIdWithRules(v.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Version disappeared"));
		return toDetail(v, true);
	}

	@Transactional
	public RuleDtos.RuleSetSummary createRuleSet(RuleDtos.CreateRuleSetRequest req, EdamPrincipal caller) {
		if (this.sets.existsByCode(req.code())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A rule set with that code already exists.");
		}
		BusinessRuleSet s = new BusinessRuleSet();
		s.setCode(req.code());
		s.setName(req.name());
		s.setModule(req.module());
		s.setDescription(req.description());
		s.setActive(true);
		s.draftVersion(); // seed DRAFT v1
		BusinessRuleSet saved = this.sets.save(s);
		this.audit.record(AuditAction.CREATE, "BusinessRuleSet", saved.getId(),
				"created " + saved.getCode());
		return summarize(saved);
	}

	@Transactional
	public RuleDtos.RuleSetSummary updateRuleSet(UUID id, RuleDtos.UpdateRuleSetRequest req, EdamPrincipal caller) {
		BusinessRuleSet s = this.sets.findByIdWithVersions(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule set not found"));
		Map<String, Object> before = diff(s, req);
		if (req.name() != null) s.setName(req.name());
		if (req.module() != null) s.setModule(req.module());
		if (req.description() != null) s.setDescription(req.description());
		if (req.active() != null) s.setActive(req.active());
		BusinessRuleSet saved = this.sets.save(s);
		if (!before.isEmpty()) {
			this.audit.recordChange(AuditAction.UPDATE, "BusinessRuleSet", saved.getId(), before, diff(s, null));
		}
		if (req.active() != null) {
			this.audit.record(Boolean.TRUE.equals(req.active()) ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
					"BusinessRuleSet", saved.getId(), req.active() ? "activated" : "deactivated");
		}
		return summarize(saved);
	}

	// --------------------------------------------------------------------------------
	// Version lifecycle
	// --------------------------------------------------------------------------------

	@Transactional
	public RuleDtos.RuleVersionDetail newDraft(UUID ruleSetId, EdamPrincipal caller) {
		BusinessRuleSet s = this.sets.findByIdWithVersions(ruleSetId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule set not found"));
		BusinessRuleVersion existingDraft = s.getVersions().stream()
				.filter(v -> v.getStatus() == RuleVersionStatus.DRAFT).findFirst().orElse(null);
		if (existingDraft != null) {
			return toDetail(existingDraft, true);
		}
		BusinessRuleVersion v = s.draftVersion();
		this.sets.save(s);
		this.audit.record(AuditAction.CREATE, "BusinessRuleVersion", v.getId(),
				"new draft v" + v.getVersionNumber() + " on " + s.getCode());
		return toDetail(v, true);
	}

	@Transactional
	public RuleDtos.RuleVersionDetail publish(UUID ruleSetId, RuleDtos.PublishRequest req, EdamPrincipal caller) {
		// 1. Locate the current draft under a PESSIMISTIC_WRITE lock so concurrent publications
		//    cannot clobber each other, and join-fetch rules for validation.
		BusinessRuleVersion draft = this.versions.findByRuleSetAndStatusWithRules(ruleSetId, RuleVersionStatus.DRAFT)
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "No DRAFT version to publish."));
		// Acquire a row-level lock via the lock query (same id, re-selected with LockModeType.PESSIMISTIC_WRITE).
		this.versions.lockById(draft.getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Draft disappeared"));
		BusinessRuleSet s = draft.getRuleSet();

		// Validate every rule against the module's real field/action whitelist. wildcard()
		// is NOT used here: unknown fields/ops/actions/parameters are rejected BEFORE publish
		// so malformed rules never reach evaluation.
		RuleFieldContributor contrib = contributorFor(s.getModule());
		RuleEngine.EvaluationContext fieldCtx = RuleEngine.EvaluationContext.forFields(contrib.exposedFields());
		RuleEngine.ActionSchema actionSchema = schemaFor(s.getModule());
		for (BusinessRule r : draft.getRules()) {
			Map<String, Object> cond = this.engine.parseCondition(r.getConditionDefinition());
			this.engine.validateCondition(cond, fieldCtx);
			this.engine.parseAction(r.getActionDefinition(), actionSchema);
		}

		// Retire any previously PUBLISHED version; the partial unique index enforces at most one.
		Instant now = Instant.now();
		this.versions.findByRuleSetAndStatusWithRules(ruleSetId, RuleVersionStatus.PUBLISHED).ifPresent(p -> {
			if (!p.getId().equals(draft.getId())) {
				p.markRetired(now);
				this.audit.record(AuditAction.DEACTIVATE, "BusinessRuleVersion", p.getId(),
						"retired v" + p.getVersionNumber() + " in favour of v" + draft.getVersionNumber());
			}
		});

		Instant effFrom = (req != null && req.effectiveFrom() != null) ? req.effectiveFrom() : now;
		if (effFrom.isBefore(now.minusSeconds(1))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "effectiveFrom cannot be in the past.");
		}
		draft.setEffectiveFrom(effFrom);
		draft.markPublished(caller.userId(), now);
		this.versions.save(draft);
		this.audit.record(AuditAction.PUBLISH, "BusinessRuleVersion", draft.getId(),
			"published v" + draft.getVersionNumber() + " of " + s.getCode());
		return toDetail(draft, true);
	}

	@Transactional
	public RuleDtos.RuleVersionDetail retirePublished(UUID ruleSetId, EdamPrincipal caller) {
		BusinessRuleVersion published = this.versions.findByRuleSetAndStatusWithRules(ruleSetId, RuleVersionStatus.PUBLISHED)
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "No PUBLISHED version to retire."));
		// Acquire pessimistic lock to serialize concurrent retirements.
		this.versions.lockById(published.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Published version disappeared"));
		Instant now = Instant.now();
		published.markRetired(now);
		this.versions.save(published);
		this.audit.record(AuditAction.DEACTIVATE, "BusinessRuleVersion", published.getId(),
				"retired v" + published.getVersionNumber());
		return toDetail(published, true);
	}

	// --------------------------------------------------------------------------------
	// Rule upsert within a DRAFT version
	// --------------------------------------------------------------------------------

	@Transactional
	public RuleDtos.RuleDetail upsertRule(UUID versionId, RuleDtos.RuleUpsertRequest req, EdamPrincipal caller) {
		BusinessRuleVersion v = this.versions.findByIdWithRules(versionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Version not found"));
		if (v.getStatus() != RuleVersionStatus.DRAFT) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Rules can only be edited in DRAFT versions.");
		}
		// Resolve module through the owning set and validate against the module's field/action
		// whitelist. Upsert rejects unknown fields/ops/actions/parameters so a bad rule cannot be
		// saved and later "fixed" by a publish — fail as early as possible.
		UUID setId = v.getRuleSet().getId(); // works on lazy proxy without initialising it
		BusinessRuleSet s = this.sets.findById(setId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule set not found for version"));
		RuleFieldContributor contrib = contributorFor(s.getModule());
		Map<String, Object> cond = this.engine.parseCondition(this.engine.writeCondition(req.condition() == null ? Map.of("all", List.of()) : req.condition()));
		this.engine.validateCondition(cond, RuleEngine.EvaluationContext.forFields(contrib.exposedFields()));
		Map<String, Object> act = this.engine.parseAction(
				this.engine.writeAction(req.action() == null ? Map.of("type", "WARN") : req.action()),
				schemaFor(s.getModule()));

		BusinessRule target;
		if (req.id() != null) {
			target = v.getRules().stream().filter(r -> Objects.equals(r.getId(), req.id())).findFirst()
					.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule not found in this version"));
		} else {
			boolean codeExists = v.getRules().stream().anyMatch(r -> Objects.equals(r.getCode(), req.code()));
			if (codeExists) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A rule with code " + req.code() + " already exists in this version.");
			target = new BusinessRule();
			target.setRuleVersion(v);
			v.getRules().add(target);
		}
		target.setCode(req.code());
		target.setName(req.name());
		target.setConditionDefinition(this.engine.writeCondition(cond));
		target.setActionDefinition(this.engine.writeAction(act));
		if (req.priority() != null) target.setPriority(req.priority());
		if (req.active() != null) target.setActive(req.active());
		this.versions.save(v);
		this.audit.record(req.id() == null ? AuditAction.CREATE : AuditAction.UPDATE,
				"BusinessRule", target.getId(), req.code());
		return toRuleDetail(target);
	}

	@Transactional
	public void deleteRule(UUID ruleId, EdamPrincipal caller) {
		UUID versionId = this.rules.findVersionIdByRuleId(ruleId);
		if (versionId == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "Rule not found");
		}
		BusinessRuleVersion v = this.versions.findByIdWithRules(versionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Version not found"));
		BusinessRule r = v.getRules().stream().filter(x -> Objects.equals(x.getId(), ruleId)).findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule not found"));
		if (v.getStatus() != RuleVersionStatus.DRAFT) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Only DRAFT rules may be deleted.");
		}
		v.getRules().remove(r);
		this.versions.save(v);
		this.audit.record(AuditAction.DELETE, "BusinessRule", r.getId(), r.getCode());
	}

	// --------------------------------------------------------------------------------
	// Evaluation entry point (used by other services like expense-request validation)
	// --------------------------------------------------------------------------------

	/**
	 * Evaluate active rules for (module, ruleSetCode) against a caller-supplied context.
	 *
	 * <p>Returns an {@link RuleDtos.EvaluationOutcome} that explicitly distinguishes MATCH,
	 * NO_MATCH, and ERROR. Callers of mandatory-control dispatch (REJECT_REQUEST,
	 * REQUIRE_APPROVAL_LEVEL, REQUIRE_ATTACHMENT, SET_DELEGATION_TARGET) MUST check
	 * {@code outcome.hasErrors()} and treat an ERROR outcome as a hard stop: no mandatory
	 * control may be skipped because a rule failed to evaluate. It is the caller's
	 * responsibility to convert an ERROR outcome into a safe operation (e.g., block
	 * submission pending operator review). The engine itself does NOT auto-reject, because
	 * blanket rejection is itself a business policy decision.
	 *
	 * <p>Error diagnostics are stable codes (no request data is echoed into logs or into the
	 * returned structure) so audit trails can correlate failures without leaking PII.
	 */
	@Transactional(readOnly = true)
	public RuleDtos.EvaluationOutcome evaluate(String module, String ruleSetCode, RuleEngine.EvaluationContext ctx) {
		// Enforce the field whitelist at evaluation entry point as well, so that a caller that
		// constructs a context with unexpected names cannot bypass publish-time validation via
		// direct service calls.
		RuleFieldContributor contrib = this.contributorsByModule.get(module);
		Set<String> allowed = (contrib != null) ? contrib.exposedFields() : Set.of();
		// Re-wrap the supplied context to enforce the whitelist even if the caller passed a
		// wildcard or a permissive map.
		RuleEngine.EvaluationContext safeCtx = new RuleEngine.EvaluationContext() {
			@Override public boolean isAllowedField(String f) { return allowed.contains(f); }
			@Override public Object get(String f) { return allowed.contains(f) ? ctx.get(f) : null; }
		};

		List<ActiveRule> rules = this.eval.loadActiveRules(module, ruleSetCode);
		List<RuleDtos.RuleEvaluationResult> results = new ArrayList<>(rules.size());
		List<RuleDtos.RuleEvaluationResult> matches = new ArrayList<>();
		List<RuleDtos.RuleEvaluationResult> errors = new ArrayList<>();
		for (ActiveRule ar : rules) {
			Map<String, Object> cond;
			try {
				cond = this.engine.parseCondition(ar.conditionJson());
			} catch (BusinessException bex) {
				RuleDtos.RuleEvaluationResult err = new RuleDtos.RuleEvaluationResult(
						ar.ruleId(), ar.ruleCode(), ar.versionNumber(), ar.priority(),
						RuleDtos.RuleMatchStatus.ERROR, null, "RULE_PARSE_ERROR");
				results.add(err); errors.add(err);
				log.warn("Rule {} v{} parse failed at evaluation: code=RULE_PARSE_ERROR", ar.ruleId(), ar.versionNumber());
				continue;
			}
			try {
				boolean matched = this.engine.evaluate(cond, safeCtx);
				if (matched) {
					Map<String, Object> action = (contrib == null)
							? this.engine.parseAction(ar.actionJson())
							: this.engine.parseAction(ar.actionJson(), schemaFor(module));
					RuleDtos.RuleEvaluationResult r = new RuleDtos.RuleEvaluationResult(
							ar.ruleId(), ar.ruleCode(), ar.versionNumber(), ar.priority(),
							RuleDtos.RuleMatchStatus.MATCH, action, null);
					results.add(r); matches.add(r);
				} else {
					results.add(new RuleDtos.RuleEvaluationResult(
							ar.ruleId(), ar.ruleCode(), ar.versionNumber(), ar.priority(),
							RuleDtos.RuleMatchStatus.NO_MATCH, null, null));
				}
			} catch (BusinessException bex) {
				// ERROR is recorded explicitly; we NEVER treat a failed rule as either matched
				// (would fire unsafe actions) or non-matched (would skip mandatory controls).
				RuleDtos.RuleEvaluationResult err = new RuleDtos.RuleEvaluationResult(
						ar.ruleId(), ar.ruleCode(), ar.versionNumber(), ar.priority(),
						RuleDtos.RuleMatchStatus.ERROR, null, "RULE_EVAL_ERROR");
				results.add(err); errors.add(err);
				log.warn("Rule {} v{} evaluation failed: code=RULE_EVAL_ERROR", ar.ruleId(), ar.versionNumber());
			}
		}
		return new RuleDtos.EvaluationOutcome(List.copyOf(results), List.copyOf(matches), List.copyOf(errors));
	}

	/** Convenience wrapper returning only matched actions. Throws if any rule errored, so
	 *  callers that ask for matches cannot silently skip mandatory controls. */
	@Transactional(readOnly = true)
	public List<RuleDtos.RuleDetail> evaluateMatches(String module, String ruleSetCode, RuleEngine.EvaluationContext ctx) {
		RuleDtos.EvaluationOutcome o = evaluate(module, ruleSetCode, ctx);
		if (o.hasErrors()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Rule evaluation produced " + o.errors().size()
					+ " error(s); mandatory controls cannot be safely applied.");
		}
		List<RuleDtos.RuleDetail> out = new ArrayList<>();
		for (RuleDtos.RuleEvaluationResult r : o.matches()) {
			out.add(new RuleDtos.RuleDetail(r.ruleId(), r.ruleCode(), r.ruleCode(),
					null, r.action(), r.priority(), true));
		}
		return out;
	}

	// --------------------------------------------------------------------------------
	// Mapping
	// --------------------------------------------------------------------------------

	private BusinessRuleVersion draftOrLatest(BusinessRuleSet s) {
		return s.getVersions().stream().filter(v -> v.getStatus() == RuleVersionStatus.DRAFT).findFirst()
				.orElseGet(() -> s.getVersions().stream()
						.filter(v -> v.getStatus() == RuleVersionStatus.PUBLISHED).findFirst()
						.orElse(null));
	}

	private RuleDtos.RuleSetSummary summarize(BusinessRuleSet s) {
		int published = s.getVersions().stream().filter(v -> v.getStatus() == RuleVersionStatus.PUBLISHED).mapToInt(BusinessRuleVersion::getVersionNumber).findFirst().orElse(0);
		int draft = s.getVersions().stream().filter(v -> v.getStatus() == RuleVersionStatus.DRAFT).mapToInt(BusinessRuleVersion::getVersionNumber).findFirst().orElse(0);
		return new RuleDtos.RuleSetSummary(s.getId(), s.getCode(), s.getName(), s.getModule(), s.getDescription(),
				s.isActive(), published, draft);
	}

	private RuleDtos.RuleVersionDetail toDetail(BusinessRuleVersion v, boolean includeRules) {
		List<RuleDtos.RuleDetail> rules;
		if (includeRules) {
			rules = v.getRules().stream().map(this::toRuleDetail).toList();
		} else {
			rules = List.of();
		}
		return new RuleDtos.RuleVersionDetail(v.getId(), v.getVersionNumber(), v.getStatus(),
				v.getEffectiveFrom(), v.getEffectiveTo(), v.getPublishedAt(), v.getPublishedBy(),
				v.getRules().size(), rules);
	}

	private RuleDtos.RuleDetail toRuleDetail(BusinessRule r) {
		Map<String, Object> cond = this.engine.parseCondition(r.getConditionDefinition());
		Map<String, Object> act = this.engine.parseAction(r.getActionDefinition());
		return new RuleDtos.RuleDetail(r.getId(), r.getCode(), r.getName(), cond, act, r.getPriority(), r.isActive());
	}

	private Map<String, Object> diff(BusinessRuleSet s, RuleDtos.UpdateRuleSetRequest req) {
		// Used for audit before/after snapshots. We only record non-null changes; this is
		// sufficient for the audit record's state-diff contract.
		Map<String, Object> m = new LinkedHashMap<>();
		if (req != null) {
			if (req.name() != null && !Objects.equals(req.name(), s.getName())) m.put("name", s.getName());
			if (req.description() != null && !Objects.equals(req.description(), s.getDescription())) m.put("description", s.getDescription());
			if (req.active() != null && req.active() != s.isActive()) m.put("active", s.isActive());
		}
		return m;
	}
}
