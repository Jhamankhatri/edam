package com.edam.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.Auditor;
import com.edam.common.security.InstitutionContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Workflow administration: drafts, validation, publish and rollback (BRD FM-01..FM-06, §8.6).
 *
 * <h2>Version semantics, and why they matter</h2>
 * <ul>
 *   <li>A draft is editable and can never route a request.</li>
 *   <li>Publishing validates the document and makes the version immutable. Any request that starts
 *       afterwards pins that version.</li>
 *   <li>Publishing a new version retires the currently published one. Requests that pinned the
 *       retired version keep using it — this is the mechanism behind "publishing must not affect
 *       in-flight requests".</li>
 *   <li>Rollback re-publishes an earlier version by creating a <em>new</em> version with that
 *       version's document. It does not mutate history: an auditor can still see that the old
 *       version was superseded and by what.</li>
 * </ul>
 *
 * <h2>Validation before publish</h2>
 * {@link WorkflowSteps#parse} rejects an unknown approver token, a duplicate code or sequence, a
 * missing field and an out-of-range quorum. An invalid workflow cannot be published, so a
 * misconfigured document can never reach a live request.
 *
 * <h2>Checksum</h2>
 * The SHA-256 of the document is stored and compared on publish, so an operator can prove the
 * published model is byte-identical to the one that was reviewed.
 */
@Service
public class WorkflowService {

	private final WorkflowDefinitionRepository definitions;

	private final WorkflowVersionRepository versions;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	private final JsonMapper jsonMapper;

	private final Auditor auditor;

	public WorkflowService(WorkflowDefinitionRepository definitions, WorkflowVersionRepository versions,
			InstitutionContext institutionContext, AuditRecorder audit, JsonMapper jsonMapper, Auditor auditor) {
		this.definitions = definitions;
		this.versions = versions;
		this.institutionContext = institutionContext;
		this.audit = audit;
		this.jsonMapper = jsonMapper;
		this.auditor = auditor;
	}

	// ------------------------------------------------------------------
	// Definitions
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<WorkflowDefinition> listDefinitions() {
		return this.definitions
			.findByInstitutionIdOrderByCodeAsc(this.institutionContext.requireInstitutionId());
	}

	@Transactional(readOnly = true)
	public WorkflowDefinition getDefinition(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.definitions.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Workflow definition", id));
	}

	@Transactional
	public WorkflowDefinition createDefinition(String code, String name, String description) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		String cleanCode = code.trim().toUpperCase(java.util.Locale.ROOT);
		if (this.definitions.existsByInstitutionIdAndCode(institutionId, cleanCode)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"A workflow with that code already exists for this institution.");
		}
		WorkflowDefinition definition = new WorkflowDefinition(cleanCode, name.trim(), description);
		definition.assignInstitution(institutionId);
		WorkflowDefinition saved = this.definitions.save(definition);
		this.audit.record(AuditAction.CREATE, "WorkflowDefinition", saved.getId(),
				"Workflow definition '" + cleanCode + "' created");
		return saved;
	}

	// ------------------------------------------------------------------
	// Versions
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<WorkflowVersion> listVersions(Long definitionId) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		getDefinition(definitionId);
		return this.versions.findByInstitutionIdAndDefinitionIdOrderByVersionNumberDesc(institutionId, definitionId);
	}

	/** The version a new submission would pin: the published one. */
	@Transactional(readOnly = true)
	public WorkflowVersion requirePublishedVersion(Long definitionId) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.versions
			.findFirstByInstitutionIdAndDefinitionIdAndStatusOrderByVersionNumberDesc(institutionId, definitionId,
					WorkflowVersionStatus.PUBLISHED)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"This workflow has no published version, so no request can be routed by it."));
	}

	@Transactional
	public WorkflowVersion createDraft(Long definitionId, String definitionJson, String changeNote) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		WorkflowDefinition definition = getDefinition(definitionId);
		// Validate eagerly: an administrator should learn about a malformed document when saving the
		// draft, not when publishing or, worse, when the first request tries to route.
		WorkflowSteps.parse(this.jsonMapper, definitionJson);
		int next = this.versions.maxVersionNumber(definitionId) + 1;
		WorkflowVersion draft = new WorkflowVersion(definition, next, definitionJson,
				checksum(definitionJson), changeNote);
		draft.assignInstitution(institutionId);
		WorkflowVersion saved = this.versions.save(draft);
		this.audit.record(AuditAction.CREATE, "WorkflowVersion", saved.getId(),
				"Workflow draft v" + next + " created for '" + definition.getCode() + "'");
		return saved;
	}

	@Transactional
	public WorkflowVersion updateDraft(Long versionId, String definitionJson, String changeNote) {
		WorkflowVersion version = getVersion(versionId);
		if (!version.isDraft()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"Only a draft can be edited. Create a new version instead of editing a published one.");
		}
		WorkflowSteps.parse(this.jsonMapper, definitionJson);
		version.updateDraft(definitionJson, checksum(definitionJson), changeNote);
		this.audit.record(AuditAction.UPDATE, "WorkflowVersion", versionId,
				"Workflow draft v" + version.getVersionNumber() + " updated");
		return version;
	}

	/**
	 * Publish a draft, retiring the previously published version.
	 *
	 * <p>The two writes happen in one transaction so there is never a moment with two published
	 * versions; the unique index would fail the second insert anyway, but a half-completed publish
	 * would leave the definition unroutable.
	 */
	@Transactional
	public WorkflowVersion publish(Long versionId) {
		WorkflowVersion version = getVersion(versionId);
		if (!version.isDraft()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Only a draft can be published.");
		}
		WorkflowSteps.parse(this.jsonMapper, version.getDefinitionJson());
		Long institutionId = this.institutionContext.requireInstitutionId();
		this.versions
			.findFirstByInstitutionIdAndDefinitionIdAndStatusOrderByVersionNumberDesc(institutionId,
					version.getDefinition().getId(), WorkflowVersionStatus.PUBLISHED)
			.ifPresent(previous -> {
				previous.retire();
				this.audit.record(AuditAction.DEACTIVATE, "WorkflowVersion", previous.getId(),
						"Workflow v" + previous.getVersionNumber() + " retired by v" + version.getVersionNumber());
			});
		version.publish(this.auditor.username(), Instant.now());
		this.audit.record(AuditAction.PUBLISH, "WorkflowVersion", versionId,
				"Workflow v" + version.getVersionNumber() + " published for '"
						+ version.getDefinition().getCode() + "'");
		return version;
	}

	/**
	 * Roll back to an earlier version by publishing its document as a new version.
	 *
	 * <p>Rollback never un-retires or mutates the target version: history has to stay linear for the
	 * audit trail to mean anything. The new version records which version it was restored from in its
	 * change note.
	 */
	@Transactional
	public WorkflowVersion rollback(Long definitionId, int targetVersionNumber) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		WorkflowDefinition definition = getDefinition(definitionId);
		WorkflowVersion target = this.versions
			.findByInstitutionIdAndDefinitionIdOrderByVersionNumberDesc(institutionId, definitionId)
			.stream()
			.filter(version -> version.getVersionNumber() == targetVersionNumber)
			.findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Workflow version " + targetVersionNumber,
					targetVersionNumber));
		int next = this.versions.maxVersionNumber(definitionId) + 1;
		WorkflowVersion restored = new WorkflowVersion(definition, next, target.getDefinitionJson(),
				target.getChecksum(),
				"Rollback to version " + targetVersionNumber + " by " + this.auditor.username());
		restored.assignInstitution(institutionId);
		this.versions
			.findFirstByInstitutionIdAndDefinitionIdAndStatusOrderByVersionNumberDesc(institutionId, definitionId,
					WorkflowVersionStatus.PUBLISHED)
			.ifPresent(previous -> {
				previous.retire();
				this.audit.record(AuditAction.DEACTIVATE, "WorkflowVersion", previous.getId(),
						"Workflow v" + previous.getVersionNumber() + " retired by rollback");
			});
		WorkflowVersion saved = this.versions.save(restored);
		saved.publish(this.auditor.username(), Instant.now());
		this.audit.recordChange(AuditAction.ROLLBACK, "WorkflowVersion", saved.getId(),
				"Workflow rolled back to version " + targetVersionNumber, Map.of("publishedVersionNumber",
						targetVersionNumber),
				Map.of("newVersionNumber", next));
		return saved;
	}

	@Transactional(readOnly = true)
	public WorkflowVersion getVersion(Long versionId) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.versions.findByIdAndInstitutionId(versionId, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Workflow version", versionId));
	}

	/** Parse a version's document into the executable step model. */
	public WorkflowSteps stepsOf(WorkflowVersion version) {
		return WorkflowSteps.parse(this.jsonMapper, version.getDefinitionJson());
	}

	static String checksum(String json) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of()
				.formatHex(digest.digest((json == null ? "" : json).getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

}
