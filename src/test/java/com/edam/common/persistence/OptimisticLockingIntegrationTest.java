package com.edam.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.edam.approvallimits.model.DepartmentApprovalLimit;
import com.edam.approvallimits.repository.DepartmentApprovalLimitRepository;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.organization.model.Department;
import com.edam.organization.model.OrganizationProfile;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.organization.repository.OrganizationProfileRepository;
import com.edam.organization.repository.OrganizationUnitRepository;
import com.edam.organization.repository.OrganizationUnitTypeRepository;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;
import com.edam.testsupport.RequestFixtures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves that {@code @Version} actually prevents a lost update, and that the failure is the one the API
 * documents.
 *
 * <h2>Why this needs two real transactions</h2>
 * A single-threaded test cannot produce an optimistic-lock conflict at all: it would read, write and commit
 * in one transaction and always succeed. The only way to prove the mechanism is to have two transactions
 * read the <em>same</em> version and then both try to write. If {@code @Version} were missing from an
 * entity, this test would fail with a lost update rather than a conflict — which is exactly the bug the
 * check exists to catch.
 *
 * <h2>Why a barrier rather than a sleep</h2>
 * Both threads read before either writes. A timed sleep makes that likely; a barrier makes it certain, so
 * the test is neither flaky nor slow. The threads are started, both reach the barrier, and only then does
 * either attempt its update.
 *
 * <h2>What is asserted</h2>
 * <ol>
 *   <li>Exactly one writer succeeds.</li>
 *   <li>The other fails with {@link OptimisticLockingFailureException}, which
 *       {@code GlobalExceptionHandler} maps to HTTP 409 {@code EDAM-3002}. The mapping itself is covered by
 *       {@code GlobalExceptionHandlerTest}; this test proves the exception that reaches it really occurs.</li>
 *   <li>The stored row carries the winner's value, not a blend of both.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Optimistic locking")
class OptimisticLockingIntegrationTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private DepartmentApprovalLimitRepository limits;

	@Autowired
	private DepartmentRepository departments;

	@Autowired
	private OrganizationUnitRepository units;

	@Autowired
	private OrganizationUnitTypeRepository unitTypes;

	@Autowired
	private OrganizationProfileRepository profiles;

	@Autowired
	private CurrencyRepository currencies;

	@Autowired
	private TransactionTemplate transactionTemplate;

	/** A template that commits independently of whatever transaction is currently in progress. */
	private TransactionTemplate requiredNew;

	@BeforeEach
	void configureRequiredNewTemplate() {
		this.requiredNew = new TransactionTemplate(
				java.util.Objects.requireNonNull(this.transactionTemplate.getTransactionManager()));
		this.requiredNew.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	private Department department;

	private Currency currency;

	@BeforeEach
	void setUp() {
		OrganizationProfile profile = RequestFixtures.organizationProfile(this.profiles, this.currencies);
		this.currency = this.currencies.findById(profile.getDefaultCurrencyId()).orElseThrow();
		this.department = RequestFixtures.department(this.departments, this.units, this.unitTypes);
	}

	/**
	 * Two concurrent updaters of one row: one commits, the other is told the record changed.
	 *
	 * <p>The fixture is a department approval limit because it is a real financial configuration row with
	 * {@code @Version} inherited from {@code AuditableEntity}; the mechanism under test is the same one every
	 * auditable entity uses.
	 */
	@Test
	@DisplayName("two concurrent updates of one row produce exactly one conflict")
	void concurrentUpdateConflicts() throws Exception {
		UUID id = this.transactionTemplate.execute(status -> this.limits
			.save(new DepartmentApprovalLimit(this.department.getId(), null,
					DepartmentApprovalLimit.ExpenseType.OPEX, this.currency.getId(),
					new BigDecimal("1000.0000"), Instant.now().minusSeconds(60), null))
			.getId());
		assertThat(id).isNotNull();

		int writers = 2;
		CyclicBarrier afterRead = new CyclicBarrier(writers);
		ExecutorService pool = Executors.newFixedThreadPool(writers);
		try {
			Callable<String> first = writer(id, new BigDecimal("2000.0000"), afterRead);
			Callable<String> second = writer(id, new BigDecimal("3000.0000"), afterRead);
			Future<String> one = pool.submit(first);
			Future<String> two = pool.submit(second);

			String outcomeOne = one.get(30, TimeUnit.SECONDS);
			String outcomeTwo = two.get(30, TimeUnit.SECONDS);

			long committed = java.util.stream.Stream.of(outcomeOne, outcomeTwo).filter("COMMITTED"::equals).count();
			long conflicted = java.util.stream.Stream.of(outcomeOne, outcomeTwo)
				.filter("CONFLICT"::equals)
				.count();
			assertThat(committed).as("exactly one writer must commit; outcomes were %s and %s", outcomeOne,
					outcomeTwo).isEqualTo(1);
			assertThat(conflicted).as("the other must be refused as a stale write, not silently applied")
				.isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}

		// The stored row is one writer's value; a lost update would have made it the other's, and a blend
		// would be a value neither asked for.
		BigDecimal stored = this.transactionTemplate
			.execute(status -> this.limits.findById(id).orElseThrow().getApprovalLimit());
		assertThat(stored).isIn(new BigDecimal("2000.0000"), new BigDecimal("3000.0000"));
	}

	/**
	 * One writer: read the row inside a transaction, wait until the other thread has read it too, then update.
	 *
	 * <p>The read and the write deliberately share a transaction, because that is the situation
	 * optimistic locking addresses — a long-lived edit that began from a snapshot which has since moved on.
	 */
	private Callable<String> writer(UUID id, BigDecimal newAmount, CyclicBarrier afterRead) {
		return () -> {
			try {
				return this.transactionTemplate.execute(status -> {
					try {
						DepartmentApprovalLimit row = this.limits.findById(id).orElseThrow();
						// Both writers must have read the same version before either writes. One barrier is
						// enough: it releases only when the second reader arrives, so by then both hold the
						// snapshot the update will be compared against.
						afterRead.await(10, TimeUnit.SECONDS);
						row.setLimit(newAmount);
						this.limits.saveAndFlush(row);
						return "COMMITTED";
					}
					catch (OptimisticLockingFailureException ex) {
						status.setRollbackOnly();
						return "CONFLICT";
					}
					catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						status.setRollbackOnly();
						return "INTERRUPTED";
					}
					catch (java.util.concurrent.BrokenBarrierException ex) {
						status.setRollbackOnly();
						return "BROKEN";
					}
					catch (java.util.concurrent.TimeoutException ex) {
						// The other writer never arrived, so this one must not proceed on a half-synchronised
						// read; rolling back keeps the conflict assertion meaningful.
						status.setRollbackOnly();
						return "TIMED_OUT";
					}
				});
			}
			catch (OptimisticLockingFailureException ex) {
				// The failure can surface when the transaction commits rather than at the flush.
				return "CONFLICT";
			}
		};
	}

	/**
	 * A stale write is refused even without concurrency: read, let another transaction change and commit the
	 * row, then attempt the first transaction's write. This is the same defect without the timing, and it
	 * fails deterministically.
	 */
	@Test
	@DisplayName("a write based on an outdated read is refused")
	void staleReadWriteRefused() {
		UUID id = this.transactionTemplate.execute(status -> this.limits
			.save(new DepartmentApprovalLimit(this.department.getId(), null,
					DepartmentApprovalLimit.ExpenseType.CAPEX, this.currency.getId(),
					new BigDecimal("5000.0000"), Instant.now().minusSeconds(60), null))
			.getId());

		assertThatThrownBy(() -> this.transactionTemplate.execute(status -> {
			DepartmentApprovalLimit stale = this.limits.findById(id).orElseThrow();
			// Another actor's committed change, made while our transaction still holds the old version.
			// This must be REQUIRES_NEW: with the default propagation it would join the outer transaction,
			// the "other" write would never commit independently, and no conflict would ever arise.
			this.requiredNew.execute(inner -> {
				DepartmentApprovalLimit fresh = this.limits.findById(id).orElseThrow();
				fresh.setLimit(new BigDecimal("6000.0000"));
				return this.limits.saveAndFlush(fresh);
			});
			stale.setLimit(new BigDecimal("7000.0000"));
			return this.limits.saveAndFlush(stale);
		})).isInstanceOf(OptimisticLockingFailureException.class);

		BigDecimal stored = this.transactionTemplate
			.execute(status -> this.limits.findById(id).orElseThrow().getApprovalLimit());
		assertThat(stored).isEqualByComparingTo("6000.0000");
	}

}
