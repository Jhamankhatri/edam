package com.edam.configuration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.configuration.model.ApplicationSetting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, UUID> {
	Optional<ApplicationSetting> findByKey(String key);
	List<ApplicationSetting> findByActiveTrueOrderByCategoryAscKeyAsc();
	List<ApplicationSetting> findByCategoryAndActiveTrueOrderByKeyAsc(String category);
	List<ApplicationSetting> findByCategoryInAndActiveTrueOrderByCategoryAscKeyAsc(List<String> categories);
	boolean existsByKey(String key);
}
