package com.edam.institution.internal;

import com.edam.institution.domain.InstitutionBranding;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface InstitutionBrandingRepository extends JpaRepository<InstitutionBranding, Long> {

    Optional<InstitutionBranding> findByInstitutionId(Long institutionId);
}
