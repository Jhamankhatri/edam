package com.edam.committee.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.edam.committee.dto.CommitteeDtos.AddMemberRequest;
import com.edam.committee.dto.CommitteeDtos.CommitteeCoverageResponse;
import com.edam.committee.dto.CommitteeDtos.CommitteeLevelMemberRequest;
import com.edam.committee.dto.CommitteeDtos.CommitteeLevelRequest;
import com.edam.committee.dto.CommitteeDtos.CommitteeLevelResponse;
import com.edam.committee.dto.CommitteeDtos.CommitteeMemberResponse;
import com.edam.committee.dto.CommitteeDtos.CommitteeResponse;
import com.edam.committee.dto.CommitteeDtos.LevelMemberResponse;
import com.edam.committee.dto.CommitteeDtos.SaveCommitteeRequest;
import com.edam.committee.model.CommitteeLevel;
import com.edam.committee.model.CommitteeLevelMember;
import com.edam.committee.model.CommitteeMember;
import com.edam.committee.model.ExpenseCommittee;
import com.edam.committee.repository.CommitteeMemberRepository;
import com.edam.committee.repository.CommitteeRepository;
import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expense committee configuration (Module F).
 *
 * <h2>What is validated here, and why not in the database</h2>
 * The database already enforces, structurally: no two active bands of the same committee and currency
 * overlapping; at most one active CEO, CFO and head of administration per committee; at least one required
 * approval per band. It cannot express the rules that need to look at several rows at once:
 * <ul>
 * <li>a band's quorum must be reachable by its seats, otherwise the band can never approve anything;</li>
 * <li>every mandatory seat must be an active member of the committee;</li>
 * <li>a member must belong to the committee before being seated on one of its bands;</li>
 * <li>membership periods must not overlap for the same person, so "who was on the committee on the day"
 * has exactly one answer.</li>
 * </ul>
 * Those live here, in one place, with the reason stated once.
 *
 * <h2>Deactivation instead of deletion</h2>
 * A committee that approved a payment must still be readable years later, so nothing here deletes. Levels and
 * members are deactivated or ended, and the history survives.
 */
@Service
public class CommitteeService {

	private final CommitteeRepository committees;

	private final CommitteeMemberRepository members;

	private final CurrencyRepository currencies;

	private final UserRepository users;

	public CommitteeService(CommitteeRepository committees, CommitteeMemberRepository members,
			CurrencyRepository currencies, UserRepository users) {
		this.committees = committees;
		this.members = members;
		this.currencies = currencies;
		this.users = users;
	}

