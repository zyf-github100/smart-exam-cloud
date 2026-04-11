package com.smart.exam.common.web.audit;

import jakarta.servlet.http.HttpServletRequest;

public final class AuditHttpUtils {

    private AuditHttpUtils() {
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String fromHeader = request.getHeader("X-Forwarded-For");
        if (fromHeader != null && !fromHeader.isBlank()) {
            String[] parts = fromHeader.split(",");
            return parts[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String extractUserAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
