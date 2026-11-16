package com.edam.communication;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.edam.communication.model.NotificationOutbox;
import com.edam.communication.model.OutboxStatus;
import com.edam.communication.repository.NotificationOutboxRepository;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the transactional-outbox contract holds: a rolled-back business transaction
 * does not leave orphan outbox rows. Uses a raw row insert in a nested transaction that is
 * deliberately setRollbackOnly, then asserts zero rows remain.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxTransactionTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired private NotificationOutboxRepository outbox;
	@Autowired private TransactionTemplate tx;

	@Test
	void rolledBackTransactionDoesNotPersistOutboxRows() {
		long before = outbox.count();
		tx.execute(status -> {
			NotificationOutbox row = newRow("ROLLBACK_TX_TEST");
			row.setStatus(OutboxStatus.PENDING);
			outbox.save(row);
			status.setRollbackOnly();
			return null;
		});
		long after = outbox.count();
		assertThat(after).isEqualTo(before);
	}

	@Test
	void committedTransactionPersistsOutboxRows() {
		long before = outbox.count();
		UUID id = tx.execute(status -> {
			NotificationOutbox row = newRow("COMMIT_TX_TEST");
			row.setStatus(OutboxStatus.PENDING);
			return outbox.save(row).getId();
		});
		assertThat(outbox.findById(id)).isPresent();
		outbox.deleteById(id);
		assertThat(outbox.count()).isEqualTo(before);
	}

	private NotificationOutbox newRow(String key) {
		NotificationOutbox r = new NotificationOutbox();
		r.setIdempotencyKey(key + ":" + UUID.randomUUID());
		r.setEventCode("TEST");
		r.setChannel(NotificationOutbox.Channel.IN_APP);
		// outbox.user_id is nullable (test email delivery, system notifications)
		r.setSubject("sub");
		r.setBodyText("body");
		r.setAttempts(0);
		r.setMaxAttempts(3);
		r.setNextAttemptAt(Instant.now(Clock.systemUTC()));
		return r;
	}
}
