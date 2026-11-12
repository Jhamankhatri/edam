package com.edam.common.security;

import java.util.Optional;

/**
 * Holds the request-scoped {@link InstitutionContext} in a {@link ThreadLocal}.
 * Must always be cleared in a servlet filter {@code finally} block to prevent context leakage across pooled threads.
 */
public final class InstitutionContextHolder {

    private static final ThreadLocal<InstitutionContext> CONTEXT = new ThreadLocal<>();

    private InstitutionContextHolder() {
    }

    public static void set(InstitutionContext context) {
        if (context == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(context);
        }
    }

    public static Optional<InstitutionContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
