package com.edam.expense.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response contracts for the expense-request API (BRD FM-01..FM-06).
 *
 * <h2>What a client can and cannot set</h2>
 * A client chooses <em>what</em> is being requested: nature, classification, amount, currency,
 * department narrative. It never chooses the requester, the institution, the workflow version, the
 * state or the exchange rate — those are derived from the authenticated identity and the institution's
 * configuration. Accepting them would make the tenant and the approval route client-controlled.
 */
public final class RequestDtos {

	private RequestDtos() {
	}

	/** Full request representation, as the case-file view needs it. */
	public record ExpenseRequestResponse(Long id, String requestNumber, String title, String description,
			BigDecimal amount, String currencyCode, BigDecimal amountInBase, String expenseNature, Long categoryId,
			String categoryName, Long subcategoryId, String subcategoryName, String departmentCode,
			String branchCode, String requesterEmployeeId, String requesterUsername, String requesterDisplayName,
			String state, String currentStepCode, Long workflowDefinitionId, String workflowCode,
			Integer workflowVersionNumber, Instant submittedAt, Instant finalizedAt, String cancellationReason,
			Instant createdAt, Instant updatedAt, long version) {
	}

	public record CreateExpenseRequest(
			@NotBlank @Pattern(regexp = "OPEX|CAPEX", message = "must be OPEX or CAPEX") String expenseNature,
			@NotNull Long categoryId, Long subcategoryId,
			@NotBlank @Size(max = 200) String title, @Size(max = 4000) String description,
			@NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
			@NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code") String currencyCode,
			@Size(max = 64) String branchCode) {
	}

	/**
	 * Edit of a draft or returned request. No {@code title}: the title fields are locked (BRD §8.6),
	 * so a client that sends one is not silently ignored — the field does not exist.
	 */
	public record UpdateExpenseRequest(@NotNull Long categoryId, Long subcategoryId,
			@Size(max = 4000) String description,
			@NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
			@NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code") String currencyCode,
			@NotBlank @Pattern(regexp = "OPEX|CAPEX", message = "must be OPEX or CAPEX") String expenseNature,
			@Size(max = 64) String branchCode) {
	}

	public record SubmitRequest(@NotNull Long workflowDefinitionId) {
	}

	public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {
	}

	public record ReverseRequest(@NotBlank @Size(max = 1000) String reason) {
	}

	/** One case-file entry (BRD §8.4: step, approver, action, remarks, status, timestamp). */
	public record RequestHistoryResponse(int sequence, String action, String fromState, String toState,
			String actorEmployeeId, String actorUsername, String comment, Instant occurredAt,
			String correlationId) {
	}

	/** The case file: current state plus history and current assignments, in one read. */
	public record RequestCaseFileResponse(ExpenseRequestResponse request, List<RequestHistoryResponse> history,
			List<ApprovalView> approvals) {
	}

	/** One approval task as an approver sees it. */
	public record ApprovalView(Long taskId, String stepCode, String stepLabel, String approverRole, int sequence,
			String status, String assignedEmployeeId, String assignedDepartment, String actedByUsername,
			Instant actedAt, String comment, boolean actionableByCaller) {
	}

	/**
	 * Explain what the caller may do with this request right now.
	 *
	 * <p>Returned with the case file so a React client can render the action panel from the server's
	 * answer instead of re-implementing the authorization rules — and so the client cannot grant
	 * itself an action the server would refuse.
	 */
	public record AvailableActions(boolean canEdit, boolean canSubmit, boolean canCancel, boolean canApprove,
			boolean canReject, boolean canRevert, boolean canForward, boolean canReverse,
			List<String> permittedActions, String explanation) {
	}

}
