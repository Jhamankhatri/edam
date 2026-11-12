package com.edam.common.exception;

import java.util.ArrayList;
import java.util.List;

import com.edam.common.api.ApiResponse.FieldErrorDetail;
import com.edam.common.api.ErrorCode;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * Request payload failed Bean Validation or manual binding validation.
 *
 * <p>Only field paths and validation messages are retained. The rejected values are not
 * stored, logged or returned, because they may contain confidential financial data.
 */
public class ValidationFailedException extends EdamException {

	private final transient List<FieldErrorDetail> fieldErrors;

	public ValidationFailedException(List<FieldErrorDetail> fieldErrors) {
		super(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.safeMessage());
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	public ValidationFailedException(String field, String message) {
		this(List.of(new FieldErrorDetail(field, message)));
	}

	public List<FieldErrorDetail> getFieldErrors() {
		return this.fieldErrors;
	}

	/**
	 * Adapts Spring's {@link BindingResult} to the transport-neutral field error model,
	 * dropping the rejected values.
	 */
	public static ValidationFailedException from(BindingResult bindingResult) {
		List<FieldErrorDetail> details = new ArrayList<>();
		for (ObjectError error : bindingResult.getAllErrors()) {
			if (error instanceof FieldError fieldError) {
				details.add(new FieldErrorDetail(fieldError.getField(),
						fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "is invalid"));
			}
			else {
				details.add(new FieldErrorDetail(error.getObjectName(),
						error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid"));
			}
		}
		return new ValidationFailedException(details);
	}

}
