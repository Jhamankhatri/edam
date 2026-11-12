package com.edam.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Jakarta Bean Validation validator for {@link ValidHexColor}.
 */
public class HexColorValidator implements ConstraintValidator<ValidHexColor, String> {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return HEX_COLOR_PATTERN.matcher(value).matches();
    }

    /**
     * Returns {@code candidate} if it matches strict {@code #RRGGBB} format; otherwise {@code fallback}.
     */
    public static String sanitizeOrDefault(String candidate, String fallback) {
        if (candidate != null && HEX_COLOR_PATTERN.matcher(candidate).matches()) {
            return candidate;
        }
        return fallback;
    }
}
