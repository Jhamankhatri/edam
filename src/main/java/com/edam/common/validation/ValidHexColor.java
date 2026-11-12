package com.edam.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string represents a strict 6-digit hexadecimal CSS color (e.g. {@code #1E3A5F}).
 * Prevents CSS injection through configurable institution branding properties.
 */
@Documented
@Constraint(validatedBy = HexColorValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidHexColor {

    String message() default "must be a valid 6-digit hex color code (e.g. #1E3A5F)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
