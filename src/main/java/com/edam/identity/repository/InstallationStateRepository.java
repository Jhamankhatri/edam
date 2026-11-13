package com.edam.identity.repository;

import com.edam.identity.model.InstallationState;

import org.springframework.data.jpa.repository.JpaRepository;

/** The single installation-state row. */
public interface InstallationStateRepository extends JpaRepository<InstallationState, Short> {

}
