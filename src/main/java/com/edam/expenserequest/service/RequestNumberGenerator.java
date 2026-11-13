package com.edam.expenserequest.service;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;

/**
 * Allocates human-readable request numbers (Module G).
 *
 * <h2>Format</h2>
 * {@code <CATEGORY>-<YYYYMM>-<NNNNNN>}, for example {@code TRAVEL-202611-000042}. It is what a person
 * quotes on the phone, so it must be unique installation-wide and must not be guessable enough to
 * enumerate — the six-digit sequence is deliberately padded to make accidental similarity less likely
 * without implying a security boundary. The request id remains the real identifier.
 *
 * <p>The format is <strong>provisional</strong>: see OD-P2-05. The BRD states that the reference
 * generation algorithm "should be redesigned and approved", so this is recorded as a pending business
 * decision rather than presented as the final scheme. Changing the format later is a code change, not a
 * data migration, because the number is opaque to every consumer.
 *
 * <h2>Why a database sequence rather than {@code MAX(number)+1}</h2>
 * {@code MAX+1} races: two concurrent submissions read the same maximum and one fails on the unique
 * constraint. A sequence is allocated by PostgreSQL outside the caller's transaction, so concurrent
 * callers cannot collide.
 *
 * <h2>Why the number is not rolled back</h2>
 * {@code nextval} is non-transactional by design. If a submission fails after taking a number, that number
 * is simply never used — a gap. Gaps are the correct trade: reusing a number would mean two different
 * requests could share an identity in someone's inbox, and a gap is trivially ignorable while a duplicate
 * is an audit failure. The sequence is never reset.
 *
 * <h2>The month is the submission month, not the creation month</h2>
 * A draft created in October and submitted in November must be filed under November, because the
 * registers and the finance period it lands in are decided at submission.
 */
@Service
public class RequestNumberGenerator {

	private static final int SEQUENCE_WIDTH = 6;

	@PersistenceContext
	private EntityManager entityManager;

	private final Clock clock;

	public RequestNumberGenerator(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Allocates the next number for a category.
	 *
	 * @param categoryCode the category's configured code; never a name, so a rename does not relabel
	 * historical numbers
	 */
	public String next(String categoryCode) {
		long sequence = nextSequenceValue();
		return format(categoryCode, period(), sequence);
	}

	/**
	 * Allocates the next raw sequence value.
	 *
	 * <p>Exposed so a test can assert monotonicity without parsing the formatted string, and so a future
	 * numbering scheme can reuse the allocator without inheriting this format.
	 */
	public long nextSequenceValue() {
		Number value = (Number) this.entityManager.createNativeQuery("select nextval('expense_request_number_seq')")
			.getSingleResult();
		return value.longValue();
	}

	static String format(String categoryCode, String period, long sequence) {
		String code = (categoryCode == null || categoryCode.isBlank()) ? "REQ" : sanitize(categoryCode);
		return code + "-" + period + "-" + String.format("%0" + SEQUENCE_WIDTH + "d", sequence);
	}

	/**
	 * Restricts the prefix to characters that are unambiguous in a URL, an email subject and a printed
	 * register. A category code containing a space or a slash would produce a number that breaks when
	 * quoted, so it is normalised rather than rejected: the code itself remains the administrator's.
	 */
	private static String sanitize(String raw) {
		String upper = raw.trim().toUpperCase(java.util.Locale.ROOT);
		StringBuilder out = new StringBuilder(upper.length());
		for (char c : upper.toCharArray()) {
			if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-') {
				out.append(c);
			}
		}
		return out.isEmpty() ? "REQ" : out.toString();
	}

	private String period() {
		Instant now = Instant.now(this.clock);
		return YearMonth.from(now.atZone(ZoneOffset.UTC)).toString().replace("-", "");
	}

	/** True when the given text has this generator's shape. Used by tests and by import validation. */
	public static boolean matchesFormat(String candidate) {
		return candidate != null && candidate.matches("[A-Z0-9-]+-\\d{6}-\\d{6,}");
	}

}
