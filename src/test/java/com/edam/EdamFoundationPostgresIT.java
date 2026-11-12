package com.edam;

import static org.assertj.core.api.Assertions.assertThat;

import com.edam.common.audit.AuditEventCommand;
import com.edam.common.audit.AuditEventPublisher;
import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.security.EdamUserPrincipal;
import com.edam.identity.domain.MembershipStatus;
import com.edam.identity.domain.Permission;
import com.edam.identity.domain.Role;
import com.edam.identity.domain.UserAccount;
import com.edam.identity.domain.UserInstitutionMembership;
import com.edam.identity.domain.UserStatus;
import com.edam.institution.api.InstitutionBrandingService;
import com.edam.institution.api.InstitutionBrandingView;
import com.edam.institution.domain.Institution;
import com.edam.institution.domain.InstitutionBranding;
import com.edam.institution.domain.InstitutionStatus;
import com.edam.institution.domain.ThemeMode;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Full-stack integration verification against real PostgreSQL + Flyway + Embedded Tomcat + Jasper JSPs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class EdamFoundationPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InstitutionBrandingService brandingService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AuditEventPublisher auditEventPublisher;

    @Test
    void flywayMigrationsAndPermissionsSeededSuccessfully() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM permissions",
                Integer.class);

        assertThat(migrationCount).isNotNull().isGreaterThanOrEqualTo(2);
        assertThat(permissionCount).isNotNull().isGreaterThanOrEqualTo(6);
    }

    @Test
    void rendersLoginJspDynamicThemeCssAndHealthEndpointOverHttp() {
        ResponseEntity<String> loginResponse = restTemplate.getForEntity("/login", String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody())
                .contains("Sign In")
                .contains("/assets/css/edam.css")
                .contains("/assets/theme/institution-theme.css")
                .contains("name=\"_csrf\"");

        ResponseEntity<String> themeResponse = restTemplate.getForEntity(
                "/assets/theme/institution-theme.css",
                String.class);
        assertThat(themeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(themeResponse.getBody()).contains("--edam-primary:");

        ResponseEntity<Map> healthResponse = restTemplate.getForEntity("/api/v1/health", Map.class);
        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(healthResponse.getHeaders().getFirst(CorrelationIdProvider.CORRELATION_ID_HEADER))
                .isNotBlank();
        assertThat(healthResponse.getBody()).containsEntry("success", true);
    }

    @Test
    void persistsMultiInstitutionUserMembershipsAndAuditEventsWithJpaAuditing() {
        Long instAId = transactionTemplate.execute(status -> {
            Institution instA = new Institution(
                    "IT_INST_A",
                    "Integration Bank A",
                    "Bank A",
                    "Integration Bank A Ltd",
                    InstitutionStatus.ACTIVE,
                    "PKR",
                    "en-PK",
                    "Asia/Karachi");
            entityManager.persist(instA);

            Institution instB = new Institution(
                    "IT_INST_B",
                    "Integration Bank B",
                    "Bank B",
                    "Integration Bank B Ltd",
                    InstitutionStatus.ACTIVE,
                    "USD",
                    "en-US",
                    "UTC");
            entityManager.persist(instB);

            InstitutionBranding brandingA = new InstitutionBranding(
                    instA,
                    "Bank A Expense Hub",
                    "/assets/images/edam-logo.svg",
                    "/assets/images/edam-logo.svg",
                    "#123456",
                    "#234567",
                    "#0F766E",
                    ThemeMode.LIGHT,
                    "Bank A Footer");
            entityManager.persist(brandingA);

            Permission permission = entityManager
                    .createQuery("SELECT p FROM Permission p WHERE p.code = :code", Permission.class)
                    .setParameter("code", "INSTITUTION_CONFIG_VIEW")
                    .getSingleResult();

            Role roleA = new Role(instA, "IT_VIEWER", "IT Viewer", "Integration role", true);
            roleA.addPermission(permission);
            entityManager.persist(roleA);

            UserAccount user = new UserAccount(
                    "it.multiuser",
                    "it.multiuser@example.org",
                    "Multi User",
                    "$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012",
                    "LOCAL",
                    UserStatus.ACTIVE);
            entityManager.persist(user);

            UserInstitutionMembership mA = new UserInstitutionMembership(
                    user, instA, "E100", "FIN", true, MembershipStatus.ACTIVE);
            mA.assignRole(roleA);
            entityManager.persist(mA);

            UserInstitutionMembership mB = new UserInstitutionMembership(
                    user, instB, "E200", "OPS", false, MembershipStatus.ACTIVE);
            entityManager.persist(mB);

            entityManager.flush();
            assertThat(instA.getCreatedAt()).isNotNull();
            assertThat(instA.getCreatedBy()).isEqualTo("SYSTEM");
            return instA.getId();
        });

        InstitutionBrandingView brandingView = brandingService.getBrandingByCode("IT_INST_A");
        assertThat(brandingView.primaryColor()).isEqualTo("#123456");
        assertThat(brandingView.defaultCurrencyCode()).isEqualTo("PKR");

        EdamUserPrincipal principal = (EdamUserPrincipal) userDetailsService.loadUserByUsername("it.multiuser");
        assertThat(principal.getAccessibleInstitutionCodes())
                .containsExactlyInAnyOrder("IT_INST_A", "IT_INST_B");
        assertThat(principal.getPermissionsForInstitution("IT_INST_A"))
                .containsExactly("INSTITUTION_CONFIG_VIEW");
        assertThat(principal.getPermissionsForInstitution("IT_INST_B")).isEmpty();

        auditEventPublisher.publish(new AuditEventCommand(
                instAId,
                "it-corr-1",
                "SYSTEM",
                null,
                "FOUNDATION_IT_CHECK",
                "FOUNDATION",
                "INSTITUTION",
                String.valueOf(instAId),
                "Verified audit persistence in PostgreSQL",
                null,
                "{\"status\":\"verified\"}",
                "127.0.0.1"));

        Integer auditRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE correlation_id = 'it-corr-1'",
                Integer.class);
        assertThat(auditRows).isEqualTo(1);
    }
}
