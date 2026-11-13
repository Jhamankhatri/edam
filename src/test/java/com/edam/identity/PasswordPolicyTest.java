package com.edam.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edam.common.exception.BusinessException;
import com.edam.identity.service.PasswordPolicy;
import com.edam.usermanagement.model.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The password policy, exercised as behaviour rather than as a list of getters.
 *
 * <p>These are pure unit tests: no Spring context, no database. The policy is a decision function, and a
 * database-backed test would only make the failure slower to read.
 */
class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy(12, 128, true, true, true, true);

	private static User user() {
		User user = new User();
		user.setUsername("muhammad.ali");
		user.setFirstName("Muhammad");
		user.setLastName("Ali");
		user.setEmail("muhammad.ali@bank.example");
		user.setEmployeeCode("EMP-4471");
		return user;
	}

	@Nested
	@DisplayName("length")
	class Length {

		@Test
		@DisplayName("a password shorter than the minimum is refused")
		void tooShort() {
			assertThatThrownBy(() -> PasswordPolicyTest.this.policy.validate("Ab1!short", null))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("at least 12");
		}

		@Test
		@DisplayName("the minimum length is configurable, and a lower configured value is honoured")
		void configurable() {
			PasswordPolicy permissive = new PasswordPolicy(10, 128, true, true, true, true);
			assertThatCode(() -> permissive.validate("Abcdef1!xy", null)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("a configured minimum below 8 is raised to 8, never silently accepted")
		void floorIsEnforced() {
			// A deployment that configures 4 must not get 4: the policy refuses to be that weak.
			PasswordPolicy weak = new PasswordPolicy(4, 128, false, false, false, false);
			assertThatThrownBy(() -> weak.validate("abcdefg", null)).isInstanceOf(BusinessException.class);
			assertThatCode(() -> weak.validate("abcdefgh", null)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("whitespace is refused, because a trailing space is invisible and unrecoverable")
		void noWhitespace() {
			assertThatThrownBy(() -> PasswordPolicyTest.this.policy.validate("Valid Pass1!word", null))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("spaces");
		}
	}

	@Nested
	@DisplayName("composition")
	class Composition {

		@Test
		@DisplayName("each required character class is enforced and named in the message")
		void requiredClasses() {
			assertThatThrownBy(() -> policy.validate("alllowercase1!", null)).hasMessageContaining("uppercase");
			assertThatThrownBy(() -> policy.validate("ALLUPPERCASE1!", null)).hasMessageContaining("lowercase");
			assertThatThrownBy(() -> policy.validate("NoDigitsHere!!", null)).hasMessageContaining("digit");
			assertThatThrownBy(() -> policy.validate("NoSymbols1234", null)).hasMessageContaining("symbol");
		}

		@Test
		@DisplayName("a compliant password is accepted")
		void compliant() {
			assertThatCode(() -> policy.validate("Str0ng!Passphrase", null)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("composition requirements can be relaxed by configuration for a bank that chooses to")
		void configurableComposition() {
			PasswordPolicy relaxed = new PasswordPolicy(12, 128, false, false, false, false);
			assertThatCode(() -> relaxed.validate("onlylowercase", null)).doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("personal data")
	class PersonalData {

		@Test
		@DisplayName("a password containing the username is refused however strong it otherwise looks")
		void containsUsername() {
			assertThatThrownBy(() -> policy.validate("muhammad.ali-A1!", user()))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("name, username");
		}

		@Test
		@DisplayName("a password containing the first name is refused")
		void containsFirstName() {
			assertThatThrownBy(() -> policy.validate("Muhammad!2026x", user()))
				.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("a password containing the e-mail local part is refused")
		void containsEmailLocalPart() {
			assertThatThrownBy(() -> policy.validate("Xx-muhammad.ali-xX1!", user()))
				.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("a password containing the employee code is refused")
		void containsEmployeeCode() {
			assertThatThrownBy(() -> policy.validate("Str0ng!EMP-4471", user()))
				.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("a password unrelated to the user is accepted")
		void unrelated() {
			assertThatCode(() -> policy.validate("Quiet!Harbour9Tree", user())).doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("common passwords")
	class Common {

		@Test
		@DisplayName("a well-known password is refused even when it satisfies every composition rule")
		void rejectsCommon() {
			// "Password123!" satisfies length and all four classes; it must still be refused.
			assertThatThrownBy(() -> policy.validate("Password123!", null))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("too common");
		}
	}

	@Test
	@DisplayName("the described policy is what the client is told, so a form can validate before submitting")
	void descriptionMatchesPolicy() {
		PasswordPolicy.PasswordPolicyDescription description = this.policy.describe();
		assertThat(description.minimumLength()).isEqualTo(12);
		assertThat(description.requireUppercase()).isTrue();
		assertThat(description.requireSpecial()).isTrue();
	}

}
