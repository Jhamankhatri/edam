package com.edam.businessrules.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

	public BusinessRuleService(BusinessRuleSetRepository sets,
			BusinessRuleVersionRepository versions,
			BusinessRuleRepository rules,
			BusinessRuleEvaluationRepository eval,
			RuleEngine engine, AuditRecorder audit) {
		this.sets = sets;
		this.versions = versions;
		this.rules = rules;
		this.eval = eval;
		this.engine = engine;
		this.audit = audit;
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
		BusinessRuleSet s = this.sets.findByIdWithVersions(ruleSetId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule set not found"));
		BusinessRuleVersion draft = s.getVersions().stream()
				.filter(v -> v.getStatus() == RuleVersionStatus.DRAFT)
				.findFirst().orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "No DRAFT version to publish."));

		// Publish is an integrity-sensitive operation; lock the draft row to prevent
		// concurrent publications from silently clobbering each other.
		draft = this.versions.lockById(draft.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Draft disappeared"));

		// Validate every rule before flipping status.
		RuleEngine.EvaluationContext validationCtx = RuleEngine.EvaluationContext.of(Map.of());
		for (BusinessRule r : draft.getRules()) {
			Map<String, Object> cond = this.engine.parseCondition(r.getConditionDefinition());
			this.engine.validateCondition(cond, validationCtx);
			this.engine.parseAction(r.getActionDefinition());
		}

		// Retire any previously PUBLISHED version; the partial unique index on (rule_set_id)
		// WHERE status='PUBLISHED' enforces that at most one PUBLISHED exists, so retire first.
		List<BusinessRuleVersion> published = s.getVersions().stream()
				.filter(v -> v.getStatus() == RuleVersionStatus.PUBLISHED).toList();
		Instant now = Instant.now();
		for (BusinessRuleVersion p : published) {
			p.markRetired(now);
			this.audit.record(AuditAction.DEACTIVATE, "BusinessRuleVersion", p.getId(),
					"retired v" + p.getVersionNumber() + " in favour of v" + draft.getVersionNumber());
		}

		Instant effFrom = (req != null && req.effectiveFrom() != null) ? req.effectiveFrom() : now;
		if (effFrom.isBefore(now.minusSeconds(1))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "effectiveFrom cannot be in the past.");
		}
		draft.markPublished(caller.userId(), now);
		draft.setEffectiveFrom(effFrom);
		// Optimistic-lock version bump happens on save; @Version will reject stale writes.
		this.versions.save(draft);
		this.sets.save(s);
		this.audit.record(AuditAction.PUBLISH, "BusinessRuleVersion", draft.getId(),
				"published v" + draft.getVersionNumber() + " of " + s.getCode());
		return toDetail(draft, true);
	}

	@Transactional
	public RuleDtos.RuleVersionDetail retirePublished(UUID ruleSetId, EdamPrincipal caller) {
		BusinessRuleSet s = this.sets.findByIdWithVersions(ruleSetId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule set not found"));
		BusinessRuleVersion published = s.getVersions().stream()
				.filter(v -> v.getStatus() == RuleVersionStatus.PUBLISHED)
				.findFirst().orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "No PUBLISHED version to retire."));
		published = this.versions.lockById(published.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Published version disappeared"));
		published.markRetired(Instant.now());
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
		// Validate the condition/action syntax now (deeper context validation happens on publish).
		Map<String, Object> cond = this.engine.parseCondition(this.engine.writeCondition(req.condition() == null ? Map.of("all", List.of()) : req.condition()));
		this.engine.validateCondition(cond, RuleEngine.EvaluationContext.of(Map.of()));
		Map<String, Object> act = this.engine.parseAction(this.engine.writeAction(req.action() == null ? Map.of("type", "WARN") : req.action()));

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

	/** Evaluate the active rules for a given module+rule set against the supplied context.
	 *  Returns ordered list of matched actions (priority order). */
	@Transactional(readOnly = true)
	public List<RuleDtos.RuleDetail> evaluate(String module, String ruleSetCode, RuleEngine.EvaluationContext ctx) {
		List<ActiveRule> rules = this.eval.loadActiveRules(module, ruleSetCode);
		List<RuleDtos.RuleDetail> matched = new ArrayList<>();
		for (ActiveRule ar : rules) {
			Map<String, Object> cond = this.engine.parseCondition(ar.conditionJson());
			try {
				if (this.engine.evaluate(cond, ctx)) {
					Map<String, Object> action = this.engine.parseAction(ar.actionJson());
					matched.add(new RuleDtos.RuleDetail(ar.ruleId(), ar.ruleCode(), ar.ruleCode(),
							cond, action, ar.priority(), true));
				}
			} catch (BusinessException bex) {
				// Safe-fail: log and continue rather than silently treating as match or throwing
				// up into a caller that cannot correct the data. Publish-time validation should
				// prevent this path for well-formed rules.
				log.warn("Rule {} evaluation failed safely: {}", ar.ruleCode(), bex.getMessage());
			}
		}
		return matched;
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
