package com.edam.communication;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.communication.dto.EventConfigResponse;
import com.edam.communication.dto.SmtpConfigurationRequest;
import com.edam.communication.dto.SmtpConfigurationResponse;
import com.edam.communication.dto.TemplateResponse;
import com.edam.communication.dto.TemplateUpsertRequest;
import com.edam.communication.model.CommunicationEvent;
import com.edam.communication.repository.NotificationOutboxRepository;
import com.edam.communication.service.EmailTemplateService;
import com.edam.communication.service.NotificationDispatcher;
import com.edam.communication.service.NotificationEventService;
import com.edam.communication.service.SmtpConfigurationService;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommunicationModuleIntegrationTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired private SmtpConfigurationService smtp;
	@Autowired private EmailTemplateService templates;
	@Autowired private NotificationEventService events;
	@Autowired private NotificationDispatcher dispatcher;
	@Autowired private NotificationOutboxRepository outbox;
	@Autowired private UserRepository users;

	@BeforeEach
	void auth() {
		// Seed a system admin user if none; reuse or create admin via existing dev-users mechanism.
		User admin = users.findAll().stream().findFirst().orElseGet(() -> {
			User u = new User();
			u.setUsername("admin-test");
			u.setFirstName("Admin");
			u.setLastName("Test");
			u.setEmail("admin@example.com");
			u.setActive(true);
			return users.save(u);
		});
		EdamPrincipal p = new EdamPrincipal(admin.getId(), null, admin.getUsername(), admin.displayName(),
				Set.of("ADMIN"),
				Set.of(Permissions.EMAIL_CONFIGURATION_MANAGE, Permissions.NOTIFICATION_TEMPLATE_MANAGE,
						Permissions.NOTIFICATION_VIEW, Permissions.USER_VIEW, Permissions.USER_MANAGE,
						Permissions.CONFIGURATION_MANAGE, Permissions.SYSTEM_ADMINISTER),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
	}

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void smtpSaveAndResponseMasksPassword() {
		SmtpConfigurationRequest req = new SmtpConfigurationRequest(
				"smtp.example.com", 587, "testuser", "s3cr3t", "STARTTLS",
				"no-reply@example.com", "EDAM", null,
				null, null, true);
		SmtpConfigurationResponse saved = smtp.save(req);
		assertThat(saved.id()).isNotNull();
		assertThat(saved.passwordSet()).isTrue();
		assertThat(saved.active()).isTrue();

		var viewed = smtp.getActive().orElseThrow();
		assertThat(viewed.host()).isEqualTo("smtp.example.com");
		assertThat(viewed.passwordSet()).isTrue();
		// ciphertext must never be served as username/host etc.
		assertThat(viewed.username()).isEqualTo("testuser");
	}

	@Test
	void templateCreateAndVersionBumps() {
		var req = new TemplateUpsertRequest("EXP_APPROVED_MAIL", "Expense Approved",
				"Sent when expense is approved", CommunicationEvent.EXPENSE_APPROVED.name(),
				"Approved: {{requestId}}", "<p>Your request {{requestId}} is approved.</p>",
				"Approved: {{requestId}}", "en", true,
				Set.of("requestId", "requester"), "initial");
		TemplateResponse t = templates.create(req);
		assertThat(t.currentVersion()).isEqualTo(1);
		assertThat(t.active()).isTrue();
		assertThat(t.allowedPlaceholders()).contains("requestId");

		var v2req = new TemplateUpsertRequest(t.code(), t.displayName(), t.description(), t.eventCode(),
				"Approved v2: {{requestId}}", "<p>Approved (v2): {{requestId}}</p>", "Approved: {{requestId}}",
				"en", true, Set.of("requestId", "requester"), "new content");
		var v2 = templates.createVersion(t.id(), v2req);
		assertThat(v2.versionNumber()).isEqualTo(2);
	}

	@Test
	void eventCatalogIsSeeded() {
		List<EventConfigResponse> list = events.list();
		assertThat(list).isNotEmpty();
		assertThat(list.stream().anyMatch(e -> e.eventCode().equals(CommunicationEvent.EXPENSE_SUBMITTED.name())))
				.isTrue();
	}

	@Test
	void dispatcherEnqueuesWhenSmtpConfigured() {
		// Configure SMTP
		SmtpConfigurationRequest req = new SmtpConfigurationRequest(
				"smtp.example.com", 587, "u", "p", "STARTTLS",
				"no-reply@example.com", "EDAM", null, null, null, true);
		smtp.save(req);

		// Create a template for EXPENSE_APPROVED
		var req2 = new TemplateUpsertRequest("EXP_APPROVED", "Approved", "",
				CommunicationEvent.EXPENSE_APPROVED.name(),
				"Approved {{requestId}}", "<p>Approved {{requestId}}</p>",
				"Approved {{requestId}}", "en", true, Set.of("requestId"), "");
		TemplateResponse t = templates.create(req2);

		// Route event to that template
		events.update(CommunicationEvent.EXPENSE_APPROVED.name(),
				new com.edam.communication.dto.EventConfigRequest(true, true, t.id(), Set.of("REQUESTER"), true));

		User recipient = users.findAll().stream().findFirst().orElseThrow();
		long before = outbox.count();
		dispatcher.dispatch(CommunicationEvent.EXPENSE_APPROVED, recipient.getId(),
				Map.of("requestId", "R-1"), "Request approved", "Body", null, UUID.randomUUID());
		long after = outbox.count();
		assertThat(after).isGreaterThanOrEqualTo(before + 2); // email + in-app
	}
}
