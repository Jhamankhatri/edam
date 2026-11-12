package com.edam.web;

/**
 * View-model attribute names shared by controllers, interceptors and the JSP layouts.
 * Centralized so a typo becomes a compile error instead of a silently blank page element.
 */
public final class ViewConstants {

	/** Value of {@code activeNav} used by the sidebar to highlight the current section. */
	public static final String ACTIVE_NAV = "activeNav";

	public static final String PAGE_TITLE = "pageTitle";

	public static final String PRINCIPAL = "principal";

	/**
	 * Institution branding view model ({@link InstitutionTheme}). Always contributed by
	 * {@link InstitutionViewAttributesInterceptor}; a view must never read configuration
	 * directly.
	 */
	public static final String INSTITUTION_THEME = "institutionTheme";

	/** Request correlation ID, rendered on the error page so users can quote it to support. */
	public static final String CORRELATION_ID = "correlationId";

	public static final String NAV_DASHBOARD = "dashboard";

	private ViewConstants() {
	}

}
