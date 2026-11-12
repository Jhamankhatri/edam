<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>

<main class="edam-main">
    <div class="edam-content">
        <section class="edam-card">
            <h1 class="edam-card-title">Access Denied (403)</h1>
            <p><c:out value="${errorMessage}" default="You do not have permission to access this resource." /></p>
            <dl class="edam-kv-list">
                <dt>Correlation ID</dt>
                <dd><code><c:out value="${correlationId}" default="none" /></code></dd>
            </dl>
            <p>
                <a class="edam-btn edam-btn-primary" href="<c:url value='/' />">Return to Overview</a>
            </p>
        </section>
    </div>
</main>

<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>
