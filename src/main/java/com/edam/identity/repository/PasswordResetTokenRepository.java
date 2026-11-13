package com.edam.identity.repository;

import java.util.Optional;
import java.util.UUID;

import com.edam.identity.model.PasswordResetToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Password-reset grants, addressed by token hash so the raw token is never queried. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/**
	 * Invalidates every outstanding grant for a user.
	 *
	 * <p>Called when a reset completes or a password changes. Without it, an attacker who obtained an
	 * earlier link could still redeem it after the legitimate user had already reset — so the older
	 * grant must die with the change it was issued for.
	 */
	@Query("update PasswordResetToken t set t.usedAt = :when where t.userId = :userId and t.usedAt is null")
	int invalidateOutstanding(@Param("userId") UUID userId, @Param("when") java.time.Instant when);

}
