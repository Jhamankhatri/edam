<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>

<main class="edam-main">
    <div class="edam-login-wrapper">
        <div class="edam-card">
            <h1 class="edam-card-title">Sign In to <c:out value="${branding.shortName}" /></h1>

            <c:if test="${param.error != null}">
                <div class="edam-alert edam-alert-error" role="alert">
                    Invalid username or password, or your account is locked or disabled.
                </div>
            </c:if>
            <c:if test="${param.logout != null}">
                <div class="edam-alert edam-alert-success" role="status">
                    You have been signed out safely.
                </div>
            </c:if>

            <form method="post" action="<c:url value='/login' />">
                <c:if test="${not empty _csrf}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
                </c:if>

                <div class="edam-form-group">
                    <label class="edam-form-label" for="username">Username</label>
                    <input class="edam-form-input" id="username" name="username" type="text" autocomplete="username" required autofocus>
                </div>

                <div class="edam-form-group">
                    <label class="edam-form-label" for="password">Password</label>
                    <input class="edam-form-input" id="password" name="password" type="password" autocomplete="current-password" required>
                </div>

                <button type="submit" class="edam-btn edam-btn-primary">Sign In</button>
            </form>
        </div>
    </div>
</main>

<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>
