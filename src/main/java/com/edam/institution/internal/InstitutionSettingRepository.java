package com.edam.institution.internal;

import com.edam.institution.domain.InstitutionSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface InstitutionSettingRepository extends JpaRepository<InstitutionSetting, Long> {

    Optional<InstitutionSetting> findByInstitutionIdAndSettingKey(Long institutionId, String settingKey);
}
