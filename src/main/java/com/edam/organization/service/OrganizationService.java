package com.edam.organization.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.organization.dto.OrganizationDtos.BranchResponse;
import com.edam.organization.dto.OrganizationDtos.CreateOrganizationUnitRequest;
import com.edam.organization.dto.OrganizationDtos.DepartmentResponse;
import com.edam.organization.dto.OrganizationDtos.OrganizationUnitNode;
import com.edam.organization.dto.OrganizationDtos.OrganizationUnitResponse;
import com.edam.organization.dto.OrganizationDtos.OrganizationUnitTypeResponse;
import com.edam.organization.dto.OrganizationDtos.SaveBranchRequest;
import com.edam.organization.dto.OrganizationDtos.SaveDepartmentRequest;
import com.edam.organization.dto.OrganizationDtos.UpdateOrganizationUnitRequest;
import com.edam.organization.model.Branch;
import com.edam.organization.model.Department;
import com.edam.organization.model.OrganizationUnit;
import com.edam.organization.model.OrganizationUnitType;
import com.edam.organization.repository.BranchRepository;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.organization.repository.OrganizationUnitRepository;
import com.edam.organization.repository.OrganizationUnitTypeRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the organization hierarchy, departments and branches (Module A).
 *
 * <h2>Invariants this service is responsible for</h2>
 * <ol>
 *   <li><strong>No cycles.</strong> The database prevents a self-parent, but A→B→C→A needs a walk of the
 *       ancestor chain before a parent is changed. This is the only place a cycle can appear, so the
 *       check lives here rather than in a trigger that would have to recurse.</li>
 *   <li><strong>Codes are unique, case-insensitively.</strong> The database has a unique index on the exact
 *       value; {@code "HR"} and {@code "hr"} would both be accepted by it, and a bank that later changes
 *       case in one place would end up with two "HR" departments in reports. Normalising to upper case on
 *       write makes the two agree.</li>
 *   <li><strong>A department and its organization unit agree.</strong> Created together, renamed together;
 *       see {@link Department}'s Javadoc.</li>
 * </ol>
 *
 * <h2>Delete policy</h2>
 * Units, departments and branches are deactivated, never deleted. Every one of them is referenced by
 * historical requests, and an organisation chart that loses the department a 2024 request came from is
 * not audit-ready. The requirement's "never destroy data" rule is enforced by not offering the operation.
 */
@Service
public class OrganizationService {

	private final OrganizationUnitRepository units;

	private final OrganizationUnitTypeRepository unitTypes;

	private final DepartmentRepository departments;

	private final BranchRepository branches;

	private final UserRepository users;

	private final AuditRecorder audit;

	public OrganizationService(OrganizationUnitRepository units, OrganizationUnitTypeRepository unitTypes,
			DepartmentRepository departments, BranchRepository branches, UserRepository users, AuditRecorder audit) {
		this.units = units;
		this.unitTypes = unitTypes;
		this.departments = departments;
		this.branches = branches;
		this.users = users;
		this.audit = audit;
	}

	// ------------------------------------------------------------------
	// Unit types
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<OrganizationUnitTypeResponse> listUnitTypes() {
		return this.unitTypes.findAllByOrderByNameAsc()
			.stream()
			.map(type -> new OrganizationUnitTypeResponse(type.getId(), type.getCode(), type.getName(),
					type.getDescription()))
			.toList();
	}

	// ------------------------------------------------------------------
	// Hierarchy
	// ------------------------------------------------------------------

