package com.edam.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

final class SecurityRequestSupport {

    private SecurityRequestSupport() {
    }

    static boolean isApiOrJsonRequest(HttpServletRequest request, String sanitizedPath) {
        String accept = request.getHeader("Accept");
        return sanitizedPath.startsWith("/api/")
                || (accept != null
                && accept.contains(MediaType.APPLICATION_JSON_VALUE)
                && !accept.contains(MediaType.TEXT_HTML_VALUE));
    }
}
