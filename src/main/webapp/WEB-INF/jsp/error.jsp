<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Error"/>
<%@ include file="_layout/head.jsp" %>
<%@ include file="_layout/theme.jsp" %>
<body class="edam-auth-body">
<main class="edam-auth edam-auth--message">
    <p class="edam-auth__code"><c:out value="${statusCode}"/></p>
    <h1 class="edam-auth__institution"><c:out value="${statusReason}"/></h1>
    <p><c:out value="${statusMessage}"/></p>
    <c:if test="${not empty correlationId}">
        <p class="edam-muted">Reference: <code><c:out value="${correlationId}"/></code></p>
    </c:if>
    <p><a class="edam-button edam-button--ghost" href="<c:url value='/dashboard'/>">Return to dashboard</a></p>
</main>
</body>
</html>
