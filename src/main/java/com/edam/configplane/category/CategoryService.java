package com.edam.configplane.category;

import java.util.List;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.InstitutionContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category and sub-category administration (BRD FM-07, §8.6).
 *
 * <h2>Rules implemented here</h2>
 * <ul>
 *   <li>Active name uniqueness per institution (backed by a partial unique index, so a concurrent
 *       insert surfaces as a duplicate rather than as a silent duplicate row).</li>
 *   <li>Sub-category names unique within their category.</li>
 *   <li>Deleting a category that still has sub-categories is refused with a business message;
 *       the foreign key is the backstop.</li>
 *   <li>Deletion is physical only when the category has never been used; otherwise the caller is
 *       told to deactivate. History must not lose the name a request referenced.</li>
 * </ul>
 *
 * <p>Every mutation writes an audit record with before/after values (AUD-02). The recorder runs in
 * its own transaction so the evidence survives a rollback of the business change.
 *
 * <p>Institution context is resolved from the authenticated identity; no method accepts a tenant
 * id from the caller.
 */
@Service
public class CategoryService {

	private final ExpenseCategoryRepository categories;

	private final ExpenseSubcategoryRepository subcategories;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	public CategoryService(ExpenseCategoryRepository categories, ExpenseSubcategoryRepository subcategories,
			InstitutionContext institutionContext, AuditRecorder audit) {
		this.categories = categories;
		this.subcategories = subcategories;
		this.institutionContext = institutionContext;
		this.audit = audit;
	}

