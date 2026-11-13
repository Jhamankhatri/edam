package com.edam.committee.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.committee.model.CommitteeMember;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Committee API contract (Module F).
 *
 * <p>Amounts are {@link BigDecimal} with an explicit precision and scale, because a committee band decides who
 * must approve a payment. A {@code double} would silently change a boundary: {@code 100000.00} compared against
 * a band ending at {@code 100000.00} is not reliably equal in binary floating point, and a request landing on
 * the wrong side of a band is a governance failure, not a rounding curiosity.
 */
public final class CommitteeDtos {

	private CommitteeDtos() {
	}

	/** Create or rename a committee. */
	public record SaveCommitteeRequest(
			@NotBlank @Size(max = 64) String committeeCode,
			@NotBlank @Size(max = 200) String committeeName,
			@Size(max = 2000) String description) {
	}

	/** One amount band. */
	public record CommitteeLevelRequest(
			@NotNull @Min(1) @Max(32767) Short levelNumber,
			@NotBlank @Size(max = 200) String levelName,
			@NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal minimumAmount,
			@DecimalMin(value = "0.0000", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal maximumAmount,
			@NotNull UUID currencyId,
			@NotNull @Min(1) @Max(32767) Short minimumApprovals,
			@NotEmpty @Valid List<CommitteeLevelMemberRequest> members) {
	}

	/** Seats a member on a band. */
	public record CommitteeLevelMemberRequest(
			@NotNull UUID memberId,
			boolean mandatory,
			@Min(1) @Max(32767) Short approvalOrder) {
	}

	/** Adds a member to a committee. */
	public record AddMemberRequest(
			@NotNull UUID userId,
			@NotNull CommitteeMember.MemberType memberType,
			Instant effectiveFrom) {
	}

	/** A member of a committee. */
	public record CommitteeMemberResponse(UUID id, UUID userId, String displayName, String memberType,
			Instant effectiveFrom, Instant effectiveTo, boolean active) {
	}

	/** A seating on a band. */
	public record LevelMemberResponse(UUID memberId, UUID userId, String displayName, String memberType,
			boolean mandatory, Short approvalOrder) {
	}

	/** An amount band with its seats. */
	public record CommitteeLevelResponse(UUID id, short levelNumber, String levelName, BigDecimal minimumAmount,
			BigDecimal maximumAmount, UUID currencyId, String currencyCode, short minimumApprovals, boolean active,
			List<LevelMemberResponse> members) {
	}

	/** A committee with its bands and members. */
	public record CommitteeResponse(UUID id, String committeeCode, String committeeName, String description,
			boolean active, List<CommitteeLevelResponse> levels, List<CommitteeMemberResponse> members,
			Instant createdAt, Instant updatedAt) {
	}

	/**
	 * Which committee and band would decide an amount, and whether that band is currently able to do so.
	 *
	 * <p>{@code satisfiable} is the point of this response. A band whose quorum exceeds its seated members can
	 * never approve anything; discovering that when a payment is submitted is far too late.
	 */
	public record CommitteeCoverageResponse(UUID committeeId, String committeeCode, String committeeName,
			UUID levelId, short levelNumber, String levelName, BigDecimal amount, String currencyCode,
			short minimumApprovals, int mandatoryMembers, int seatedMembers, boolean satisfiable, String explanation) {
	}

}
