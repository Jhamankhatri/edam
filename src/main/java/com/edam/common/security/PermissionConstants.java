package com.edam.common.security;

/**
 * Neutral platform capability permission codes.
 * Institution-specific business roles are configured per institution in the database
 * rather than hardcoding bank-specific role names in application code.
 */
public final class PermissionConstants {

    public static final String PLATFORM_HEALTH_VIEW = "PLATFORM_HEALTH_VIEW";
    public static final String INSTITUTION_CONFIG_VIEW = "INSTITUTION_CONFIG_VIEW";
    public static final String INSTITUTION_CONFIG_MANAGE = "INSTITUTION_CONFIG_MANAGE";
    public static final String IDENTITY_USER_VIEW = "IDENTITY_USER_VIEW";
    public static final String IDENTITY_USER_MANAGE = "IDENTITY_USER_MANAGE";
    public static final String AUDIT_LOG_VIEW = "AUDIT_LOG_VIEW";

    private PermissionConstants() {
    }
}
