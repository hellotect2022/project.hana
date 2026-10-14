package com.mpole.hana.digitaltwin.device.support;

import java.nio.charset.StandardCharsets;

/**
 * CCTV RTSP 스트림 URL 조립 유틸.
 *
 * <p>저장 모델(property_info)에는 자격증명을 박지 않고, CCTV 전용 응답에서 <b>읽을 때</b>
 * 이 유틸로 스트림 URL을 조립한다. 자격증명은 percent-encode 되어 userinfo 로 주입되고,
 * port 는 명시 값(property_info.port)이 있으면 base URL 의 포트를 덮어쓴다.</p>
 *
 * <p>host/path 는 base URL(main_url)을 기준으로 한다(cctv_ip 는 카메라 자체 IP 로 NVR host 와 다를 수 있음).</p>
 */
public final class RtspUrlSupport {

    private RtspUrlSupport() {
    }

    /**
     * @param baseUrl      기준 URL (예: {@code rtsp://host[:port]/path}). 공백이면 null 반환.
     * @param userId       자격증명 ID (공백이면 미주입)
     * @param password     자격증명 PW (공백이면 미주입)
     * @param portOverride 포트 오버라이드 (공백이면 base URL 의 포트 유지)
     * @return 자격증명/포트가 반영된 URL. baseUrl 이 공백이면 {@code null}.
     */
    public static String withCredentials(String baseUrl, String userId, String password, String portOverride) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String url = baseUrl.trim();

        int schemeIdx = url.indexOf("://");
        if (schemeIdx < 0) {
            // 스킴이 없으면 안전하게 원본 반환(조립 불가)
            return url;
        }
        String scheme = url.substring(0, schemeIdx + 3); // "rtsp://"
        String rest = url.substring(schemeIdx + 3);       // host[:port]/path...

        int slash = rest.indexOf('/');
        String authority = slash >= 0 ? rest.substring(0, slash) : rest;
        String path = slash >= 0 ? rest.substring(slash) : "";

        // 기존 userinfo 제거(중복 방지)
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }

        // host / port 분리
        String host = authority;
        String port = null;
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) {
            host = authority.substring(0, colon);
            port = authority.substring(colon + 1);
        }
        if (portOverride != null && !portOverride.isBlank()) {
            port = portOverride.trim();
        }

        StringBuilder sb = new StringBuilder(scheme);
        boolean hasCred = userId != null && !userId.isBlank()
                && password != null && !password.isBlank();
        if (hasCred) {
            sb.append(enc(userId.trim())).append(':').append(enc(password.trim())).append('@');
        }
        sb.append(host);
        if (port != null && !port.isBlank()) {
            sb.append(':').append(port);
        }
        sb.append(path);
        return sb.toString();
    }

    /**
     * userinfo 최소 percent-encode.
     * <p>RFC3986 userinfo 에서 허용되는 unreserved/sub-delims 는 그대로 두고, URL 파싱을 깨는
     * 문자(예: {@code @ : / ? #}, 공백 등)만 percent-encode 한다. 예: {@code cctv!234@ → cctv!234%40}.
     * (일부 카메라가 percent-decode 를 하지 않아도 sub-delims 는 원문 유지되어 안전.)</p>
     */
    private static String enc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean safe = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || "-._~$&'()*+,;=".indexOf(c) >= 0;
            if (safe) {
                b.append(c);
            } else {
                for (byte bb : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    b.append('%').append(Character.toUpperCase(Character.forDigit((bb >> 4) & 0xF, 16)))
                     .append(Character.toUpperCase(Character.forDigit(bb & 0xF, 16)));
                }
            }
        }
        return b.toString();
    }
}
