package com.edam.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.identity.model.LoginAttempt;

import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only authentication evidence. */
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

	List<LoginAttempt> findTop20ByUsernameOrderByAttemptedAtDesc(String username);

	/** Failed attempts for a username since a cutoff. Backs investigation and lockout analysis. */
	long countByUsernameAndSuccessFalseAndAttemptedAtAfter(String username, Instant since);

}
