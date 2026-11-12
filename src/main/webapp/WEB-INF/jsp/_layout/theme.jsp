<%--
  Institution theme tokens, supplied by configuration (InstitutionViewAttributesInterceptor) rather than
  hard-coded markup so re-branding a tenant never requires a code change (BRD 15.4).
--%>
<style>
    :root {
        --edam-primary: <c:out value="${institutionTheme.primaryColor}"/>;
        --edam-secondary: <c:out value="${institutionTheme.secondaryColor}"/>;
        --edam-accent: <c:out value="${institutionTheme.accentColor}"/>;
    }
</style>
