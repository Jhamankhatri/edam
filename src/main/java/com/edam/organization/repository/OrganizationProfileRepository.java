package com.edam.organization.repository;

import java.util.Optional;
import java.util.UUID;

import com.edam.organization.model.OrganizationProfile;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The singleton organization profile (Module A).
 *
 * <p>{@link #findFirstByActiveTrue()} is the only reader: there is no "list all profiles" method to
 * accidentally call, which makes it impossible for a caller to iterate over banks that do not exist.
 */
public interface OrganizationProfileRepository extends JpaRepository<OrganizationProfile, UUID> {

	Optional<OrganizationProfile> findFirstByActiveTrue();

}
