<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="_layout/header.jsp" %>

<section class="edam-hero">
    <h1>Welcome, <c:out value="${principal.displayName}"/></h1>
    <p>
        <c:out value="${institutionTheme.name}"/> &mdash; Expense Delegation and Approval Management.
        This release provides the platform foundation only; expense, approval, committee and
        reporting capabilities are delivered in subsequent phases.
    </p>
</section>

<c:if test="${institutionSelectionRequired}">
    <div class="edam-notice" role="status">
        <strong>Institution selection required.</strong>
        Your account belongs to more than one institution. Select the institution you are acting
        for before opening institution-scoped records. EDAM never assumes a single institution.
    </div>
</c:if>

<section class="edam-grid">
    <article class="edam-card">
        <h2>Session</h2>
        <dl class="edam-facts">
            <dt>Employee</dt><dd><c:out value="${principal.employeeId}"/></dd>
            <dt>Institutions</dt><dd><c:out value="${principal.institutionCodes}"/></dd>
            <dt>Current institution</dt>
            <dd><c:out value="${empty currentInstitution ? 'not selected' : currentInstitution}"/></dd>
            <dt>Base currency</dt><dd><c:out value="${baseCurrency}"/></dd>
        </dl>
    </article>

    <article class="edam-card">
        <h2>Permissions</h2>
        <ul class="edam-tag-list">
            <c:forEach var="permission" items="${principal.permissions}">
                <li class="edam-tag"><c:out value="${permission}"/></li>
            </c:forEach>
        </ul>
        <p class="edam-muted">
            Permissions are enforced on the server. Hiding a control is never an authorization
            control.
        </p>
    </article>

    <article class="edam-card">
        <h2>Configured institutions</h2>
        <c:choose>
            <c:when test="${empty institutions}">
                <p class="edam-muted">No institution records are loaded in this environment.</p>
            </c:when>
            <c:otherwise>
                <table class="edam-table">
                    <thead>
                    <tr><th>Code</th><th>Name</th><th>Base currency</th><th>Status</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="institution" items="${institutions}">
                        <tr>
                            <td><c:out value="${institution.code}"/></td>
                            <td><c:out value="${institution.name}"/></td>
                            <td><c:out value="${institution.baseCurrency}"/></td>
                            <td><c:out value="${institution.active ? 'Active' : 'Inactive'}"/></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </c:otherwise>
        </c:choose>
    </article>

    <article class="edam-card">
        <h2>Platform foundation</h2>
        <ul class="edam-list">
            <li>Correlation ID on every request, response and log line</li>
            <li>Standardized API response envelope and error codes</li>
            <li>Centralized exception handling for API and JSP flows</li>
            <li>Flyway-managed PostgreSQL schema with JPA auditing</li>
            <li>Health probes at <code>/actuator/health</code></li>
        </ul>
    </article>
</section>

<%@ include file="_layout/footer-layout.jsp" %>
