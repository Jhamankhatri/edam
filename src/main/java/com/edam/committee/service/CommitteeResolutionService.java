package com.edam.committee.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.committee.model.CommitteeLevel;
import com.edam.committee.model.CommitteeLevelMember;
import com.edam.committee.model.CommitteeMember;
import com.edam.committee.model.ExpenseCommittee;
import com.edam.committee.repository.CommitteeMemberRepository;
import com.edam.committee.repository.CommitteeRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a committee level into the people who would act on it (Module F, consumed by Module I).
 *
 * <h2>Why this lives in the committee module</h2>
 * The workflow engine knows an approver designation and nothing about committees. Committee membership has
 * its own rules — effective dates, deactivation, seats — and duplicating them into the assignment resolver
 * would mean two definitions of "who is on this level" that could disagree. The resolver asks this service;
 * the rules stay in one place.
 *
 * <h2>What "resolved" means here</h2>
 * A member is returned only when all of the following hold: the level exists and belongs to the named
 * committee, the level is active, the member is active, the member's effective period covers the instant
 * asked about, and the user account is active. Each exclusion is a way a task could otherwise be assigned to
 * someone who cannot or should not act.
 *
 * <h2>What is deliberately not decided here</h2>
 * Quorum. A level carries {@code minimumApprovals}, but whether a step needs all seated members, a quorum, or
 * only the mandatory ones is a property of how the <em>workflow step</em> was configured. This service
 * therefore returns the eligible people and the level's own minimum, and leaves the combination to the caller.
 */
@Service
public class CommitteeResolutionService {

	private static final Logger log = LoggerFactory.getLogger(CommitteeResolutionService.class);

	private final CommitteeRepository committees;

	private final CommitteeMemberRepository members;

	private final UserRepository users;

	public CommitteeResolutionService(CommitteeRepository committees, CommitteeMemberRepository members,
			UserRepository users) {
		this.committees = committees;
		this.members = members;
		this.users = users;
	}

	/**
	 * The active, currently-effective members seated on one committee level.
	 *
	 * <p>Returns empty rather than throwing when the level does not exist: the caller is resolving an
	 * assignment, and a designation pointing at a level that has been deleted is a configuration defect the
	 * workflow layer reports as "no approver could be resolved" with the step named. Throwing here would
	 * surface as a failure inside the committee module, which is the wrong place to look for it.
	 */
	@Transactional(readOnly = true)
	public List<UUID> resolveLevelMembers(UUID committeeLevelId, Instant at) {
		if (committeeLevelId == null) {
			return List.of();
		}
		Instant when = (at == null) ? Instant.now() : at;
		List<CommitteeLevel> matchingLevels = new ArrayList<>();
		for (ExpenseCommittee committee : this.committees.findAll()) {
			for (CommitteeLevel level : committee.getLevels()) {
				if (committeeLevelId.equals(level.getId()) && level.isActive() && committee.isActive()) {
					matchingLevels.add(level);
				}
			}
		}
		if (matchingLevels.isEmpty()) {
			log.warn("committee.level_unresolved levelId={} reason=not_found_or_inactive", committeeLevelId);
			return List.of();
		}
		CommitteeLevel level = matchingLevels.get(0);
		List<UUID> resolved = new ArrayList<>();
		for (CommitteeLevelMember seat : level.getSeats()) {
			CommitteeMember member = seat.getMember();
			if (!this.members.isActiveNow(member, when)) {
				continue;
			}
			// A deactivated account cannot sign in, so a task assigned to it would sit unactioned. This is the
			// same exclusion the assignment resolver applies to every other designation.
			Optional<User> user = this.users.findById(member.getUserId());
			if (user.isEmpty() || !user.get().isActive()) {
				log.debug("committee.member_excluded levelId={} memberId={} reason=inactive_account",
						committeeLevelId, member.getId());
				continue;
			}
			if (!resolved.contains(member.getUserId())) {
				resolved.add(member.getUserId());
			}
		}
		if (resolved.isEmpty()) {
			log.warn("committee.level_resolved_empty levelId={} reason=no_effective_active_member",
					committeeLevelId);
		}
		return resolved;
	}

	/**
	 * The level's own minimum approval count, or empty when the level does not exist.
	 *
	 * <p>Exposed separately so the workflow layer can raise a step's quorum to the committee's minimum
	 * without this service having to return a compound result. A committee that requires three approvals
	 * must not be satisfiable by a step configured with one.
	 */
	@Transactional(readOnly = true)
	public Optional<Short> minimumApprovalsOf(UUID committeeLevelId) {
		if (committeeLevelId == null) {
			return Optional.empty();
		}
		for (ExpenseCommittee committee : this.committees.findAll()) {
			for (CommitteeLevel level : committee.getLevels()) {
				if (committeeLevelId.equals(level.getId())) {
					return Optional.of(level.getMinimumApprovals());
				}
			}
		}
		return Optional.empty();
	}

}
