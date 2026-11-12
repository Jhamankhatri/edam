<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>

<div class="edam-shell">
    <%@ include file="/WEB-INF/jsp/layout/sidebar.jspf" %>

    <main class="edam-main">
        <%@ include file="/WEB-INF/jsp/layout/navbar.jspf" %>

        <div class="edam-content">
            <div class="edam-grid">
                <section class="edam-card">
                    <h2 class="edam-card-title">Active Institution Configuration</h2>
                    <dl class="edam-kv-list">
                        <dt>Institution Code</dt>
                        <dd><c:out value="${branding.institutionCode}" /></dd>
                        <dt>Display Name</dt>
                        <dd><c:out value="${branding.name}" /></dd>
                        <dt>Default Currency</dt>
                        <dd><c:out value="${branding.defaultCurrencyCode}" /></dd>
                        <dt>Locale / Zone</dt>
                        <dd><c:out value="${branding.locale}" /> / <c:out value="${branding.timeZone}" /></dd>
                        <dt>Theme Mode</dt>
                        <dd><c:out value="${branding.themeMode}" /></dd>
                    </dl>
                </section>

                <section class="edam-card">
                    <h2 class="edam-card-title">Request Trace &amp; Security Context</h2>
                    <dl class="edam-kv-list">
                        <dt>Correlation ID</dt>
                        <dd><code><c:out value="${correlationId}" /></code></dd>
                        <dt>Authenticated User</dt>
                        <dd><c:out value="${activeUsername}" default="anonymous" /></dd>
                        <dt>Context Code</dt>
                        <dd><c:out value="${activeInstitutionCode}" default="PLATFORM" /></dd>
                        <dt>Packaging</dt>
                        <dd>Executable WAR (Embedded Tomcat + Jasper JSP)</dd>
                    </dl>
                </section>
            </div>

            <section class="edam-card">
                <h2 class="edam-card-title">Operational Health Verification</h2>
                <p>Verify live health-check status and correlation header propagation via the REST health endpoint.</p>
                <button id="edam-health-check-btn"
                        type="button"
                        class="edam-btn edam-btn-primary"
                        data-health-url="<c:url value='/api/v1/health' />">
                    Check Platform Health
                </button>
                <pre id="edam-health-output" class="edam-status-box">Click "Check Platform Health" to probe /api/v1/health.</pre>
            </section>
        </div>
    </main>
</div>

<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>