	/**
	 * The whole hierarchy as a forest.
	 *
	 * <p>Built in memory from one query rather than by recursive SQL per node: the chart is small (a bank
	 * has hundreds of units, not millions), and a single load keeps the response consistent without a
	 * transaction that spans N round trips.
	 */
	@Transactional(readOnly = true)
	public List<OrganizationUnitNode> tree() {
		List<OrganizationUnit> all = this.units.findAll();
		Map<UUID, String> typeCodes = typeCodes();
		Map<UUID, List<OrganizationUnit>> byParent = new LinkedHashMap<>();
		List<OrganizationUnit> roots = new ArrayList<>();
		for (OrganizationUnit unit : all) {
			if (unit.getParentUnitId() == null) {
				roots.add(unit);
			}
			else {
				byParent.computeIfAbsent(unit.getParentUnitId(), key -> new ArrayList<>()).add(unit);
			}
		}
		Comparator<OrganizationUnit> byName = Comparator.comparing(OrganizationUnit::getName,
				String.CASE_INSENSITIVE_ORDER);
		roots.sort(byName);
		List<OrganizationUnitNode> forest = new ArrayList<>();
		for (OrganizationUnit root : roots) {
			forest.add(buildNode(root, byParent, typeCodes, 0));
		}
		return forest;
	}

	private OrganizationUnitNode buildNode(OrganizationUnit unit, Map<UUID, List<OrganizationUnit>> byParent,
			Map<UUID, String> typeCodes, int depth) {
		List<OrganizationUnit> children = new ArrayList<>(byParent.getOrDefault(unit.getId(), List.of()));
		children.sort(Comparator.comparing(OrganizationUnit::getName, String.CASE_INSENSITIVE_ORDER));
		List<OrganizationUnitNode> childNodes = new ArrayList<>();
		for (OrganizationUnit child : children) {
			childNodes.add(buildNode(child, byParent, typeCodes, depth + 1));
		}
		return new OrganizationUnitNode(toResponse(unit, typeCodes, depth), List.copyOf(childNodes));
	}

	@Transactional(readOnly = true)
	public List<OrganizationUnitResponse> listUnits() {
		Map<UUID, String> typeCodes = typeCodes();
		return this.units.findByActiveTrueOrderByNameAsc().stream().map(unit -> toResponse(unit, typeCodes, 0)).toList();
	}

