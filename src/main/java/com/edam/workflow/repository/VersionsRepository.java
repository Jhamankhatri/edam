package com.edam.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.workflow.model.WorkflowVersion;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Workflow revisions: the publishable unit of configuration.
 *
 * <p>A version is a drafts-then-publish container. Exactly one version of a definition is PUBLISHED at a
 * time (a partial unique index enforces it), and only a PUBLISHED version may be started. Because the
 * instance stores the version it started under, publishing a new revision cannot change an in-flight request.
 */
public interface VersionsRepository extends JpaRepository<WorkflowVersion, UUID> {

	List<WorkflowVersion> findByWorkflowDefinitionIdOrderByVersionNumberDesc(UUID definitionId);

	/** The single published revision, or empty. The partial unique index guarantees at most one. */
	default Optional<WorkflowVersion> findPublished(UUID definitionId) {
		return findByWorkflowDefinitionIdOrderByVersionNumberDesc(definitionId).stream()
			.filter(version -> version.getStatus() == WorkflowVersion.Status.PUBLISHED)
			.findFirst();
	}

	/** True when the definition already has a revision carrying this number. */
	boolean existsByWorkflowDefinitionIdAndVersionNumber(UUID definitionId, int versionNumber);

	/** True when two revisions of the same definition share a number, checked before saving. */
	default boolean numberTaken(UUID definitionId, Integer excludeVersionId, int versionNumber) {
		return findByWorkflowDefinitionIdOrderByVersionNumberDesc(definitionId).stream()
			.filter(version -> excludeVersionId == null || !excludeVersionId.equals(version.getId()))
			.anyMatch(version -> version.getVersionNumber() == versionNumber);
	}

	/** Revisions of a definition that may still accept a new instance. */
	default Optional<WorkflowVersion> findStartable(UUID definitionId) {
		return findPublished(definitionId);
	}

	/** The highest version number used so far, or zero when a definition has no revisions yet. */
	default int maxVersionNumber(UUID definitionId) {
		return findByWorkflowDefinitionIdOrderByVersionNumberDesc(definitionId).stream()
			.mapToInt(WorkflowVersion::getVersionNumber)
			.max()
			.orElse(0);
	}

}
