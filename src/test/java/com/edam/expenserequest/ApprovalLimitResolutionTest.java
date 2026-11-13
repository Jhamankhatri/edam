package com.edam.expenserequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.approvallimits.dto.ApprovalLimitDtos.EffectiveLimit;
import com.edam.approvallimits.model.DepartmentApprovalLimit;
import com.edam.approvallimits.repository.DepartmentApprovalLimitRepository;
import com.edam.approvallimits.service.ApprovalLimitService;
import com.edam.common.audit.AuditRecorder;
import com.edam.masterdata.service.CurrencyService;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.usermanagement.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The approval-limit resolution rules (OD-P2-04).
 *
 * <h2>What is being proved</h2>
 * Two rules, both governance decisions rather than arithmetic:
 * <ol>
 *   <li>the most specific applicable row wins;</li>
 *   <li>a personal limit may tighten a person's authority but never widen it — the effective ceiling is the
 *       lower of the personal and departmental figures.</li>
 * </ol>
 * The second rule is the one a bank would notice if it were inverted: a manager could approve more than
 * their department head. It is asserted directly rather than inferred from a larger scenario.
 *
 * <h2>Why the repositories are mocked</h2>
 * The decision is a pure function of the rows and the instant. Mocking the repository isolates exactly that
 * function; a database test would additionally cover the query, and does so separately in the integration
 * suite.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalLimitResolutionTest {

	private static final UUID DEPARTMENT = UUID.randomUUID();

	private static final UUID PERSON = UUID.randomUUID();

	private static final UUID CURRENCY = UUID.randomUUID();

	private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");

	@Mock
	private DepartmentApprovalLimitRepository limits;

	@Mock
	private DepartmentRepository departments;

	@Mock
	private UserRepository users;

	@Mock
	private CurrencyService currencyService;

	@Mock
	private AuditRecorder audit;

	private ApprovalLimitService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		this.service = new ApprovalLimitService(this.limits, this.departments, this.users, this.currencyService,
				this.audit, clock);
	}

	// ---------------------------------------------------------------------------------------------
	// Fixtures
	// ---------------------------------------------------------------------------------------------

	/** A limit row. {@code userId} null means department-wide. */
	private static DepartmentApprovalLimit limit(UUID userId, String amount, DepartmentApprovalLimit.ExpenseType type) {
		return new DepartmentApprovalLimit(DEPARTMENT, userId, type, CURRENCY, new BigDecimal(amount),
				NOW.minusSeconds(86400), null);
	}

	private void givenRows(DepartmentApprovalLimit... rows) {
		when(this.limits.findActiveForDepartmentAndCurrency(eq(DEPARTMENT), eq(CURRENCY)))
			.thenReturn(List.of(rows));
	}

	private Optional<EffectiveLimit> resolve(String classification) {
		return this.service.effectiveLimit(DEPARTMENT, PERSON, classification, CURRENCY, NOW);
	}

	// ---------------------------------------------------------------------------------------------
	// Resolution
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("effective ceiling")
	class EffectiveCeiling {

		@Test
		@DisplayName("a department-wide limit applies when there is no personal one")
		void departmentWideApplies() {
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			Optional<EffectiveLimit> resolved = resolve("OPEX");
			assertThat(resolved).isPresent();
			assertThat(resolved.get().limit()).isEqualByComparingTo("100000.0000");
			assertThat(resolved.get().basis()).isEqualTo("DEPARTMENT");
		}

		@Test
		@DisplayName("a personal limit lower than the department limit binds")
		void personalLowerBinds() {
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH),
					limit(PERSON, "50000.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			Optional<EffectiveLimit> resolved = resolve("OPEX");
			assertThat(resolved).isPresent();
			assertThat(resolved.get().limit()).isEqualByComparingTo("50000.0000");
			assertThat(resolved.get().basis()).isEqualTo("PERSONAL");
		}

		@Test
		@DisplayName("a personal limit higher than the department limit does NOT widen authority")
		void personalHigherDoesNotWiden() {
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH),
					limit(PERSON, "500000.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			Optional<EffectiveLimit> resolved = resolve("OPEX");
			assertThat(resolved).isPresent();
			// The governance rule: the department ceiling still binds.
			assertThat(resolved.get().limit()).isEqualByComparingTo("100000.0000");
			assertThat(resolved.get().basis()).isEqualTo("DEPARTMENT");
		}

		@Test
		@DisplayName("equal personal and departmental limits resolve deterministically")
		void equalLimitsResolve() {
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH),
					limit(PERSON, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			Optional<EffectiveLimit> resolved = resolve("OPEX");
			assertThat(resolved).isPresent();
			assertThat(resolved.get().limit()).isEqualByComparingTo("100000.0000");
		}

		@Test
		@DisplayName("no configured limit yields empty, never an unlimited authority")
		void noLimitIsNotUnlimited() {
			givenRows();
			assertThat(resolve("OPEX")).isEmpty();
		}

		@Test
		@DisplayName("another person's personal limit is ignored")
		void otherPersonsLimitIgnored() {
			givenRows(limit(UUID.randomUUID(), "1.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			// Only someone else's personal limit exists, so nothing applies to this person.
			assertThat(resolve("OPEX")).isEmpty();
		}

		@Test
		@DisplayName("an OPEX-only limit does not govern CAPEX")
		void classificationIsRespected() {
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.OPEX));
			assertThat(resolve("OPEX")).isPresent();
			assertThat(resolve("CAPEX")).isEmpty();
		}

		@Test
		@DisplayName("a BOTH limit governs both classifications")
		void bothGovernsBoth() {
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			assertThat(resolve("OPEX")).isPresent();
			assertThat(resolve("CAPEX")).isPresent();
		}

		@Test
		@DisplayName("a closed period is not applied")
		void closedPeriodIgnored() {
			DepartmentApprovalLimit closed = new DepartmentApprovalLimit(DEPARTMENT, null,
					DepartmentApprovalLimit.ExpenseType.BOTH, CURRENCY, new BigDecimal("100000.0000"),
					NOW.minusSeconds(86400), NOW);
			givenRows(closed);
			// effective_to is exclusive-style: a period ending exactly now no longer covers now.
			assertThat(resolve("OPEX")).isEmpty();
		}

		@Test
		@DisplayName("a limit starting in the future is not applied yet")
		void futureLimitIgnored() {
			DepartmentApprovalLimit future = new DepartmentApprovalLimit(DEPARTMENT, null,
					DepartmentApprovalLimit.ExpenseType.BOTH, CURRENCY, new BigDecimal("100000.0000"),
					NOW.plusSeconds(3600), null);
			givenRows(future);
			assertThat(resolve("OPEX")).isEmpty();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Authority check
	// ---------------------------------------------------------------------------------------------

	@Nested
	@DisplayName("authority check")
	class AuthorityCheck {

		@Test
		@DisplayName("a person within their limit is permitted")
		void withinLimitPermitted() {
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			when(this.users.findById(any())).thenReturn(Optional.empty());
			var check = this.service.checkAuthority(DEPARTMENT, "OPEX", CURRENCY, new BigDecimal("99999.0000"),
					List.of(PERSON));
			assertThat(check.permittedUserIds()).containsExactly(PERSON);
		}

		@Test
		@DisplayName("an amount equal to the limit is permitted: the ceiling is inclusive")
		void atLimitPermitted() {
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			var check = this.service.checkAuthority(DEPARTMENT, "OPEX", CURRENCY, new BigDecimal("100000.0000"),
					List.of(PERSON));
			assertThat(check.permittedUserIds()).containsExactly(PERSON);
		}

		@Test
		@DisplayName("one cent above the limit is refused")
		void aboveLimitRefused() {
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			var check = this.service.checkAuthority(DEPARTMENT, "OPEX", CURRENCY, new BigDecimal("100000.0001"),
					List.of(PERSON));
			assertThat(check.permittedUserIds()).isEmpty();
			assertThat(check.reasons()).isNotEmpty();
		}

		@Test
		@DisplayName("an unconfigured department refuses and says why")
		void unconfiguredRefused() {
			givenRows();
			var check = this.service.checkAuthority(DEPARTMENT, "OPEX", CURRENCY, new BigDecimal("1.0000"),
					List.of(PERSON));
			assertThat(check.permittedUserIds()).isEmpty();
			assertThat(check.reasons()).anyMatch(reason -> reason.contains("No approval limit is configured"));
		}

		@Test
		@DisplayName("only the candidates with sufficient limits are returned")
		void onlyAuthorisedCandidatesReturned() {
			UUID junior = UUID.randomUUID();
			givenRows(limit(null, "100000.0000", DepartmentApprovalLimit.ExpenseType.BOTH),
					limit(junior, "1000.0000", DepartmentApprovalLimit.ExpenseType.BOTH));
			var check = this.service.checkAuthority(DEPARTMENT, "OPEX", CURRENCY, new BigDecimal("5000.0000"),
					List.of(PERSON, junior));
			assertThat(check.permittedUserIds()).containsExactly(PERSON);
		}
	}

}
