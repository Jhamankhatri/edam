<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Sign in"/>
<%@ include file="_layout/head.jsp" %>
<%@ include file="_layout/theme.jsp" %>
<body class="edam-auth-body">
<main class="edam-auth">
    <h1 class="edam-auth__brand"><c:out value="${institutionTheme.shortName}"/></h1>
    <p class="edam-auth__institution"><c:out value="${institutionTheme.name}"/></p>

    <c:if test="${param.error ne null}">
        <div class="edam-alert edam-alert--error" role="alert">Sign-in failed. Check your credentials.</div>
    </c:if>
    <c:if test="${param.logout ne null}">
        <div class="edam-alert" role="status">You have signed out.</div>
    </c:if>

    <form method="post" action="<c:url value='/login'/>" class="edam-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <label for="username">Username</label>
        <input id="username" name="username" type="text" autocomplete="username" required autofocus>
        <label for="password">Password</label>
        <input id="password" name="password" type="password" autocomplete="current-password" required>
        <button type="submit" class="edam-button edam-button--primary">Sign in</button>
    </form>

    <p class="edam-muted">
        Authentication is not yet integrated with an institutional identity provider. The
        development profile provides fixture accounts; see SECURITY_GUIDELINES.md.
    </p>
</main>
</body>
</html>
