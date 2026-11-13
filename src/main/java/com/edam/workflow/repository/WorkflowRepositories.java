package com.edam.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.workflow.model.WorkflowAction;
import com.edam.workflow.model.WorkflowDefinition;
import com.edam.workflow.model.WorkflowInstance;
import com.edam.workflow.model.WorkflowState;
import com.edam.workflow.model.WorkflowStep;
import com.edam.workflow.model.WorkflowTask;
import com.edam.workflow.model.WorkflowVersion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositories for the workflow engine (Module I), grouped in one file.
 *
 * <h2>Why they are nested in one holder</h2>
 * They are small, they are only ever used by the engine, and keeping them together makes the engine's
 * persistence surface readable in one place. They are still six independent Spring Data interfaces; the
 * nesting changes nothing at runtime.
 */
public final class WorkflowRepositories {

	private WorkflowRepositories() {
	}

	/** Workflow definitions. */
	public interface Definitions extends JpaRepository<WorkflowDefinition, UUID> {

		Optional<WorkflowDefinition> findByCodeIgnoreCase(String code);

		boolean existsByCodeIgnoreCase(String code);

		List<WorkflowDefinition> findByActiveTrueOrderByNameAsc();

		Optional<WorkflowDefinition> findFirstByRequestTypeIdAndActiveTrue(UUID requestTypeId);

	}

	/** Workflow revisions. */
	public interface Versions extends JpaRepository<WorkflowVersion, UUID> {

		List<WorkflowVersion> findByWorkflowDefinitionIdOrderByVersionNumberDesc(UUID definitionId);

		/** The single published revision, or empty. The partial unique index guarantees at most one. */
		@Query("""
				select v from WorkflowVersion v
				where v.workflowDefinitionId = :definitionId
				  and v.status = com.edam.workflow.model.WorkflowVersion$Status.PUBLISHED
				""")
		Optional<WorkflowVersion> findPublished(@Param("definitionId") UUID definitionId);

		@Query("select coalesce(max(v.versionNumber), 0) from WorkflowVersion v where v.workflowDefinitionId = :definitionId")
		int maxVersionNumber(@Param("definitionId") UUID definitionId);

	}

	/** States of a version. */
	public interface States extends JpaRepository<WorkflowState, UUID> {

		List<WorkflowState> findByWorkflowVersionId(UUID versionId);

		@Query("""
				select s from WorkflowState s
				where s.workflowVersion.id = :versionId and s.initial = true
				""")
		Optional<WorkflowState> findInitial(@Param("versionId") UUID versionId);

		@Query("""
				select s from WorkflowState s
				where s.workflowVersion.id = :versionId and s.stateType = :stateType
				""")
		Optional<WorkflowState> findByType(@Param("versionId") UUID versionId,
				@Param("stateType") WorkflowState.StateType stateType);

	}

	/** Steps of a version, with approvers. */
	public interface Steps extends JpaRepository<WorkflowStep, UUID> {

		@Query("""
				select distinct st from WorkflowStep st
				left join fetch st.approvers
				where st.workflowVersion.id = :versionId
				order by st.stepOrder
				""")
		List<WorkflowStep> findWithApprovers(@Param("versionId") UUID versionId);

		@Query("""
				select st from WorkflowStep st
				where st.workflowVersion.id = :versionId and st.stepOrder > :afterOrder
				order by st.stepOrder
				""")
		List<WorkflowStep> findAfterOrder(@Param("versionId") UUID versionId, @Param("afterOrder") short afterOrder);

	}

	/** Instances. */
	public interface Instances extends JpaRepository<WorkflowInstance, UUID> {

		Optional<WorkflowInstance> findByExpenseRequestId(UUID expenseRequestId);

		boolean existsByExpenseRequestId(UUID expenseRequestId);

		List<WorkflowInstance> findByWorkflowVersionId(UUID versionId);

		long countByWorkflowVersionIdAndCompletedAtIsNull(UUID versionId);

	}

	/** Tasks. */
	public interface Tasks extends JpaRepository<WorkflowTask, UUID> {

		List<WorkflowTask> findByWorkflowInstanceIdAndStatus(UUID instanceId, WorkflowTask.TaskStatus status);

		List<WorkflowTask> findByWorkflowInstanceIdOrderByAssignedAtAsc(UUID instanceId);

		@Query("""
				select t from WorkflowTask t
				where t.status = com.edam.workflow.model.WorkflowTask$TaskStatus.PENDING
				  and (t.assignedUserId = :userId
				       or (t.assignedRoleId is not null and t.assignedRoleId in :roleIds))
				order by t.assignedAt desc
				""")
		Page<WorkflowTask> findInbox(@Param("userId") UUID userId, @Param("roleIds") List<UUID> roleIds,
				Pageable pageable);

		long countByAssignedUserIdAndStatus(UUID userId, WorkflowTask.TaskStatus status);

		@Query("""
				select count(t) from WorkflowTask t
				where t.workflowInstanceId = :instanceId
				  and t.workflowStepId = :stepId
				  and t.status = com.edam.workflow.model.WorkflowTask$TaskStatus.APPROVED
				""")
		long countApproved(@Param("instanceId") UUID instanceId, @Param("stepId") UUID stepId);

		@Query("""
				select t from WorkflowTask t
				where t.workflowInstanceId = :instanceId
				  and t.workflowStepId = :stepId
				  and t.status = com.edam.workflow.model.WorkflowTask$TaskStatus.PENDING
				""")
		List<WorkflowTask> findOpenForStep(@Param("instanceId") UUID instanceId, @Param("stepId") UUID stepId);

		@Query("""
				select t from WorkflowTask t
				where t.id = :taskId
				  and t.workflowInstanceId = :instanceId
				  and t.status = com.edam.workflow.model.WorkflowTask$TaskStatus.PENDING
				""")
		Optional<WorkflowTask> findOpenTask(@Param("taskId") UUID taskId, @Param("instanceId") UUID instanceId);

		@Query("""
				select t from WorkflowTask t
				where t.workflowInstanceId = :instanceId
				  and t.status = com.edam.workflow.model.WorkflowTask$TaskStatus.PENDING
				  and (t.assignedUserId = :userId or (t.assignedRoleId is not null and t.assignedRoleId in :roleIds))
				""")
		List<WorkflowTask> findOpenForCaller(@Param("instanceId") UUID instanceId, @Param("userId") UUID userId,
				@Param("roleIds") List<UUID> roleIds);

	}

	/** Actions. */
	public interface Actions extends JpaRepository<WorkflowAction, UUID> {

		List<WorkflowAction> findByWorkflowTaskIdOrderByActionAtAsc(UUID taskId);

		@Query("""
				select a from WorkflowAction a
				join WorkflowTask t on t.id = a.workflowTaskId
				where t.workflowInstanceId = :instanceId
				order by a.actionAt asc
				""")
		List<WorkflowAction> findForInstance(@Param("instanceId") UUID instanceId);

	}

}
