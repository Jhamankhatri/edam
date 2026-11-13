package com.edam.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edam.workflow.model.WorkflowState;
import com.edam.workflow.model.WorkflowStepApprover;
import com.edam.workflow.model.WorkflowTask;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Invariants of the workflow value objects.
 *
 * <p>These are deliberately not {@code @SpringBootTest}s. Each rule below is a property of the object, and a
 * database-backed test would only re-assert it more slowly while making the failure harder to read. The
 * persistence behaviour is covered separately against real PostgreSQL.
 */
class WorkflowStateModelTest {

	@Nested
	@DisplayName("states")
	class States {

		@Test
		@DisplayName("only INITIAL is initial, and it is never terminal")
		void initialStateIsNotTerminal() {
			WorkflowState state = new WorkflowState(null, "DRAFT", "Draft", WorkflowState.StateType.INITIAL);
			assertThat(state.isInitial()).isTrue();
			assertThat(state.isTerminal()).isFalse();
		}

		@Test
		@DisplayName("IN_PROGRESS is neither initial nor terminal")
		void inProgressIsTransient() {
			WorkflowState state = new WorkflowState(null, "IN_APPROVAL", "In approval",
					WorkflowState.StateType.IN_PROGRESS);
			assertThat(state.isInitial()).isFalse();
			assertThat(state.isTerminal()).isFalse();
		}

		@Test
		@DisplayName("every outcome state is terminal")
		void outcomeStatesAreTerminal() {
			for (WorkflowState.StateType type : new WorkflowState.StateType[] { WorkflowState.StateType.APPROVED,
					WorkflowState.StateType.REJECTED, WorkflowState.StateType.CANCELLED,
					WorkflowState.StateType.REVERSED }) {
				WorkflowState state = new WorkflowState(null, type.name(), type.name(), type);
				assertThat(state.isTerminal()).as("%s must end the workflow", type).isTrue();
				assertThat(state.isInitial()).isFalse();
			}
		}
	}

	@Nested
	@DisplayName("tasks")
	class Tasks {

		private final UUID instance = UUID.randomUUID();

		private final UUID step = UUID.randomUUID();

		@Test
		@DisplayName("a task must be addressed to a user or a role")
		void requiresAnAssignee() {
			assertThatThrownBy(() -> new WorkflowTask(this.instance, this.step, null, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("user or a role");
		}

		@Test
		@DisplayName("a task cannot be addressed to both a user and a role")
		void rejectsBothAssignees() {
			assertThatThrownBy(
					() -> new WorkflowTask(this.instance, this.step, UUID.randomUUID(), UUID.randomUUID(), null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("OR a role");
		}

		@Test
		@DisplayName("completing a task twice is refused, because a double approval is a defect")
		void refusesDoubleAction() {
			WorkflowTask task = new WorkflowTask(this.instance, this.step, UUID.randomUUID(), null, null);
			task.complete(WorkflowTask.TaskStatus.APPROVED);
			assertThatThrownBy(() -> task.complete(WorkflowTask.TaskStatus.APPROVED))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("already APPROVED");
		}

		@Test
		@DisplayName("cancelling is idempotent and does not overwrite a completed outcome")
		void cancelDoesNotOverwriteOutcome() {
			WorkflowTask approved = new WorkflowTask(this.instance, this.step, UUID.randomUUID(), null, null);
			approved.complete(WorkflowTask.TaskStatus.APPROVED);
			approved.cancel();
			assertThat(approved.getStatus()).isEqualTo(WorkflowTask.TaskStatus.APPROVED);
			assertThat(approved.getCompletedAt()).isNotNull();
		}

		@Test
		@DisplayName("an open task reports open; a completed one does not")
		void opennessTracksStatus() {
			WorkflowTask task = new WorkflowTask(this.instance, this.step, UUID.randomUUID(), null, null);
			assertThat(task.isOpen()).isTrue();
			task.complete(WorkflowTask.TaskStatus.REJECTED);
			assertThat(task.isOpen()).isFalse();
		}
	}

	@Nested
	@DisplayName("approver designations")
	class Approvers {

		@Test
		@DisplayName("a ROLE designation requires a role and rejects a user")
		void roleNeedsRole() {
			assertThatThrownBy(() -> new WorkflowStepApprover(null, WorkflowStepApprover.ApproverType.ROLE, null, null,
					null, null))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new WorkflowStepApprover(null, WorkflowStepApprover.ApproverType.ROLE,
					UUID.randomUUID(), UUID.randomUUID(), null, null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("an office designation must not carry any reference, so a stale one cannot linger")
		void officeRejectsReferences() {
			assertThatThrownBy(() -> new WorkflowStepApprover(null, WorkflowStepApprover.ApproverType.CEO,
					UUID.randomUUID(), null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new WorkflowStepApprover(null, WorkflowStepApprover.ApproverType.DEPARTMENT_HEAD,
					null, UUID.randomUUID(), null, null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a committee designation requires a committee level")
		void committeeNeedsLevel() {
			assertThatThrownBy(() -> new WorkflowStepApprover(null,
					WorkflowStepApprover.ApproverType.EXPENSE_COMMITTEE, null, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("valid designations are accepted")
		void acceptsValidDesignations() {
			assertThat(new WorkflowStepApprover(null, WorkflowStepApprover.ApproverType.DEPARTMENT_HEAD, null, null, null,
					null)).isNotNull();
			assertThat(new WorkflowStepApprover(null, WorkflowStepApprover.ApproverType.ROLE, UUID.randomUUID(), null,
					null, null)).isNotNull();
			assertThat(new WorkflowStepApprover(null, WorkflowStepApprover.ApproverType.USER, null, UUID.randomUUID(),
					null, null)).isNotNull();
		}
	}

}
