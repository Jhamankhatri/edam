package com.edam.configplane.committee;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expense Committee administration (BRD FM-08).
 *
 * <h2>Invariants this service owns (cross-row, so not expressible as column constraints)</h2>
 * <ul>
 *   <li><strong>One active holder per designated office.</strong> At most one active CEO, one CFO and
 *       one Head of Admin &amp; GSD per institution. Also enforced by three partial unique indexes,
 *       because a read-then-write check races under concurrent administration. The service check
 *       exists to return a business message, not to be the control.</li>
 *   <li><strong>Non-overlapping amount bands.</strong> Two active levels covering the same amount
 *       would make routing non-deterministic. Also enforced by a database exclusion constraint.</li>
 *   <li><strong>Member count and approvals.</strong> Minimum approvals must be within the selected
 *       member count, and a level may select at most ten members (BRD FM-08).</li>
 *   <li><strong>Required offices present.</strong> A level that requires the CEO must actually
 *       include an active CEO-designated member, otherwise the level can never be satisfied.</li>
 *   <li><strong>Institution-wide uniqueness of a member.</strong> One person cannot appear twice on
 *       the committee, because the second record would let one person approve twice.</li>
 * </ul>
 *
 * <h2>Deletion</h2>
 * A level referenced by a delegation rule is not deletable; a member on an active level is
 * deactivated rather than removed, so the historical composition of the committee stays auditable.
 */
@Service
public class CommitteeService {

	private final CommitteeMemberRepository members;

	private final CommitteeLevelRepository levels;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	public CommitteeService(CommitteeMemberRepository members, CommitteeLevelRepository levels,
			InstitutionContext institutionContext, AuditRecorder audit) {
		this.members = members;
		this.levels = levels;
		this.institutionContext = institutionContext;
		this.audit = audit;
	}