	// ---------------------------------------------------------------------------------------------
	// Committees
	// ---------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<CommitteeResponse> list(boolean includeInactive) {
		List<ExpenseCommittee> found = includeInactive ? this.committees.findAllByOrderByCommitteeNameAsc()
				: this.committees.findByActiveTrueOrderByCommitteeNameAsc();
		Map<UUID, String> currencyCodes = currencyCodes();
		Map<UUID, String> names = new HashMap<>();
		return found.stream().map(committee -> toResponse(committee, currencyCodes, names)).toList();
	}

	@Transactional(readOnly = true)
	public CommitteeResponse get(UUID id) {
		return toResponse(require(id), currencyCodes(), new HashMap<>());
	}

	@Transactional
	public CommitteeResponse create(SaveCommitteeRequest request) {
		if (this.committees.existsByCommitteeCode(request.committeeCode())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A committee with code '" + request.committeeCode() + "' already exists.");
		}
		ExpenseCommittee committee = new ExpenseCommittee(request.committeeCode(), request.committeeName(),
				request.description());
		return this.toResponse(this.committees.save(committee), currencyCodes(), new HashMap<>());
	}

	@Transactional
	public CommitteeResponse rename(UUID id, SaveCommitteeRequest request) {
		ExpenseCommittee committee = require(id);
		// The code is the stable identifier used by workflow definitions and by the matrix; renaming it would
		// silently break a published configuration, so only the name and description are mutable.
		committee.rename(request.committeeName(), request.description());
		return this.toResponse(this.committees.save(committee), currencyCodes(), new HashMap<>());
	}

	@Transactional
	public void deactivate(UUID id) {
		ExpenseCommittee committee = require(id);
		committee.deactivate();
		this.committees.save(committee);
	}

	// ---------------------------------------------------------------------------------------------
	// Members
	// ---------------------------------------------------------------------------------------------

	@Transactional
	public CommitteeMemberResponse addMember(UUID committeeId, AddMemberRequest request) {
		ExpenseCommittee committee = require(committeeId);
		User user = this.users.findById(request.userId())
			.orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"User '" + request.userId() + "' is inactive and cannot be seated on a committee.");
		}
		Instant from = request.effectiveFrom() != null ? request.effectiveFrom() : Instant.now();
		if (this.members.findByCommitteeIdAndUserIdAndActiveTrue(committeeId, request.userId()).isPresent()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"That user is already an active member of this committee.");
		}
		// The single-office rules are also database indexes; checking first turns a constraint violation into
		// an actionable message naming the office that is already filled.
		CommitteeMember member = new CommitteeMember(committee, user.getId(), request.memberType(), from);
		CommitteeMember saved = this.members.save(member);
		return toMemberResponse(saved, user.displayName());
	}

	@Transactional
	public void endMembership(UUID committeeId, UUID memberId) {
		require(committeeId);
		CommitteeMember member = this.members.findById(memberId)
			.filter(candidate -> candidate.getCommittee().getId().equals(committeeId))
			.orElseThrow(() -> new ResourceNotFoundException("Committee member", memberId));
		// Ending a membership that a band's mandatory quorum depends on would make that band unreachable.
		// Refusing is better than accepting a change that silently disables an approval path.
		List<CommitteeLevel> seatedLevels = member.getCommittee()
			.getLevels()
			.stream()
			.filter(CommitteeLevel::isActive)
			.toList();
		if (!seatedLevels.isEmpty()) {
			boolean seatedMandatory = false;
			for (CommitteeLevel level : seatedLevels) {
				if (isSeated(level, member)) {
					seatedMandatory = true;
					break;
				}
			}
			if (seatedMandatory) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Member is seated on an active level; unseat them from every level before ending membership.");
			}
		}
		member.end(Instant.now());
		this.members.save(member);
	}

	// ---------------------------------------------------------------------------------------------
	// Levels
	// ---------------------------------------------------------------------------------------------

	@Transactional
	public CommitteeLevelResponse addLevel(UUID committeeId, CommitteeLevelRequest request) {
		ExpenseCommittee committee = require(committeeId);
		Currency currency = this.currencies.findById(request.currencyId())
			.orElseThrow(() -> new ResourceNotFoundException("Currency", request.currencyId()));
		validateBand(request, committee, currency);

		CommitteeLevel level = new CommitteeLevel(committee, request.levelNumber(), request.levelName(),
				request.minimumAmount(), request.maximumAmount(), currency.getId(), request.minimumApprovals());
		committee.getLevels().add(level);
		seatMembers(level, request.members(), committee);
		this.committees.save(committee);
		return toLevelResponse(level, currency.getCode(), new HashMap<>());
	}

	@Transactional
	public void deactivateLevel(UUID committeeId, UUID levelId) {
		ExpenseCommittee committee = require(committeeId);
		CommitteeLevel level = committee.getLevels()
			.stream()
			.filter(candidate -> candidate.getId().equals(levelId))
			.findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Committee level", levelId));
		level.deactivate();
		this.committees.save(committee);
	}

	/**
	 * Reports which active band of each active committee would decide an amount, and whether that band can
	 * actually reach its quorum.
	 *
	 * <p>Bands are half-open, so an amount equal to a band's maximum belongs to the next band up. That is the
	 * conservative reading: an amount on a boundary is decided by the higher authority, never the lower one.
	 */
	@Transactional(readOnly = true)
	public List<CommitteeCoverageResponse> coverage(UUID currencyId, BigDecimal amount) {
		Currency currency = this.currencies.findById(currencyId)
			.orElseThrow(() -> new ResourceNotFoundException("Currency", currencyId));
		List<CommitteeCoverageResponse> matches = new ArrayList<>();
		for (ExpenseCommittee committee : this.committees.findByActiveTrueOrderByCommitteeNameAsc()) {
			for (CommitteeLevel level : committee.getLevels()) {
				if (!level.isActive() || !level.getCurrencyId().equals(currencyId) || !level.covers(amount)) {
					continue;
				}
				matches.add(coverageOf(committee, level, amount, currency.getCode()));
			}
		}
		return matches;
	}

	private CommitteeCoverageResponse coverageOf(ExpenseCommittee committee, CommitteeLevel level, BigDecimal amount,
			String currencyCode) {
		List<CommitteeLevelMember> seats = seatsOf(level);
		int mandatory = (int) seats.stream().filter(CommitteeLevelMember::isMandatory).count();
		int seated = seats.size();
		boolean satisfiable = seated >= level.getMinimumApprovals() && (seated > 0);
		String explanation;
		if (seated == 0) {
			explanation = "No member is seated on this level, so it cannot approve anything.";
		}
		else if (seated < level.getMinimumApprovals()) {
			explanation = "This level requires " + level.getMinimumApprovals() + " approvals but only " + seated
					+ " member(s) are seated, so it can never reach its quorum.";
		}
		else if (mandatory > seated) {
			explanation = "This level has more mandatory members than seats, which cannot happen once configured.";
		}
		else {
			explanation = "This level can reach its quorum of " + level.getMinimumApprovals() + " with " + seated
					+ " seated member(s)" + (mandatory > 0 ? ", " + mandatory + " of them mandatory." : ".");
		}
		return new CommitteeCoverageResponse(committee.getId(), committee.getCommitteeCode(),
				committee.getCommitteeName(), level.getId(), level.getLevelNumber(), level.getLevelName(), amount,
				currencyCode, level.getMinimumApprovals(), mandatory, seated, satisfiable, explanation);
	}

	// ---------------------------------------------------------------------------------------------
	// Validation
	// ---------------------------------------------------------------------------------------------

	private void validateBand(CommitteeLevelRequest request, ExpenseCommittee committee, Currency currency) {
		BigDecimal minimum = request.minimumAmount();
		BigDecimal maximum = request.maximumAmount();
		if (maximum != null && maximum.compareTo(minimum) <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The maximum amount must be greater than the minimum amount.");
		}
		boolean duplicateNumber = committee.getLevels()
			.stream()
			.filter(CommitteeLevel::isActive)
			.anyMatch(level -> level.getLevelNumber() == request.levelNumber());
		if (duplicateNumber) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Level number " + request.levelNumber() + " is already used by an active level.");
		}
		for (CommitteeLevel existing : committee.getLevels()) {
			if (!existing.isActive() || !existing.getCurrencyId().equals(currency.getId())) {
				continue;
			}
			// Half-open overlap test, matching the database's exclusion constraint so the user sees a readable
			// message instead of a constraint violation.
			boolean overlaps = minimum.compareTo(existing.getMaximumAmount() == null ? BigDecimal.valueOf(Long.MAX_VALUE)
					: existing.getMaximumAmount()) < 0
					&& (maximum == null || existing.getMinimumAmount().compareTo(maximum) < 0);
			if (overlaps) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This amount band overlaps level "
						+ existing.getLevelNumber() + " of the same committee and currency.");
			}
		}
	}

	private void seatMembers(CommitteeLevel level, List<CommitteeLevelMemberRequest> requested,
			ExpenseCommittee committee) {
		Set<UUID> memberIds = new HashSet<>();
		Map<UUID, CommitteeMember> activeMembers = new HashMap<>();
		for (CommitteeMember member : committee.getMembers()) {
			if (member.isActive()) {
				activeMembers.put(member.getId(), member);
			}
		}
		int mandatory = 0;
		for (CommitteeLevelMemberRequest seat : requested) {
			if (!memberIds.add(seat.memberId())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Member '" + seat.memberId() + "' is listed more than once on this level.");
			}
			CommitteeMember member = activeMembers.get(seat.memberId());
			if (member == null) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Member '" + seat.memberId()
						+ "' is not an active member of this committee, so cannot be seated on one of its levels.");
			}
			if (seat.mandatory()) {
				mandatory++;
			}
			level.getSeats().add(new CommitteeLevelMember(level, member, seat.mandatory(), seat.approvalOrder()));
		}
		// A quorum that no combination of seats can reach is a configuration that can never approve. Catching
		// it here means an administrator learns immediately rather than an applicant discovering it days later.
		if (level.getSeats().size() < level.getMinimumApprovals()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This level requires "
					+ level.getMinimumApprovals() + " approvals but only " + level.getSeats().size()
					+ " member(s) are seated, so it could never approve anything.");
		}
		if (mandatory > level.getSeats().size()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "More mandatory members than seats were supplied.");
		}
	}

	private boolean isSeated(CommitteeLevel level, CommitteeMember member) {
		return seatsOf(level).stream().anyMatch(seat -> seat.getMember().getId().equals(member.getId()));
	}

	private List<CommitteeLevelMember> seatsOf(CommitteeLevel level) {
		return level.getSeats();
	}

	// ---------------------------------------------------------------------------------------------
	// Mapping
	// ---------------------------------------------------------------------------------------------

	private CommitteeResponse toResponse(ExpenseCommittee committee, Map<UUID, String> currencyCodes,
			Map<UUID, String> names) {
		List<CommitteeLevelResponse> levels = committee.getLevels()
			.stream()
			.map(level -> toLevelResponse(level,
					currencyCodes.getOrDefault(level.getCurrencyId(), level.getCurrencyId().toString()), names))
			.toList();
		List<CommitteeMemberResponse> memberResponses = committee.getMembers()
			.stream()
			.map(member -> toMemberResponse(member, names.computeIfAbsent(member.getUserId(), this::displayName)))
			.toList();
		return new CommitteeResponse(committee.getId(), committee.getCommitteeCode(), committee.getCommitteeName(),
				committee.getDescription(), committee.isActive(), levels, memberResponses, committee.getCreatedAt(),
				committee.getUpdatedAt());
	}

	private CommitteeLevelResponse toLevelResponse(CommitteeLevel level, String currencyCode,
			Map<UUID, String> names) {
		List<LevelMemberResponse> seats = level.getSeats()
			.stream()
			.map(seat -> new LevelMemberResponse(seat.getMember().getId(), seat.getMember().getUserId(),
					names.computeIfAbsent(seat.getMember().getUserId(), this::displayName),
					seat.getMember().getMemberType().name(), seat.isMandatory(), seat.getApprovalOrder()))
			.toList();
		return new CommitteeLevelResponse(level.getId(), level.getLevelNumber(), level.getLevelName(),
				level.getMinimumAmount(), level.getMaximumAmount(), level.getCurrencyId(), currencyCode,
				level.getMinimumApprovals(), level.isActive(), seats);
	}

	private CommitteeMemberResponse toMemberResponse(CommitteeMember member, String displayName) {
		return new CommitteeMemberResponse(member.getId(), member.getUserId(), displayName,
				member.getMemberType().name(), member.getEffectiveFrom(), member.getEffectiveTo(), member.isActive());
	}

	private Map<UUID, String> currencyCodes() {
		Map<UUID, String> codes = new HashMap<>();
		for (Currency currency : this.currencies.findAll()) {
			codes.put(currency.getId(), currency.getCode());
		}
		return codes;
	}

	private String displayName(UUID userId) {
		return this.users.findById(userId).map(User::displayName).orElse(null);
	}

	private ExpenseCommittee require(UUID id) {
		return this.committees.findById(id).orElseThrow(() -> new ResourceNotFoundException("Committee", id));
	}

	/** Exposed for the request module, which needs the band that decides a submitted amount. */
	@Transactional(readOnly = true)
	public Optional<CommitteeLevel> levelFor(UUID committeeId, UUID currencyId, BigDecimal amount) {
		return this.committees.findById(committeeId)
			.map(committee -> committee.getLevels()
				.stream()
				.filter(level -> level.isActive() && level.getCurrencyId().equals(currencyId) && level.covers(amount))
				.findFirst()
				.orElse(null));
	}

}
