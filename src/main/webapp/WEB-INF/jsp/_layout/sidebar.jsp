<aside class="edam-sidebar" aria-label="Primary navigation">
    <div class="edam-sidebar__brand">
        <span class="edam-sidebar__mark"><c:out value="${institutionTheme.shortName}"/></span>
        <span class="edam-sidebar__name"><c:out value="${institutionTheme.name}"/></span>
    </div>
    <nav class="edam-nav">
        <a class="edam-nav__item ${activeNav eq 'dashboard' ? 'is-active' : ''}"
           href="<c:url value='/dashboard'/>">Dashboard</a>
        <span class="edam-nav__section">Expenses</span>
        <span class="edam-nav__item is-disabled" title="Delivered in the OPEX phase">OPEX requests</span>
        <span class="edam-nav__item is-disabled" title="Delivered in the CAPEX phase">CAPEX requests</span>
        <span class="edam-nav__section">Administration</span>
        <span class="edam-nav__item is-disabled" title="Delivered in the configuration phase">Delegation matrix</span>
        <span class="edam-nav__item is-disabled" title="Delivered in the configuration phase">Expense Committee</span>
        <span class="edam-nav__item is-disabled" title="Delivered in the configuration phase">Currencies</span>
    </nav>
    <p class="edam-sidebar__note">Foundation release &mdash; business modules arrive in later phases.</p>
</aside>
