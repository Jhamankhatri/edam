<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>

<main class="edam-main">
    <div class="edam-content">
        <section class="edam-card">
            <h1 class="edam-card-title">
                Request Could Not Be Completed (<c:out value="${statusCode}" default="500" />)
            </h1>
            <p><c:out value="${errorMessage}" default="An unexpected application error occurred." /></p>
            <dl class="edam-kv-list">
                <dt>Error Code</dt>
                <dd><code><c:out value="${errorCode}" default="EDAM-SYS-500" /></code></dd>
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
