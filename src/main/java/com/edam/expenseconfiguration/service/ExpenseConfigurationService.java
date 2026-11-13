package com.edam.expenseconfiguration.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.CategoryResponse;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.LookupResponse;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.SaveCategoryRequest;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.SaveLookupRequest;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.SaveSubcategoryRequest;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.SubcategoryResponse;
import com.edam.expenseconfiguration.model.ExpenseCategory;
import com.edam.expenseconfiguration.model.ExpenseNature;
import com.edam.expenseconfiguration.model.ExpenseRequestType;
import com.edam.expenseconfiguration.model.ExpenseSubcategory;
import com.edam.expenseconfiguration.repository.ExpenseCategoryRepository;
import com.edam.expenseconfiguration.repository.ExpenseNatureRepository;
import com.edam.expenseconfiguration.repository.ExpenseRequestTypeRepository;
import com.edam.expenseconfiguration.repository.ExpenseSubcategoryRepository;
import com.edam.delegation.repository.DelegationRuleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expense catalogue: categories, subcategories, natures and request types (Module E).
 *
 * <h2>Why the catalogue is not seeded</h2>
 * The product ships no categories. A bank's expense chart is a business decision owned by its finance
 * function; a vendor-supplied "Travel / Accommodation / Meals" tree would be either wrong or accidentally
 * adopted, and it would be indistinguishable from real configuration after installation.
 *
 * <h2>What cannot be changed, and why</h2>
 * <ul>
 *   <li><strong>A category's expense type</strong> cannot move from OPEX to CAPEX once requests exist
 *       under it. Doing so would reclassify historical spend, changing every report that grouped by type
 *       without any record of the edit. The service refuses and directs the administrator to create a new
 *       category.</li>
 *   <li><strong>A category cannot be deactivated</strong> while active delegation rules reference it: a
 *       request under that category would then resolve no route, and the requester would be blocked with a
 *       message about approval routing rather than the real cause.</li>
 * </ul>
 */
@Service
public class ExpenseConfigurationService {

	private final ExpenseCategoryRepository categories;

	private final ExpenseSubcategoryRepository subcategories;

	private final ExpenseNatureRepository natures;

	private final ExpenseRequestTypeRepository requestTypes;

	private final DelegationRuleRepository delegationRules;

	private final AuditRecorder audit;

	public ExpenseConfigurationService(ExpenseCategoryRepository categories,
			ExpenseSubcategoryRepository subcategories, ExpenseNatureRepository natures,
			ExpenseRequestTypeRepository requestTypes, DelegationRuleRepository delegationRules, AuditRecorder audit) {
		this.categories = categories;
		this.subcategories = subcategories;
		this.natures = natures;
		this.requestTypes = requestTypes;
		this.delegationRules = delegationRules;
		this.audit = audit;
	}

	// ------------------------------------------------------------------
	// Categories
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<CategoryResponse> listCategories(boolean includeInactive) {
		List<ExpenseCategory> rows = includeInactive ? this.categories.findAllByOrderByNameAsc()
				: this.categories.findByActiveTrueOrderByNameAsc();
		Map<UUID, List<ExpenseSubcategory>> byCategory = new HashMap<>();
		List<ExpenseSubcategory> allSubcategories = includeInactive ? this.subcategories.findAll()
				: this.subcategories.findByActiveTrueOrderByNameAsc();
		for (ExpenseSubcategory subcategory : allSubcategories) {
			byCategory.computeIfAbsent(subcategory.getCategoryId(), key -> new ArrayList<>()).add(subcategory);
		}
		return rows.stream().map(category -> {
			List<ExpenseSubcategory> children = new ArrayList<>(
					byCategory.getOrDefault(category.getId(), List.of()));
			children.sort(Comparator.comparing(ExpenseSubcategory::getName, String.CASE_INSENSITIVE_ORDER));
			return new CategoryResponse(category.getId(), category.getCode(), category.getName(),
					category.getDescription(), category.getExpenseType(), category.isActive(),
					children.stream().map(ExpenseConfigurationService::toResponse).toList());
		}).toList();
	}

