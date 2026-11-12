package com.edam.configplane.limit;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.security.InstitutionContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Department-head general delegation (BRD FM-10).
 *
 * <h2>The single-record rule</h2>
 * The BRD allows <em>one</em> General Delegation record per institution, "including an inactive
 * record". This is enforced by a unique index on {@code institution_id}, so the service never has to
 * decide whether a second row is allowed — it cannot exist. The service therefore exposes a get
 * and an upsert, and an attempt to create a second record is rejected with a business message
 * rather than surfacing a database error.
 *
 * <h2>"Not configured" is a state, not zero</h2>
 * When no record exists, the API reports {@code configured = false} and no amount, and the routing
 * fallback (BRD OD-06, value PD-6) applies. Returning zero would silently route every request above
 * nothing to the CEO.
 */
@Service
public class DepartmentHeadLimitService {

	private final DepartmentHeadLimitRepository limits;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	public DepartmentHeadLimitService(DepartmentHeadLimitRepository limits, InstitutionContext institutionContext,
			AuditRecorder audit) {
		this.limits = limits;
		this.institutionContext = institutionContext;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public Optional<DepartmentHeadLimit> find() {
		return this.limits.findByInstitutionId(this.institutionContext.requireInstitutionId());
	}

	@Transactional
	public DepartmentHeadLimit upsert(BigDecimal amount, String description, Boolean active) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		requirePositive(amount);
		return this.limits.findByInstitutionId(institutionId).map(existing -> {
			Map<String, Object> before = snapshot(existing);
			existing.update(amount, description);
			if (Boolean.TRUE.equals(active)) {
				existing.activate();
			}
			else if (Boolean.FALSE.equals(active)) {
				existing.deactivate();
			}
			this.audit.recordChange(AuditAction.UPDATE, "DepartmentHeadLimit", existing.getId(),
					"Department-head general delegation updated", before, snapshot(existing));
			return existing;
		}).orElseGet(() -> {
			DepartmentHeadLimit created = new DepartmentHeadLimit(amount, description);
			created.assignInstitution(institutionId);
			if (Boolean.FALSE.equals(active)) {
				created.deactivate();
			}
			DepartmentHeadLimit saved = this.limits.save(created);
			this.audit.recordChange(AuditAction.CREATE, "DepartmentHeadLimit", saved.getId(),
					"Department-head general delegation created", null, snapshot(saved));
			return saved;
		});
	}

	private static void requirePositive(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The limit must be greater than zero.");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The limit may have at most two decimal places.");
		}
	}

	private static Map<String, Object> snapshot(DepartmentHeadLimit limit) {
		Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
		snapshot.put("amount", limit.getAmount().toPlainString());
		snapshot.put("description", limit.getDescription());
		snapshot.put("active", limit.isActive());
		return snapshot;
	}

}
