package com.edam.configplane.authority;

import java.util.List;
import java.util.Map;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;
import com.edam.configplane.category.DelegationRuleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recommending-authority administration (BRD FM-12).
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>An active authority title is unique per institution — two authorities with the same title
 *       would be indistinguishable in the delegation matrix.</li>
 *   <li>The title is immutable after creation. Delegation rules and audit records reference it by
 *       name, so a rename would obscure what was approved. Re-assigning the holder is allowed and
 *       separately audited, which is the operation the BRD actually needs (the person changes, the
 *       authority does not).</li>
 *   <li>An authority referenced by an active delegation rule cannot be deleted; deactivate instead,
 *       so historical rules keep a resolvable reference.</li>
 * </ul>
 */
@Service
public class RecommendingAuthorityService {

	private final RecommendingAuthorityRepository authorities;

	private final DelegationRuleRepository rules;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	public RecommendingAuthorityService(RecommendingAuthorityRepository authorities, DelegationRuleRepository rules,
			InstitutionContext institutionContext, AuditRecorder audit) {
		this.authorities = authorities;
		this.rules = rules;
		this.institutionContext = institutionContext;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<RecommendingAuthority> list(boolean activeOnly) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return activeOnly ? this.authorities.findByInstitutionIdAndActiveTrueOrderByTitleAsc(institutionId)
				: this.authorities.findByInstitutionIdOrderByTitleAsc(institutionId);
	}

	@Transactional(readOnly = true)
	public RecommendingAuthority get(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.authorities.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Recommending authority", id));
	}

	@Transactional
	public RecommendingAuthority create(String title, String departmentCode, String employeeId,
			String employeeDisplayName) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		String cleanTitle = requireTitle(title);
		if (this.authorities.existsByInstitutionIdAndTitleIgnoreCaseAndActiveTrue(institutionId, cleanTitle)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"An active recommending authority with that title already exists.");
		}
		RecommendingAuthority authority = new RecommendingAuthority(cleanTitle, departmentCode.trim(),
				employeeId.trim(), employeeDisplayName);
		authority.assignInstitution(institutionId);
		RecommendingAuthority saved = this.authorities.save(authority);
		this.audit.recordChange(AuditAction.CREATE, "RecommendingAuthority", saved.getId(),
				"Recommending authority created", null, snapshot(saved));
		return saved;
	}

	/**
	 * Re-assign the holder and department. The title is intentionally not editable: rules and audit
	 * records identify the authority by title, so changing it would rewrite the meaning of history.
	 */
	@Transactional
	public RecommendingAuthority reassign(Long id, String departmentCode, String employeeId,
			String employeeDisplayName) {
		RecommendingAuthority authority = get(id);
		Map<String, Object> before = snapshot(authority);
		authority.reassign(departmentCode.trim(), employeeId.trim(), employeeDisplayName);
		this.audit.recordChange(AuditAction.UPDATE, "RecommendingAuthority", id,
				"Recommending authority holder re-assigned", before, snapshot(authority));
		return authority;
	}

	@Transactional
	public RecommendingAuthority setActive(Long id, boolean active) {
		RecommendingAuthority authority = get(id);
		Long institutionId = this.institutionContext.requireInstitutionId();
		if (active && this.authorities.existsByInstitutionIdAndTitleIgnoreCaseAndActiveTrue(institutionId,
				authority.getTitle())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"Another active recommending authority already uses that title.");
		}
		Map<String, Object> before = snapshot(authority);
		if (active) {
			authority.activate();
		}
		else {
			authority.deactivate();
		}
		this.audit.recordChange(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "RecommendingAuthority", id,
				active ? "Recommending authority activated" : "Recommending authority deactivated", before,
				snapshot(authority));
		return authority;
	}

	@Transactional
	public void delete(Long id) {
		RecommendingAuthority authority = get(id);
		Long institutionId = this.institutionContext.requireInstitutionId();
		if (this.rules.existsByInstitutionIdAndRecommendingAuthoritiesId(institutionId, id)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"This authority is referenced by a delegation rule. Deactivate it instead of deleting it.");
		}
		this.audit.recordChange(AuditAction.DELETE, "RecommendingAuthority", id, "Recommending authority deleted",
				snapshot(authority), null);
		this.authorities.delete(authority);
	}

	private static String requireTitle(String title) {
		String clean = title == null ? "" : title.trim();
		if (!clean.matches("[A-Za-z0-9 ]+")) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The title may contain only letters, digits and spaces.");
		}
		return clean;
	}

	private static Map<String, Object> snapshot(RecommendingAuthority authority) {
		Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
		snapshot.put("title", authority.getTitle());
		snapshot.put("departmentCode", authority.getDepartmentCode());
		snapshot.put("employeeId", authority.getEmployeeId());
		snapshot.put("active", authority.isActive());
		return snapshot;
	}

}
