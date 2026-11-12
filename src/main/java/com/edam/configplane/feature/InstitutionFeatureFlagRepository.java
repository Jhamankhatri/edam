package com.edam.configplane.feature;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Feature-flag override persistence, tenant-scoped. */
public interface InstitutionFeatureFlagRepository extends JpaRepository<InstitutionFeatureFlag, Long> {

	List<InstitutionFeatureFlag> findByInstitutionId(Long institutionId);

	Optional<InstitutionFeatureFlag> findByInstitutionIdAndFeatureKey(Long institutionId, FeatureKey featureKey);

}
