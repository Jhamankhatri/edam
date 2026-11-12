<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="<c:out value='${institutionTheme.languageTag}'/>">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${pageTitle}"/> &middot; <c:out value="${institutionTheme.shortName}"/></title>
    <link rel="icon" href="<c:url value='/favicon.ico'/>" sizes="32x32">
    <link rel="stylesheet" href="<c:url value='/css/edam.css'/>">
</head>