	// ------------------------------------------------------------------
	// Categories
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ExpenseCategory> listCategories(boolean activeOnly) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return activeOnly ? this.categories.findByInstitutionIdAndActiveTrueOrderByDisplayOrderAscNameAsc(institutionId)
				: this.categories.findByInstitutionIdOrderByDisplayOrderAscNameAsc(institutionId);
	}

	@Transactional(readOnly = true)
	public ExpenseCategory getCategory(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.categories.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Expense category", id));
	}

	@Transactional
	public ExpenseCategory createCategory(String name, String description, Integer displayOrder) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		if (this.categories.existsByInstitutionIdAndNameIgnoreCaseAndActiveTrue(institutionId, name)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"An active category with that name already exists.");
		}
		ExpenseCategory category = new ExpenseCategory(name.trim(), description);
		category.assignInstitution(institutionId);
		category.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
		ExpenseCategory saved = this.categories.save(category);
		this.audit.recordChange(AuditAction.CREATE, "ExpenseCategory", saved.getId(), "Category created",
				null, snapshot(saved));
		return saved;
	}

	@Transactional
	public ExpenseCategory updateCategory(Long id, String name, String description, Integer displayOrder) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExpenseCategory category = getCategory(id);
		if (this.categories.existsByInstitutionIdAndNameIgnoreCaseAndActiveTrueAndIdNot(institutionId, name, id)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"An active category with that name already exists.");
		}
		var before = snapshot(category);
		category.update(name.trim(), description, displayOrder);
		this.audit.recordChange(AuditAction.UPDATE, "ExpenseCategory", id, "Category updated", before,
				snapshot(category));
		return category;
	}

	@Transactional
	public ExpenseCategory setCategoryActive(Long id, boolean active) {
		ExpenseCategory category = getCategory(id);
		var before = snapshot(category);
		if (active) {
			category.activate();
		}
		else {
			category.deactivate();
		}
		this.audit.recordChange(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "ExpenseCategory", id,
				"Category " + (active ? "activated" : "deactivated"), before, snapshot(category));
		return category;
	}

	@Transactional
	public void deleteCategory(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExpenseCategory category = getCategory(id);
		long children = this.subcategories.countByInstitutionIdAndCategoryId(institutionId, id);
		if (children > 0) {
			throw new BusinessException("A category with sub-categories cannot be deleted. "
					+ "Delete or move its sub-categories first, or deactivate the category.");
		}
		var before = snapshot(category);
		this.categories.delete(category);
		this.audit.recordChange(AuditAction.DELETE, "ExpenseCategory", id, "Category deleted", before, null);
	}

	// ------------------------------------------------------------------
	// Sub-categories
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ExpenseSubcategory> listSubcategories(Long categoryId, boolean activeOnly) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		// Resolve the parent first: this both validates existence and refuses a foreign category.
		getCategory(categoryId);
		List<ExpenseSubcategory> all = this.subcategories
			.findByInstitutionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(institutionId, categoryId);
		return activeOnly ? all.stream().filter(ExpenseSubcategory::isActive).toList() : all;
	}

	@Transactional(readOnly = true)
	public ExpenseSubcategory getSubcategory(Long id) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.subcategories.findByIdAndInstitutionId(id, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Expense sub-category", id));
	}

	@Transactional
	public ExpenseSubcategory createSubcategory(Long categoryId, String name, String description,
			Integer displayOrder) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExpenseCategory category = getCategory(categoryId);
		if (this.subcategories.existsByInstitutionIdAndCategoryIdAndNameIgnoreCaseAndActiveTrue(institutionId,
				categoryId, name)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"An active sub-category with that name already exists in this category.");
		}
		ExpenseSubcategory subcategory = new ExpenseSubcategory(category, name.trim(), description);
		subcategory.assignInstitution(institutionId);
		subcategory.update(category, name.trim(), description, displayOrder == null ? 0 : displayOrder);
		ExpenseSubcategory saved = this.subcategories.save(subcategory);
		this.audit.recordChange(AuditAction.CREATE, "ExpenseSubcategory", saved.getId(), "Sub-category created", null,
				snapshot(saved));
		return saved;
	}

	@Transactional
	public ExpenseSubcategory updateSubcategory(Long id, Long categoryId, String name, String description,
			Integer displayOrder) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExpenseSubcategory subcategory = getSubcategory(id);
		ExpenseCategory category = getCategory(categoryId);
		if (this.subcategories.existsByInstitutionIdAndCategoryIdAndNameIgnoreCaseAndActiveTrueAndIdNot(institutionId,
				categoryId, name, id)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"An active sub-category with that name already exists in this category.");
		}
		var before = snapshot(subcategory);
		subcategory.update(category, name.trim(), description, displayOrder);
		this.audit.recordChange(AuditAction.UPDATE, "ExpenseSubcategory", id, "Sub-category updated", before,
				snapshot(subcategory));
		return subcategory;
	}

	@Transactional
	public ExpenseSubcategory setSubcategoryActive(Long id, boolean active) {
		ExpenseSubcategory subcategory = getSubcategory(id);
		var before = snapshot(subcategory);
		if (active) {
			subcategory.activate();
		}
		else {
			subcategory.deactivate();
		}
		this.audit.recordChange(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "ExpenseSubcategory", id,
				"Sub-category " + (active ? "activated" : "deactivated"), before, snapshot(subcategory));
		return subcategory;
	}

	@Transactional
	public void deleteSubcategory(Long id) {
		ExpenseSubcategory subcategory = getSubcategory(id);
		var before = snapshot(subcategory);
		this.subcategories.delete(subcategory);
		this.audit.recordChange(AuditAction.DELETE, "ExpenseSubcategory", id, "Sub-category deleted", before, null);
	}

	private static java.util.Map<String, Object> snapshot(ExpenseCategory category) {
		java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
		state.put("name", category.getName());
		state.put("description", category.getDescription());
		state.put("active", category.isActive());
		state.put("displayOrder", category.getDisplayOrder());
		return state;
	}

	private static java.util.Map<String, Object> snapshot(ExpenseSubcategory subcategory) {
		java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
		state.put("category", subcategory.getCategory().getName());
		state.put("name", subcategory.getName());
		state.put("description", subcategory.getDescription());
		state.put("active", subcategory.isActive());
		state.put("displayOrder", subcategory.getDisplayOrder());
		return state;
	}

}