	@Transactional
	public CategoryResponse createCategory(SaveCategoryRequest request) {
		String code = normaliseCode(request.code());
		if (this.categories.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A category with code " + code + " exists.");
		}
		ExpenseCategory category = new ExpenseCategory();
		category.setCode(code);
		category.setName(request.name().trim());
		category.setDescription(request.description());
		category.setExpenseType(request.expenseType());
		ExpenseCategory saved = this.categories.save(category);
		this.audit.record(AuditAction.CREATE, "ExpenseCategory", saved.getId());
		return toResponse(saved, List.of());
	}

	@Transactional
	public CategoryResponse updateCategory(UUID id, SaveCategoryRequest request) {
		ExpenseCategory category = requireCategory(id);
		String code = normaliseCode(request.code());
		if (!code.equalsIgnoreCase(category.getCode()) && this.categories.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A category with code " + code + " exists.");
		}
		if (category.getExpenseType() != request.expenseType()) {
			// Historical spend would be reclassified silently. Refusing is the only option that keeps old
			// reports truthful; the administrator can create a new category for the new classification.
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The expense type of an existing category cannot change. Create a new category instead.");
		}
		category.setCode(code);
		category.setName(request.name().trim());
		category.setDescription(request.description());
		ExpenseCategory saved = this.categories.save(category);
		this.audit.record(AuditAction.UPDATE, "ExpenseCategory", id);
		return toResponse(saved, subcategoryResponses(id));
	}

