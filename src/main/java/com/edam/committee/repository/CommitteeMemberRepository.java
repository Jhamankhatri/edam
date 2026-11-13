package com.edam.committee.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.committee.model.CommitteeMember;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/** Members of committees, including the per-committee office holders. */
public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, UUID> {

	List<CommitteeMember> findByCommitteeIdAndActiveTrueOrderByEffectiveFromAsc(UUID committeeId);

	Optional<CommitteeMember> findByCommitteeIdAndUserIdAndActiveTrue(UUID committeeId, UUID userId);

	/** Active members of a committee who are not yet effective, or already ended, are excluded. */
	default boolean isActiveNow(CommitteeMember member, Instant now) {
		return member.isActive() && !member.getEffectiveFrom().isAfter(now)
				&& (member.getEffectiveTo() == null || member.getEffectiveTo().isAfter(now));
	}

}
