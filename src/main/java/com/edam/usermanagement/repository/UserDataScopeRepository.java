package com.edam.usermanagement.repository;

import java.util.List;
import java.util.UUID;

import com.edam.usermanagement.model.UserDataScope;

import org.springframework.data.jpa.repository.JpaRepository;

/** Organizational data scopes (Module C). */
public interface UserDataScopeRepository extends JpaRepository<UserDataScope, UUID> {

	List<UserDataScope> findByUserId(UUID userId);

	void deleteByUserId(UUID userId);

}
