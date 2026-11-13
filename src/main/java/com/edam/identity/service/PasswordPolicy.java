package com.edam.identity.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.usermanagement.model.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The password rules this installation enforces (Module C).
 *
 * <h2>Why the policy is configurable and not a hardcoded regex</h2>
 * Password length and composition are a bank's information-security policy, not a product opinion.
 * A bank that mandates 14 characters and a bank that mandates 12 should not require a code change.
 * Defaults are deliberately strict rather than convenient.
 *
 * <h2>What is deliberately NOT enforced</h2>
 * No forced rotation and no "must differ from the last N passwords" history by default. Both are
 * current-guidance-controversial: rotation drives predictable incrementing, and the history is what a
 * real deployment adds only if its own policy demands it. Neither is invented here. A bank that needs
 * them can add them without changing this class's contract.
 *
 * <h2>Checks that are not length-based</h2>
 * A password containing the user's own username, e-mail local part, or employee code is rejected
 * regardless of length. An attacker who knows the target's name finds "Muhammad.Ali-2026" trivially;
 * length alone would pass it.
 */
@Component
public class PasswordPolicy {

	/** Bounded iteration cap: protects against a pathological rule set from a misconfiguration. */
	private static final int MAX_INPUT_LENGTH = 200;

	private final int minimumLength;

	private final int maximumLength;

	private final boolean requireUppercase;

	private final boolean requireLowercase;

	private final boolean requireDigit;

	private final boolean requireSpecial;

	/** A short list rather than an exhaustive dictionary; the point is to reject the obvious. */
	private static final Set<String> COMMON_PASSWORDS = Set.of("password", "password1", "password123", "12345678",
			"123456789", "qwerty123", "admin123", "letmein", "welcome1", "changeme", "passw0rd", "iloveyou",
			"abc12345", "monkey123", "dragon123", "administrator");

	public PasswordPolicy(@Value("${edam.security.password.min-length:12}") int minimumLength,
			@Value("${edam.security.password.max-length:128}") int maximumLength,
			@Value("${edam.security.password.require-uppercase:true}") boolean requireUppercase,
			@Value("${edam.security.password.require-lowercase:true}") boolean requireLowercase,
			@Value("${edam.security.password.require-digit:true}") boolean requireDigit,
			@Value("${edam.security.password.require-special:true}") boolean requireSpecial) {
		this.minimumLength = Math.max(8, minimumLength);
		this.maximumLength = Math.max(this.minimumLength, maximumLength);
		this.requireUppercase = requireUppercase;
		this.requireLowercase = requireLowercase;
		this.requireDigit = requireDigit;
		this.requireSpecial = requireSpecial;
	}

	/**
	 * Describes the policy for the frontend, so a React form can show the rules before submitting
	 * rather than after failing. Contains no secret and nothing environment-specific beyond limits.
	 */
	public PasswordPolicyDescription describe() {
		return new PasswordPolicyDescription(this.minimumLength, this.maximumLength, this.requireUppercase,
				this.requireLowercase, this.requireDigit, this.requireSpecial);
	}

	/**
	 * Validates a candidate password and throws with a message that states which rule failed.
	 *
	 * <p>The message names the rule, never the value. Logging or echoing a rejected password would put a
	 * near-miss credential in a log file, which is exactly what an attacker with log access wants.
	 *
	 * @param raw the candidate password
	 * @param user context for the personal-data checks; {@code null} during bootstrap before a user exists
	 */
	public void validate(String raw, User user) {
		if (raw == null || raw.isEmpty()) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD, "A password is required.");
		}
		if (raw.length() < this.minimumLength) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD,
					"The password must be at least " + this.minimumLength + " characters long.");
		}
		if (raw.length() > MAX_INPUT_LENGTH || raw.length() > this.maximumLength) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD,
					"The password must be at most " + Math.min(MAX_INPUT_LENGTH, this.maximumLength) + " characters long.");
		}
		if (raw.chars().anyMatch(Character::isWhitespace)) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD, "The password must not contain spaces.");
		}
		if (this.requireUppercase && raw.chars().noneMatch(Character::isUpperCase)) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD, "The password must contain an uppercase letter.");
		}
		if (this.requireLowercase && raw.chars().noneMatch(Character::isLowerCase)) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD, "The password must contain a lowercase letter.");
		}
		if (this.requireDigit && raw.chars().noneMatch(Character::isDigit)) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD, "The password must contain a digit.");
		}
		if (this.requireSpecial && raw.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD, "The password must contain a symbol.");
		}
		if (COMMON_PASSWORDS.contains(raw.toLowerCase(Locale.ROOT))) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD, "That password is too common to be used.");
		}
		if (user != null && containsPersonalData(raw, user)) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD,
					"The password must not contain your name, username, employee code or e-mail address.");
		}
	}

	private boolean containsPersonalData(String raw, User user) {
		String candidate = raw.toLowerCase(Locale.ROOT);
		List<String> fragments = new java.util.ArrayList<>();
		fragments.add(user.getUsername());
		fragments.add(user.getEmail());
		fragments.add(user.getEmployeeCode());
		if (user.getEmail() != null) {
			fragments.add(user.getEmail().split("@")[0]);
		}
		fragments.add(user.getFirstName());
		fragments.add(user.getLastName());
		for (String fragment : fragments) {
			// Two-character fragments would reject almost everything; only meaningful parts are checked.
			if (fragment != null && fragment.length() >= 3 && candidate.contains(fragment.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	/** The policy, as a DTO the login/registration UI can render. */
	public record PasswordPolicyDescription(int minimumLength, int maximumLength, boolean requireUppercase,
			boolean requireLowercase, boolean requireDigit, boolean requireSpecial) {
	}

}
