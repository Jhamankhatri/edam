package com.edam.configplane.limit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;
import com.edam.configplane.ExpenseNature;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exceeding-limit administration (BRD FM-13).
 *
 * <h2>What this deliberately does not do</h2>
 * BRD FM-13 defines the limits but leaves the <em>consequence</em> of exceeding them open (OD-27:
 * whether an exceeding request is blocked, routed to the CEO, or merely reported). Therefore this
 * service stores and validates thresholds and nothing else. No routing code reads them, and the API
 * response states {@code routingEffect = NOT_ENFORCED} so a client cannot infer enforcement that
 * does not exist.
 *
 * <p>This replaces the foundation's {@code exceeding-limit-enforced-in-routing} boolean, which
 * asserted a business decision the BRD had not made (audit defect D-4).
 *
 * <h2>Cardinality</h2>
 * At most one threshold per expense nature per institution, enforced by a unique index. The upsert
 * shape reflects that: there is no create-versus-update ambiguity for the client.
 */
@Service
public class ExceedingLimitService {

	private final ExceedingLimitRepository limits;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	public ExceedingLimitService(ExceedingLimitRepository limits, InstitutionContext institutionContext,
			AuditRecorder audit) {
		this.limits = limits;
		this.institutionContext = institutionContext;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<ExceedingLimit> list() {
		return this.limits.findByInstitutionIdOrderByExpenseNatureAsc(
				this.institutionContext.requireInstitutionId());
	}

	@Transactional(readOnly = true)
	public ExceedingLimit get(ExpenseNature nature) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.limits.findByInstitutionIdAndExpenseNature(institutionId, nature)
			.orElseThrow(() -> new ResourceNotFoundException("Exceeding limit for " + nature, nature));
	}

	/**
	 * Create or replace the threshold for one nature.
	 *
	 * <p>Upsert rather than separate create/update because the identity of the row is the
	 * (institution, nature) pair, not a client-generated id.
	 */
	@Transactional
	public ExceedingLimit upsert(ExpenseNature nature, BigDecimal amount, String description, Boolean active) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		requirePositive(amount);
		return this.limits.findByInstitutionIdAndExpenseNature(institutionId, nature).map(existing -> {
			Map<String, Object> before = snapshot(existing);
			existing.update(amount, description);
			if (Boolean.TRUE.equals(active)) {
				existing.activate();
			}
			else if (Boolean.FALSE.equals(active)) {
				existing.deactivate();
			}
			this.audit.recordChange(AuditAction.UPDATE, "ExceedingLimit", existing.getId(),
					"Exceeding limit updated for " + nature, before, snapshot(existing));
			return existing;
		}).orElseGet(() -> {
			ExceedingLimit created = new ExceedingLimit(nature, amount, description);
			created.assignInstitution(institutionId);
			if (Boolean.FALSE.equals(active)) {
				created.deactivate();
			}
			ExceedingLimit saved = this.limits.save(created);
			this.audit.recordChange(AuditAction.CREATE, "ExceedingLimit", saved.getId(),
					"Exceeding limit created for " + nature, null, snapshot(saved));
			return saved;
		});
	}

	@Transactional
	public void delete(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExceedingLimit limit = this.limits.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Exceeding limit", id));
		this.audit.recordChange(AuditAction.DELETE, "ExceedingLimit", id, "Exceeding limit deleted", snapshot(limit),
				null);
		this.limits.delete(limit);
	}

	private static void requirePositive(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The limit must be greater than zero.");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The limit may have at most two decimal places.");
		}
	}

	private static Map<String, Object> snapshot(ExceedingLimit limit) {
		Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
		snapshot.put("expenseNature", limit.getExpenseNature().name());
		snapshot.put("amount", limit.getAmount().toPlainString());
		snapshot.put("description", limit.getDescription());
		snapshot.put("active", limit.isActive());
		return snapshot;
	}

}
