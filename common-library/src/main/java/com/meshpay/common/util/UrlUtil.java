package com.meshpay.common.util;

public final class UrlUtil {

    private UrlUtil() {}

    /**
     * Normalizes a service URL by ensuring it has an appropriate protocol prefix (http:// or https://).
     * If the URL has no scheme:
     * - Hostnames ending in .onrender.com or standard domain names are prefixed with https://
     * - Localhost or internal container names are prefixed with http://
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("localhost") || trimmed.startsWith("127.0.0.1") || !trimmed.contains(".")) {
            return "http://" + trimmed;
        }
        return "https://" + trimmed;
    }
}