	@Transactional
	public OrganizationUnitResponse createUnit(CreateOrganizationUnitRequest request) {
		String code = normaliseCode(request.code());
		if (this.units.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "An organization unit with code " + code + " exists.");
		}
		OrganizationUnitType type = this.unitTypes.findById(request.unitTypeId())
			.orElseThrow(() -> new ResourceNotFoundException("OrganizationUnitType", request.unitTypeId()));
		UUID parentId = request.parentUnitId();
		if (parentId != null) {
			requireUnit(parentId);
		}
		OrganizationUnit unit = new OrganizationUnit();
		unit.setCode(code);
		unit.setName(request.name().trim());
		unit.setDescription(request.description());
		unit.setUnitTypeId(type.getId());
		unit.setParentUnitId(parentId);
		OrganizationUnit saved = this.units.save(unit);
		this.audit.record(AuditAction.CREATE, "OrganizationUnit", saved.getId());
		return toResponse(saved, Map.of(type.getId(), type.getCode()), 0);
	}

	@Transactional
	public OrganizationUnitResponse updateUnit(UUID id, UpdateOrganizationUnitRequest request) {
		OrganizationUnit unit = requireUnit(id);
		UUID newParent = request.parentUnitId();
		if (newParent != null) {
			if (newParent.equals(id)) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A unit cannot be its own parent.");
			}
			requireUnit(newParent);
			requireNoCycle(id, newParent);
		}
		Map<String, Object> before = snapshot(unit);
		unit.setName(request.name().trim());
		unit.setDescription(request.description());
		unit.setParentUnitId(newParent);
		OrganizationUnit saved = this.units.save(unit);
		this.audit.recordChange(AuditAction.UPDATE, "OrganizationUnit", id, before, snapshot(saved));
		return toResponse(saved, typeCodes(), 0);
	}

	@Transactional
	public OrganizationUnitResponse setUnitActive(UUID id, boolean active) {
		OrganizationUnit unit = requireUnit(id);
		if (!active && hasActiveChildren(id)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Deactivate the subordinate units first; a unit with active children cannot be deactivated.");
		}
		unit.setActive(active);
		OrganizationUnit saved = this.units.save(unit);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "OrganizationUnit", id);
		return toResponse(saved, typeCodes(), 0);
	}

	/**
	 * Walks from the proposed parent up to the root, refusing if it reaches the unit being moved.
	 *
	 * <p>Bounded by a visited set so that a hierarchy already corrupted by an earlier bug terminates
	 * instead of looping forever — a defensive measure that turns a hang into a clear error.
	 */
	private void requireNoCycle(UUID movedUnitId, UUID proposedParentId) {
		Set<UUID> visited = new HashSet<>();
		UUID cursor = proposedParentId;
		while (cursor != null) {
			if (cursor.equals(movedUnitId)) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"That parent would create a cycle in the organization hierarchy.");
			}
			if (!visited.add(cursor)) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"The organization hierarchy already contains a cycle at " + cursor + ".");
			}
			cursor = this.units.findById(cursor).map(OrganizationUnit::getParentUnitId).orElse(null);
		}
	}

	private boolean hasActiveChildren(UUID unitId) {
		return this.units.findByParentUnitIdOrderByNameAsc(unitId).stream().anyMatch(OrganizationUnit::isActive);
	}

	// ------------------------------------------------------------------
	// Departments
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DepartmentResponse> listDepartments() {
		return toDepartmentResponses(this.departments.findByActiveTrueOrderByNameAsc());
	}

	@Transactional
	public DepartmentResponse createDepartment(SaveDepartmentRequest request) {
		String code = normaliseCode(request.code());
		if (this.departments.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A department with code " + code + " exists.");
		}
		UUID headUserId = request.headUserId();
		if (headUserId != null) {
			requireActiveUser(headUserId);
		}
		OrganizationUnit unit = createUnitFor(code, request.name(), request.parentUnitId(),
				OrganizationUnitType.DEPARTMENT);
		Department department = new Department();
		department.setOrganizationUnitId(unit.getId());
		department.setCode(code);
		department.setName(request.name().trim());
		department.setHeadUserId(headUserId);
		Department saved = this.departments.save(department);
		this.audit.record(AuditAction.CREATE, "Department", saved.getId());
		return toDepartmentResponse(saved);
	}

	@Transactional
	public DepartmentResponse updateDepartment(UUID id, SaveDepartmentRequest request) {
		Department department = requireDepartment(id);
		String code = normaliseCode(request.code());
		if (!code.equalsIgnoreCase(department.getCode()) && this.departments.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A department with code " + code + " exists.");
		}
		UUID headUserId = request.headUserId();
		if (headUserId != null) {
			requireActiveUser(headUserId);
		}
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("code", department.getCode());
		before.put("name", department.getName());
		department.setCode(code);
		department.setName(request.name().trim());
		department.setHeadUserId(headUserId);
		Department saved = this.departments.save(department);
		syncUnit(saved, request.parentUnitId());
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("code", saved.getCode());
		after.put("name", saved.getName());
		this.audit.recordChange(AuditAction.UPDATE, "Department", id, before, after);
		return toDepartmentResponse(saved);
	}

	@Transactional
	public DepartmentResponse setDepartmentActive(UUID id, boolean active) {
		Department department = requireDepartment(id);
		department.setActive(active);
		Department saved = this.departments.save(department);
		OrganizationUnit unit = this.units.findById(saved.getOrganizationUnitId()).orElse(null);
		if (unit != null) {
			unit.setActive(active);
			this.units.save(unit);
		}
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "Department", id);
		return toDepartmentResponse(saved);
	}

	private OrganizationUnit createUnitFor(String code, String name, UUID parentUnitId, String typeCode) {
		OrganizationUnitType type = this.unitTypes.findByCode(typeCode)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The " + typeCode + " unit type is not installed; run the catalogue migration."));
		OrganizationUnit unit = new OrganizationUnit();
		unit.setCode(code);
		unit.setName(name.trim());
		unit.setUnitTypeId(type.getId());
		unit.setParentUnitId(resolveParent(parentUnitId));
		return this.units.save(unit);
	}

	/**
	 * Attaches a new department/branch to the named parent, or to the root when none is given.
	 *
	 * <p>The root is looked up rather than invented: if a bank has not yet created a head office, the
	 * service refuses instead of creating one implicitly, because a silently created "Head Office" is
	 * exactly the kind of seeded fixture the requirements forbid.
	 */
	private UUID resolveParent(UUID parentUnitId) {
		if (parentUnitId != null) {
			requireUnit(parentUnitId);
			return parentUnitId;
		}
		List<OrganizationUnit> roots = this.units.findAll()
			.stream()
			.filter(unit -> unit.isRoot() && unit.isActive())
			.toList();
		if (roots.isEmpty()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"Create the top-level organization unit before adding departments or branches.");
		}
		if (roots.size() > 1) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"More than one top-level unit exists; specify the parent explicitly.");
		}
		return roots.get(0).getId();
	}

	private void syncUnit(Department saved, UUID parentUnitId) {
		this.units.findById(saved.getOrganizationUnitId()).ifPresent(unit -> {
			unit.setCode(saved.getCode());
			unit.setName(saved.getName());
			if (parentUnitId != null) {
				requireUnit(parentUnitId);
				requireNoCycle(unit.getId(), parentUnitId);
				unit.setParentUnitId(parentUnitId);
			}
			this.units.save(unit);
		});
	}

	// ------------------------------------------------------------------
	// Branches
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<BranchResponse> listBranches() {
		return this.branches.findByActiveTrueOrderByNameAsc().stream().map(this::toBranchResponse).toList();
	}

	@Transactional
	public BranchResponse createBranch(SaveBranchRequest request) {
		String code = normaliseCode(request.code());
		if (this.branches.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A branch with code " + code + " exists.");
		}
		OrganizationUnit unit = createUnitFor(code, request.name(), request.parentUnitId(),
				OrganizationUnitType.BRANCH);
		Branch branch = new Branch();
		branch.setOrganizationUnitId(unit.getId());
		branch.setCode(code);
		branch.setName(request.name().trim());
		branch.setAddress(request.address());
		Branch saved = this.branches.save(branch);
		this.audit.record(AuditAction.CREATE, "Branch", saved.getId());
		return toBranchResponse(saved);
	}

	@Transactional
	public BranchResponse updateBranch(UUID id, SaveBranchRequest request) {
		Branch branch = requireBranch(id);
		String code = normaliseCode(request.code());
		if (!code.equalsIgnoreCase(branch.getCode()) && this.branches.existsByCodeIgnoreCase(code)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A branch with code " + code + " exists.");
		}
		branch.setCode(code);
		branch.setName(request.name().trim());
		branch.setAddress(request.address());
		Branch saved = this.branches.save(branch);
		this.units.findById(saved.getOrganizationUnitId()).ifPresent(unit -> {
			unit.setCode(saved.getCode());
			unit.setName(saved.getName());
			if (request.parentUnitId() != null) {
				requireUnit(request.parentUnitId());
				requireNoCycle(unit.getId(), request.parentUnitId());
				unit.setParentUnitId(request.parentUnitId());
			}
			this.units.save(unit);
		});
		this.audit.record(AuditAction.UPDATE, "Branch", id);
		return toBranchResponse(saved);
	}

	@Transactional
	public BranchResponse setBranchActive(UUID id, boolean active) {
		Branch branch = requireBranch(id);
		branch.setActive(active);
		Branch saved = this.branches.save(branch);
		this.units.findById(saved.getOrganizationUnitId()).ifPresent(unit -> {
			unit.setActive(active);
			this.units.save(unit);
		});
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "Branch", id);
		return toBranchResponse(saved);
	}

	// ------------------------------------------------------------------
	// Lookups used by other modules
	// ------------------------------------------------------------------

	/** The department, or a not-found failure. Used by request creation and approval routing. */
	@Transactional(readOnly = true)
	public Department requireDepartment(UUID id) {
		return this.departments.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Department", id));
	}

	@Transactional(readOnly = true)
	public Optional<Department> findDepartment(UUID id) {
		return (id == null) ? Optional.empty() : this.departments.findById(id);
	}

	// ------------------------------------------------------------------
	// Mapping and helpers
	// ------------------------------------------------------------------

	private Map<UUID, String> typeCodes() {
		Map<UUID, String> codes = new HashMap<>();
		this.unitTypes.findAll().forEach(type -> codes.put(type.getId(), type.getCode()));
		return codes;
	}

	private OrganizationUnitResponse toResponse(OrganizationUnit unit, Map<UUID, String> typeCodes, int depth) {
		String typeName = this.unitTypes.findById(unit.getUnitTypeId()).map(OrganizationUnitType::getName).orElse("");
		return new OrganizationUnitResponse(unit.getId(), unit.getParentUnitId(), unit.getUnitTypeId(),
				typeCodes.getOrDefault(unit.getUnitTypeId(), ""), typeName, unit.getCode(), unit.getName(),
				unit.getDescription(), unit.isActive(), depth);
	}

	private DepartmentResponse toDepartmentResponse(Department department) {
		String headName = (department.getHeadUserId() == null) ? null
				: this.users.findById(department.getHeadUserId()).map(User::displayName).orElse(null);
		return new DepartmentResponse(department.getId(), department.getOrganizationUnitId(), department.getCode(),
				department.getName(), department.getHeadUserId(), headName, department.isActive());
	}

	/** Batch mapping so a list of departments costs one extra query for heads, not N. */
	private List<DepartmentResponse> toDepartmentResponses(List<Department> rows) {
		Set<UUID> headIds = new HashSet<>();
		rows.forEach(row -> {
			if (row.getHeadUserId() != null) {
				headIds.add(row.getHeadUserId());
			}
		});
		Map<UUID, String> names = new HashMap<>();
		if (!headIds.isEmpty()) {
			this.users.findAllById(headIds).forEach(user -> names.put(user.getId(), user.displayName()));
		}
		return rows.stream()
			.map(row -> new DepartmentResponse(row.getId(), row.getOrganizationUnitId(), row.getCode(), row.getName(),
					row.getHeadUserId(), row.getHeadUserId() == null ? null : names.get(row.getHeadUserId()),
					row.isActive()))
			.toList();
	}

	private BranchResponse toBranchResponse(Branch branch) {
		return new BranchResponse(branch.getId(), branch.getOrganizationUnitId(), branch.getCode(), branch.getName(),
				branch.getAddress(), branch.isActive());
	}

	private OrganizationUnit requireUnit(UUID id) {
		return this.units.findById(id).orElseThrow(() -> new ResourceNotFoundException("OrganizationUnit", id));
	}

	private Branch requireBranch(UUID id) {
		return this.branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Branch", id));
	}

	private void requireActiveUser(UUID userId) {
		User user = this.users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"That user is deactivated and cannot be assigned as a department head.");
		}
	}

	private static Map<String, Object> snapshot(OrganizationUnit unit) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("code", unit.getCode());
		snapshot.put("name", unit.getName());
		snapshot.put("parentUnitId", unit.getParentUnitId());
		return snapshot;
	}

	/** Codes are stored upper case so that a case change cannot create a second "HR". */
	static String normaliseCode(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A code is required.");
		}
		return raw.trim().toUpperCase(java.util.Locale.ROOT);
	}

}
