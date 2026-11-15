package com.edam.configuration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.configuration.model.BrandingTheme;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandingThemeRepository extends JpaRepository<BrandingTheme, UUID> {
	Optional<BrandingTheme> findByThemeName(String name);
	Optional<BrandingTheme> findFirstByDefaultThemeTrueAndActiveTrue();
	List<BrandingTheme> findByActiveTrueOrderByThemeNameAsc();
	List<BrandingTheme> findAllByOrderByThemeNameAsc();
	boolean existsByThemeNameAndIdNot(String name, UUID id);
}
