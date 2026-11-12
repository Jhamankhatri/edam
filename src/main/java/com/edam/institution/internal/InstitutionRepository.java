package com.edam.institution.internal;

import com.edam.institution.domain.Institution;
import com.edam.institution.domain.InstitutionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface InstitutionRepository extends JpaRepository<Institution, Long> {

    Optional<Institution> findByCodeIgnoreCaseAndStatus(String code, InstitutionStatus status);
}
