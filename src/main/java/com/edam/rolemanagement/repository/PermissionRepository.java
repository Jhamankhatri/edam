package com.edam.rolemanagement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.rolemanagement.model.Permission;

import org.springframework.data.jpa.repository.JpaRepository;

/** Permission catalogue (Module C). Read-mostly: the catalogue is installed by migration. */
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

	Optional<Permission> findByCode(String code);

	List<Permission> findAllByOrderByModuleAscCodeAsc();

	List<Permission> findByModuleOrderByCodeAsc(String module);

}