	@Transactional
	public CategoryResponse setCategoryActive(UUID id, boolean active) {
		ExpenseCategory category = requireCategory(id);
		if (!active && this.delegationRules.existsByCategoryIdAndActiveTrue(id)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Active delegation rules use this category. Deactivate those rules first, otherwise requests "
							+ "under it would have no approval route.");
		}
		category.setActive(active);
		ExpenseCategory saved = this.categories.save(category);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "ExpenseCategory", id);
		return toResponse(saved, subcategoryResponses(id));
	}

	// ------------------------------------------------------------------
	// Subcategories
	// ------------------------------------------------------------------

	@Transactional
	public SubcategoryResponse createSubcategory(UUID categoryId, SaveSubcategoryRequest request) {
		ExpenseCategory category = requireCategory(categoryId);
		String code = normaliseCode(request.code());
		if (this.subcategories.existsByCategoryIdAndCodeIgnoreCase(categoryId, code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"A subcategory with code " + code + " exists in " + category.getName() + ".");
		}
		ExpenseSubcategory subcategory = new ExpenseSubcategory();
		subcategory.setCategoryId(categoryId);
		subcategory.setCode(code);
		subcategory.setName(request.name().trim());
		subcategory.setDescription(request.description());
		ExpenseSubcategory saved = this.subcategories.save(subcategory);
		this.audit.record(AuditAction.CREATE, "ExpenseSubcategory", saved.getId());
		return toResponse(saved);
	}

	@Transactional
	public SubcategoryResponse updateSubcategory(UUID id, SaveSubcategoryRequest request) {
		ExpenseSubcategory subcategory = requireSubcategory(id);
		String code = normaliseCode(request.code());
		if (!code.equalsIgnoreCase(subcategory.getCode())
				&& this.subcategories.existsByCategoryIdAndCodeIgnoreCase(subcategory.getCategoryId(), code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A subcategory with code " + code + " exists.");
		}
		subcategory.setCode(code);
		subcategory.setName(request.name().trim());
		subcategory.setDescription(request.description());
		ExpenseSubcategory saved = this.subcategories.save(subcategory);
		this.audit.record(AuditAction.UPDATE, "ExpenseSubcategory", id);
		return toResponse(saved);
	}

	@Transactional
	public SubcategoryResponse setSubcategoryActive(UUID id, boolean active) {
		ExpenseSubcategory subcategory = requireSubcategory(id);
		subcategory.setActive(active);
		ExpenseSubcategory saved = this.subcategories.save(subcategory);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "ExpenseSubcategory", id);
		return toResponse(saved);
	}

	// ------------------------------------------------------------------
	// Natures
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<LookupResponse> listNatures() {
		return this.natures.findAllByOrderByNameAsc().stream().map(ExpenseConfigurationService::toResponse).toList();
	}

	@Transactional
	public LookupResponse createNature(SaveLookupRequest request) {
		String code = normaliseCode(request.code());
		if (this.natures.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A nature with code " + code + " exists.");
		}
		ExpenseNature nature = new ExpenseNature();
		nature.setCode(code);
		nature.setName(request.name().trim());
		nature.setDescription(request.description());
		ExpenseNature saved = this.natures.save(nature);
		this.audit.record(AuditAction.CREATE, "ExpenseNature", saved.getId());
		return toResponse(saved);
	}

	@Transactional
	public LookupResponse setNatureActive(UUID id, boolean active) {
		ExpenseNature nature = this.natures.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseNature", id));
		if (!active && this.delegationRules.existsByNatureIdAndActiveTrue(id)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Active delegation rules use this nature. Deactivate those rules first.");
		}
		nature.setActive(active);
		ExpenseNature saved = this.natures.save(nature);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "ExpenseNature", id);
		return toResponse(saved);
	}

	// ------------------------------------------------------------------
	// Request types
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<LookupResponse> listRequestTypes() {
		return this.requestTypes.findByActiveTrueOrderByNameAsc()
			.stream()
			.map(ExpenseConfigurationService::toResponse)
			.toList();
	}

	@Transactional
	public LookupResponse createRequestType(SaveLookupRequest request) {
		String code = normaliseCode(request.code());
		if (this.requestTypes.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
					"A request type with code " + code + " exists.");
		}
		ExpenseRequestType type = new ExpenseRequestType();
		type.setCode(code);
		type.setName(request.name().trim());
		type.setDescription(request.description());
		ExpenseRequestType saved = this.requestTypes.save(type);
		this.audit.record(AuditAction.CREATE, "ExpenseRequestType", saved.getId());
		return toResponse(saved);
	}

	// ------------------------------------------------------------------
	// Lookups used by other modules
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public ExpenseCategory requireCategory(UUID id) {
		return this.categories.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", id));
	}

	@Transactional(readOnly = true)
	public ExpenseNature requireNature(UUID id) {
		return this.natures.findById(id).orElseThrow(() -> new ResourceNotFoundException("ExpenseNature", id));
	}

	@Transactional(readOnly = true)
	public ExpenseSubcategory requireSubcategory(UUID id) {
		return this.subcategories.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseSubcategory", id));
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private List<SubcategoryResponse> subcategoryResponses(UUID categoryId) {
		return this.subcategories.findByCategoryIdOrderByNameAsc(categoryId)
			.stream()
			.map(ExpenseConfigurationService::toResponse)
			.toList();
	}

	private static CategoryResponse toResponse(ExpenseCategory category, List<SubcategoryResponse> children) {
		return new CategoryResponse(category.getId(), category.getCode(), category.getName(),
				category.getDescription(), category.getExpenseType(), category.isActive(), children);
	}

	private static SubcategoryResponse toResponse(ExpenseSubcategory subcategory) {
		return new SubcategoryResponse(subcategory.getId(), subcategory.getCategoryId(), subcategory.getCode(),
				subcategory.getName(), subcategory.getDescription(), subcategory.isActive());
	}

	private static LookupResponse toResponse(ExpenseNature nature) {
		return new LookupResponse(nature.getId(), nature.getCode(), nature.getName(), nature.getDescription(),
				nature.isActive());
	}

	private static LookupResponse toResponse(ExpenseRequestType type) {
		return new LookupResponse(type.getId(), type.getCode(), type.getName(), type.getDescription(),
				type.isActive());
	}

	static String normaliseCode(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A code is required.");
		}
		return raw.trim().toUpperCase(java.util.Locale.ROOT);
	}

}
