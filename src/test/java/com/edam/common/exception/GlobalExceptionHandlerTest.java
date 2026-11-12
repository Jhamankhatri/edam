package com.edam.common.exception;

import java.util.List;

import com.edam.common.api.ApiResponse;
import com.edam.common.api.ErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Error envelopes must be safe by construction: correct status, stable code, correlation ID,
 * and no internal detail leaking into the payload.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	private static MockHttpServletRequest apiRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/expense/requests");
		request.addHeader("Accept", "application/json");
		return request;
	}

	@Test
	@DisplayName("business exceptions map to 422 with their safe message")
	void businessException() {
		ResponseEntity<ApiResponse<Void>> response = this.handler
			.handleEdamException(new BusinessException("Approval amount must be greater than zero."), apiRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isFalse();
		assertThat(response.getBody().error().code()).isEqualTo("EDAM-3001");
		assertThat(response.getBody().error().message()).isEqualTo("Approval amount must be greater than zero.");
		assertThat(response.getBody().path()).isEqualTo("/api/v1/expense/requests");
		assertThat(response.getBody().correlationId()).isNotBlank();
	}

	@Test
	@DisplayName("not-found exceptions map to 404 without revealing existence of other tenants' data")
	void notFound() {
		ResponseEntity<ApiResponse<Void>> response = this.handler
			.handleEdamException(new ResourceNotFoundException("Expense request", 123L), apiRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().error().code()).isEqualTo("EDAM-4001");
		assertThat(response.getBody().error().message()).isEqualTo("Expense request '123' was not found.");
	}

	@Test
	@DisplayName("validation failures return field errors but never echo rejected values")
	void validationFailure() {
		ValidationFailedException exception = new ValidationFailedException(
				List.of(new ApiResponse.FieldErrorDetail("approvalAmount", "must be greater than 0")));

		ResponseEntity<ApiResponse<Void>> response = this.handler.handleValidationFailed(exception, apiRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().fieldErrors()).containsExactly(
				new ApiResponse.FieldErrorDetail("approvalAmount", "must be greater than 0"));
		assertThat(response.getBody().error().code()).isEqualTo("EDAM-1001");
	}

	@Test
	@DisplayName("optimistic-locking conflicts map to 409 without naming internal entities")
	void optimisticLockConflict() {
		ResponseEntity<ApiResponse<Void>> response = this.handler.handleOptimisticLock(
				new org.springframework.dao.OptimisticLockingFailureException("ExpenseRequest"),
				apiRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().error().code()).isEqualTo("EDAM-3002");
		assertThat(response.getBody().error().message()).doesNotContain("ExpenseRequest");
	}

	@Test
	@DisplayName("integrity violations are logged in full but never returned")
	void dataIntegrityViolation() {
		DataIntegrityViolationException exception = new DataIntegrityViolationException(
				"could not execute statement; SQL [insert into institution ...]; constraint [uk_institution_code]",
				new IllegalStateException("duplicate key value violates unique constraint \"uk_institution_code\""));

		ResponseEntity<ApiResponse<Void>> response = this.handler.handleDataIntegrity(exception, apiRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().error().code()).isEqualTo("EDAM-5001");
		assertThat(response.getBody().error().message()).doesNotContain("SQL").doesNotContain("institution")
			.doesNotContain("uk_institution_code");
	}

	@Test
	@DisplayName("database outages map to 503, not 500, so clients know to retry")
	void dataAccessFailure() {
		ResponseEntity<ApiResponse<Void>> response = this.handler
			.handleDataAccess(new QueryTimeoutException("statement timeout"), apiRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody().error().code()).isEqualTo("EDAM-5001");
	}

	@Test
	@DisplayName("unexpected errors return only a code and the correlation id")
	void unexpectedError() {
		ResponseEntity<ApiResponse<Void>> response = this.handler
			.handleUnexpected(new NullPointerException("sensitive internals"), apiRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.code());
		assertThat(response.getBody().error().message()).doesNotContain("sensitive internals")
			.doesNotContain("NullPointerException");
	}

	@Test
	@DisplayName("error responses are marked no-store")
	void noStore() {
		ResponseEntity<ApiResponse<Void>> response = this.handler
			.handleUnexpected(new RuntimeException("boom"), apiRequest());

		assertThat(response.getHeaders().getCacheControl()).contains("no-store");
	}

}
