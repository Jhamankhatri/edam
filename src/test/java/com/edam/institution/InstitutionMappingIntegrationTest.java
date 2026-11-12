package com.edam.institution;

import java.math.BigDecimal;
import java.util.List;

import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Flyway schema, the JPA mapping and Hibernate's
 * {@code ddl-auto: validate} agree, and that JPA auditing populates the audit columns.
 *
 * <p>This is the test that fails loudly if V1 and {@link Institution} drift apart — the
 * exact class of production incident the "no automatic schema generation" rule prevents.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstitutionMappingIntegrationTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private InstitutionRepository institutions;

	@Autowired
	private InstitutionService institutionService;

	@Test
	@DisplayName("Flyway seed data is readable and maps completely")
	void seedDataMaps() {
		InstitutionSummary institution = this.institutionService.requireActive("demo-bank");

		assertThat(institution.name()).isEqualTo("Demo Bank Limited");
		assertThat(institution.baseCurrency()).isEqualTo("PKR");
		assertThat(institution.languageTag()).isEqualTo("en-PK");
		assertThat(institution.timeZone()).isEqualTo("Asia/Karachi");
	}

	@Test
	@DisplayName("JPA auditing stamps the actor and timestamps on insert and update")
	void auditingPopulatesStamps() {
		Institution institution = new Institution();
		institution.setCode("audit-bank");
		institution.setName("Audit Bank");
		institution.setShortName("AuditBank");
		institution.setBaseCurrency("PKR");
		institution.setDepartmentHeadLimit(new BigDecimal("15000.00"));

		Institution saved = this.institutions.saveAndFlush(institution);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(saved.getCreatedBy()).isEqualTo("system");
		assertThat(saved.getUpdatedBy()).isEqualTo("system");
		assertThat(saved.getVersion()).isZero();
	}

	@Test
	@DisplayName("the code column is unique at the database level")
	void codeIsUnique() {
		Institution duplicate = new Institution();
		duplicate.setCode("demo-bank");
		duplicate.setName("Duplicate Bank");
		duplicate.setShortName("Dup");
		duplicate.setBaseCurrency("PKR");

		assertThat(this.institutions.existsByCode("demo-bank")).isTrue();
		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> this.institutions.saveAndFlush(duplicate))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("inactive institutions are not resolvable as active")
	void inactiveIsNotResolvable() {
		Institution institution = new Institution();
		institution.setCode("retired-bank");
		institution.setName("Retired Bank");
		institution.setShortName("Retired");
		institution.setBaseCurrency("PKR");
		institution.setActive(false);
		this.institutions.saveAndFlush(institution);

		assertThat(this.institutionService.findActive("retired-bank")).isEmpty();
		assertThat(this.institutionService.listActive())
			.extracting(InstitutionSummary::code)
			.doesNotContain("retired-bank");
	}

	@Test
	@DisplayName("unknown institution codes raise a not-found business exception")
	void unknownCodeIsNotFound() {
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> this.institutionService.requireActive("no-such-bank"))
			.isInstanceOf(InstitutionNotFoundException.class);
	}

	@Test
	@DisplayName("the repository exposes all seeded institutions")
	void listsSeededInstitutions() {
		List<Institution> all = this.institutions.findAll();

		assertThat(all).extracting(Institution::getCode).contains("demo-bank");
	}

}
