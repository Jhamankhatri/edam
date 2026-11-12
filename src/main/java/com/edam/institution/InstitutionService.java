package com.edam.institution;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public read access to institution configuration.
 *
 * <p>This is the module's boundary: other modules depend on
 * {@link com.edam.institution.InstitutionSummary}, never on the entity or repository.
 */
@Service
@Transactional(readOnly = true)
public class InstitutionService {

	private final InstitutionRepository institutions;

	public InstitutionService(InstitutionRepository institutions) {
		this.institutions = institutions;
	}

	public Optional<InstitutionSummary> findActive(String code) {
		return this.institutions.findByCode(code).filter(Institution::isActive).map(InstitutionService::summarize);
	}

	public InstitutionSummary requireActive(String code) {
		return findActive(code).orElseThrow(() -> new InstitutionNotFoundException(code));
	}

	public List<InstitutionSummary> listActive() {
		return this.institutions.findAll()
			.stream()
			.filter(Institution::isActive)
			.map(InstitutionService::summarize)
			.toList();
	}

	private static InstitutionSummary summarize(Institution institution) {
		return new InstitutionSummary(institution.getId(), institution.getCode(), institution.getName(),
				institution.getShortName(), institution.getBaseCurrency(), institution.getPrimaryColor(),
				institution.getSecondaryColor(), institution.getAccentColor(), institution.getLanguageTag(),
				institution.getTimeZone(), institution.isActive());
	}

}