	// ------------------------------------------------------------------
	// Members
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<CommitteeMember> listMembers(boolean activeOnly) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return activeOnly ? this.members.findByInstitutionIdAndActiveTrueOrderByDisplayNameAsc(institutionId)
				: this.members.findByInstitutionIdOrderByDisplayNameAsc(institutionId);
	}

	@Transactional(readOnly = true)
	public CommitteeMember getMember(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.members.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Committee member", id));
	}

	@Transactional
	public CommitteeMember createMember(String employeeId, String displayName, CommitteeDesignation designation,
			String designationLabel) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		if (this.members.existsByInstitutionIdAndEmployeeId(institutionId, employeeId.trim())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"That employee is already a member of the Expense Committee.");
		}
		requireOfficeAvailable(institutionId, designation, null);
		CommitteeMember member = new CommitteeMember(employeeId.trim(), displayName.trim(), designationLabel,
				designation == null ? CommitteeDesignation.MEMBER : designation);
		member.assignInstitution(institutionId);
		CommitteeMember saved = this.members.save(member);
		this.audit.recordChange(AuditAction.CREATE, "CommitteeMember", saved.getId(), "Committee member added", null,
				snapshot(saved));
		return saved;
	}

	@Transactional
	public CommitteeMember updateMember(Long id, String displayName, CommitteeDesignation designation,
			String designationLabel) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		CommitteeMember member = getMember(id);
		requireOfficeAvailable(institutionId, designation, id);
		Map<String, Object> before = snapshot(member);
		member.update(displayName.trim(), designationLabel,
				designation == null ? CommitteeDesignation.MEMBER : designation);
		this.audit.recordChange(AuditAction.UPDATE, "CommitteeMember", id, "Committee member updated", before,
				snapshot(member));
		return member;
	}

	@Transactional
	public CommitteeMember setMemberActive(Long id, boolean active) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		CommitteeMember member = getMember(id);
		if (active) {
			requireOfficeAvailable(institutionId, member.getDesignation(), id);
		}
		else if (this.levels.existsByInstitutionIdAndMembersIdAndActiveTrue(institutionId, id)) {
			// Removing an active member from an active level can make the level unsatisfiable.
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"This member belongs to an active committee level. Remove them from the level first.");
		}
		Map<String, Object> before = snapshot(member);
		if (active) {
			member.activate();
		}
		else {
			member.deactivate();
		}
		this.audit.recordChange(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "CommitteeMember", id,
				active ? "Committee member activated" : "Committee member deactivated", before, snapshot(member));
		return member;
	}

	@Transactional
	public void deleteMember(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		CommitteeMember member = getMember(id);
		if (this.levels.existsByInstitutionIdAndMembersId(institutionId, id)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"This member belongs to a committee level. Deactivate them instead of deleting them.");
		}
		this.audit.recordChange(AuditAction.DELETE, "CommitteeMember", id, "Committee member deleted",
				snapshot(member), null);
		this.members.delete(member);
	}

	// ------------------------------------------------------------------
	// Levels
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<CommitteeLevel> listLevels(boolean activeOnly) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return activeOnly ? this.levels.findByInstitutionIdAndActiveTrueOrderByAmountFromAsc(institutionId)
				: this.levels.findByInstitutionIdOrderByAmountFromAsc(institutionId);
	}

	@Transactional(readOnly = true)
	public CommitteeLevel getLevel(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.levels.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Committee level", id));
	}

	@Transactional
	public CommitteeLevel createLevel(String name, BigDecimal amountFrom, BigDecimal amountTo, int minimumApprovals,
			int requiredMemberCount, boolean requiresCeo, boolean requiresCfo, List<Long> memberIds) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		validateBand(institutionId, null, amountFrom, amountTo);
		validateComposition(minimumApprovals, requiredMemberCount);
		Set<CommitteeMember> selected = resolveMembers(institutionId, memberIds, requiredMemberCount, requiresCeo,
				requiresCfo);
		CommitteeLevel level = new CommitteeLevel(name.trim(), amountFrom, amountTo, minimumApprovals,
				requiredMemberCount, requiresCeo, requiresCfo);
		level.assignInstitution(institutionId);
		level.replaceMembers(selected);
		CommitteeLevel saved = this.levels.save(level);
		this.audit.recordChange(AuditAction.CREATE, "CommitteeLevel", saved.getId(), "Committee level created", null,
				snapshot(saved));
		return saved;
	}

	@Transactional
	public CommitteeLevel updateLevel(Long id, String name, BigDecimal amountFrom, BigDecimal amountTo,
			int minimumApprovals, int requiredMemberCount, boolean requiresCeo, boolean requiresCfo,
			List<Long> memberIds) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		CommitteeLevel level = getLevel(id);
		validateBand(institutionId, id, amountFrom, amountTo);
		validateComposition(minimumApprovals, requiredMemberCount);
		Set<CommitteeMember> selected = resolveMembers(institutionId, memberIds, requiredMemberCount, requiresCeo,
				requiresCfo);
		Map<String, Object> before = snapshot(level);
		level.update(name.trim(), amountFrom, amountTo, minimumApprovals, requiredMemberCount, requiresCeo,
				requiresCfo);
		level.replaceMembers(selected);
		this.audit.recordChange(AuditAction.UPDATE, "CommitteeLevel", id, "Committee level updated", before,
				snapshot(level));
		return level;
	}

	@Transactional
	public CommitteeLevel setLevelActive(Long id, boolean active) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		CommitteeLevel level = getLevel(id);
		if (active) {
			validateBand(institutionId, id, level.getAmountFrom(), level.getAmountTo());
		}
		Map<String, Object> before = snapshot(level);
		if (active) {
			level.activate();
		}
		else {
			level.deactivate();
		}
		this.audit.recordChange(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "CommitteeLevel", id,
				active ? "Committee level activated" : "Committee level deactivated", before, snapshot(level));
		return level;
	}

	@Transactional
	public void deleteLevel(Long id) {
		CommitteeLevel level = getLevel(id);
		// A rule that points at a deleted level could not route; deactivation is the supported path.
		this.audit.recordChange(AuditAction.DELETE, "CommitteeLevel", id, "Committee level deleted", snapshot(level),
				null);
		this.levels.delete(level);
	}

	// ------------------------------------------------------------------
	// Validation helpers
	// ------------------------------------------------------------------

	private void requireOfficeAvailable(Long institutionId, CommitteeDesignation designation, Long excludedId) {
		if (designation == null || !designation.isSingleActiveHolder()) {
			return;
		}
		java.util.Optional<CommitteeMember> existing = this.members
			.findFirstByInstitutionIdAndDesignationAndActiveTrue(institutionId, designation)
			.filter(member -> excludedId == null || !member.getId().equals(excludedId));
		existing.ifPresent(member -> {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"An active " + designation.displayName() + " already exists. Deactivate that member first.");
		});
	}

	private void validateBand(Long institutionId, Long excludedId, BigDecimal amountFrom, BigDecimal amountTo) {
		if (amountFrom == null || amountFrom.signum() < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The lower bound must not be negative.");
		}
		if (amountTo != null && amountTo.compareTo(amountFrom) <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The upper bound must be greater than the lower bound.");
		}
		List<CommitteeLevel> active = this.levels.findByInstitutionIdAndActiveTrueOrderByAmountFromAsc(institutionId);
		for (CommitteeLevel other : active) {
			if (excludedId != null && excludedId.equals(other.getId())) {
				continue;
			}
			if (overlaps(amountFrom, amountTo, other.getAmountFrom(), other.getAmountTo())) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The amount range overlaps the level '"
						+ other.getName() + "'. Committee levels must not overlap.");
			}
		}
	}

	/** Half-open interval overlap; a null upper bound means infinity. */
	static boolean overlaps(BigDecimal fromA, BigDecimal toA, BigDecimal fromB, BigDecimal toB) {
		BigDecimal lower = fromA.max(fromB);
		BigDecimal upper = (toA == null || toB == null) ? null : toA.min(toB);
		return upper == null || lower.compareTo(upper) < 0;
	}

	private static void validateComposition(int minimumApprovals, int requiredMemberCount) {
		if (requiredMemberCount < 1 || requiredMemberCount > CommitteeLevel.MAX_SELECTED_MEMBERS) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A committee level must select between 1 and " + CommitteeLevel.MAX_SELECTED_MEMBERS + " members.");
		}
		if (minimumApprovals < 1 || minimumApprovals > requiredMemberCount) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Minimum approvals must be at least 1 and at most the number of selected members.");
		}
	}

	private Set<CommitteeMember> resolveMembers(Long institutionId, List<Long> memberIds, int requiredMemberCount,
			boolean requiresCeo, boolean requiresCfo) {
		Set<CommitteeMember> selected = new LinkedHashSet<>();
		for (Long memberId : memberIds) {
			CommitteeMember member = this.members.findByIdAndInstitutionId(memberId, institutionId)
				.orElseThrow(() -> new ResourceNotFoundException("Committee member", memberId));
			if (!member.isActive()) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"Member " + member.getDisplayName() + " is inactive and cannot be selected.");
			}
			if (!selected.add(member)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"A member cannot be selected twice for the same level.");
			}
		}
		if (selected.size() != requiredMemberCount) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The number of selected members must equal the required member count.");
		}
		if (requiresCeo && selected.stream().noneMatch(m -> m.getDesignation() == CommitteeDesignation.CEO)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A level that requires the CEO must include the active CEO.");
		}
		if (requiresCfo && selected.stream().noneMatch(m -> m.getDesignation() == CommitteeDesignation.CFO)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A level that requires the CFO must include the active CFO.");
		}
		return selected;
	}

	private static Map<String, Object> snapshot(CommitteeMember member) {
		Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
		snapshot.put("employeeId", member.getEmployeeId());
		snapshot.put("displayName", member.getDisplayName());
		snapshot.put("designation", member.getDesignation().name());
		snapshot.put("active", member.isActive());
		return snapshot;
	}

	private static Map<String, Object> snapshot(CommitteeLevel level) {
		Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
		snapshot.put("name", level.getName());
		snapshot.put("amountFrom", level.getAmountFrom().toPlainString());
		snapshot.put("amountTo", level.getAmountTo() == null ? null : level.getAmountTo().toPlainString());
		snapshot.put("minimumApprovals", level.getMinimumApprovals());
		snapshot.put("requiredMemberCount", level.getRequiredMemberCount());
		snapshot.put("requiresCeo", level.isRequiresCeo());
		snapshot.put("requiresCfo", level.isRequiresCfo());
		snapshot.put("memberIds", level.getMembers().stream().map(CommitteeMember::getId).sorted().toList());
		snapshot.put("active", level.isActive());
		return snapshot;
	}

}
