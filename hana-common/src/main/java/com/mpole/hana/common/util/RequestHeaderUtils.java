package com.mpole.hana.common.util;

import jakarta.servlet.http.HttpServletRequest;

public class RequestHeaderUtils {
    private RequestHeaderUtils() {}

    // LazyHolder 오프라인 싱글톤 인스턴스 생성
    private static class Holder {
        private static final RequestHeaderUtils INSTANCE = new RequestHeaderUtils();
    }

    public static RequestHeaderUtils getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 클라이언트 실제 IP 추출
     */
    public String getClientIp(HttpServletRequest request) {
        if (request == null) return "Unknown IP";

        String ip = request.getHeader("X-Forwarded-For");

        // X-Forwarded-For는 프록시를 거치면 "클라이언트IP, 프록시1IP, 프록시2IP" 형태로 올 수 있음
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 디바이스 이름 추출 (User-Agent 100자 컷)
     */
    public String resolveDeviceName(HttpServletRequest request) {
        if (request == null) return "Unknown Device";
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 100)) : "Unknown Device";
    }

    /**
     * 디바이스 타입 분석 (PC, MOBILE, TABLET)
     */
    public String resolveDeviceType(HttpServletRequest request) {
        if (request == null) return "PC";
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) return "PC";

        String lower = userAgent.toLowerCase();

        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) {
            return "MOBILE";
        } else if (lower.contains("tablet") || lower.contains("ipad")) { // lower 케이스 변환 적용
            return "TABLET";
        } else {
            return "PC";
        }
    }
}
