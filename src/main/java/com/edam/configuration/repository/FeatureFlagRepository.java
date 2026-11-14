package com.edam.configuration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.configuration.model.FeatureFlag;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
	Optional<FeatureFlag> findByFeatureKey(String key);
	List<FeatureFlag> findAllByOrderByFeatureKeyAsc();
	boolean existsByFeatureKey(String key);
}
