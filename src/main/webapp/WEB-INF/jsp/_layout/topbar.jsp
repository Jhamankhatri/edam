<header class="edam-topbar">
    <div class="edam-topbar__title"><c:out value="${pageTitle}"/></div>
    <div class="edam-topbar__actions">
        <c:if test="${not empty currentInstitution}">
            <span class="edam-chip" title="Institution context for this session">
                <c:out value="${currentInstitution}"/>
            </span>
        </c:if>
        <c:if test="${not empty principal}">
            <span class="edam-topbar__user"><c:out value="${principal.displayName}"/></span>
            <form method="post" action="<c:url value='/logout'/>" class="edam-inline-form">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <button type="submit" class="edam-button edam-button--ghost">Sign out</button>
            </form>
        </c:if>
    </div>
</header>
