package com.edam.identity.service;

import java.time.Instant;

import com.edam.identity.model.LoginAttempt;
import com.edam.identity.repository.LoginAttemptRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the evidence of a failed sign-in, in a transaction of its own.
 *
 * <h2>Why this is a separate bean rather than a method on {@code AuthService}</h2>
 * This is the single most subtle correctness point in the authentication code, and it was found by a
 * test rather than by reading.
 *
 * <p>The failure path must <em>commit</em> the incremented counter and then reject the caller. Calling
 * {@code save()} inside a transactional method that subsequently throws rolls that save back, because a
 * {@code BusinessException} is a runtime exception and the transaction manager marks the transaction
 * rollback-only. The consequence is severe and silent: the failure counter never increments, the lockout
 * threshold is never reached, and brute-force protection is decorative while every line of code suggests
 * it works.
 *
 * <p>{@code REQUIRES_NEW} on this bean's methods starts an independent transaction that commits on
 * normal return. {@code AuthService} calls it, gets a committed record, and only then throws. Spring's
 * {@code @Transactional} proxy does not apply to self-invocation, which is precisely why this cannot be
 * a private method on {@code AuthService} — it has to be a separate bean for the propagation to take
 * effect.
 */
@Service
public class LoginAttemptRecorder {

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptRecorder.class);

	private final UserRepository users;

	private final LoginAttemptRepository attempts;

	private final int lockThreshold;

	private final java.time.Duration lockDuration;

	public LoginAttemptRecorder(UserRepository users, LoginAttemptRepository attempts,
			@Value("${edam.security.lockout.threshold:5}") int lockThreshold,
			@Value("${edam.security.lockout.duration:PT15M}") java.time.Duration lockDuration) {
		this.users = users;
		this.attempts = attempts;
		this.lockThreshold = lockThreshold;
		this.lockDuration = lockDuration;
	}

	/**
	 * Records one failed attempt and, when the threshold is reached, engages the lock.
	 *
	 * <p>Commits independently of the caller. The user row is re-read inside this transaction so the
	 * increment is applied to current state rather than to a possibly stale instance.
	 *
	 * @param userId the account, or {@code null} when no such user exists
	 * @param username the attempted name, recorded even when unknown — that is the case an operator most
	 *        needs to see when investigating an attack
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(java.util.UUID userId, String username, String ipAddress, String reason, Instant now) {
		String recorded = (username == null) ? "" : (username.length() > 100 ? username.substring(0, 100) : username);
		this.attempts.save(new LoginAttempt(recorded, ipAddress, false, reason, now));
		if (userId == null) {
			return;
		}
		this.users.findById(userId).ifPresent(user -> {
			user.recordFailedLogin(this.lockThreshold, this.lockDuration, now);
			this.users.save(user);
			if (user.getLockedUntil() != null) {
				log.warn("auth.account_locked userId={} until={}", userId, user.getLockedUntil());
			}
		});
	}

	/**
	 * Records a successful attempt and clears the account's failure state, in one committed transaction.
	 *
	 * <p>Clearing the counter belongs in this transaction rather than in the caller: it is the exact
	 * inverse of {@link #recordFailure}, and keeping both on the same bean means the write that clears it
	 * cannot be rolled back either.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordSuccess(java.util.UUID userId, String username, String ipAddress, Instant now) {
		String recorded = (username == null) ? "" : (username.length() > 100 ? username.substring(0, 100) : username);
		this.attempts.save(new LoginAttempt(recorded, ipAddress, true, null, now));
		if (userId == null) {
			return;
		}
		this.users.findById(userId).ifPresent(user -> {
			user.clearFailedLogins();
			user.setLastLoginAt(now);
			this.users.save(user);
		});
	}

}
