package com.meshpay.common.util;

public final class UrlUtil {

    private UrlUtil() {}

    /**
     * Normalizes a service URL by ensuring it has an appropriate protocol prefix (http:// or https://)
     * and a fully qualified domain name for cloud platforms like Render.
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();

        // Handle local development
        if (trimmed.startsWith("localhost") || trimmed.startsWith("127.0.0.1")) {
            return "http://" + trimmed;
        }

        // Handle internal Docker Compose service:port (e.g. "bank-service:8085")
        if (trimmed.contains(":") && !trimmed.contains(".")) {
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
            return "http://" + trimmed;
        }

        // Strip existing protocol for inspection
        String host = trimmed.replaceFirst("^https?://", "");

        // If it's a bare Render internal service slug (e.g. "meshpay-bank-service-rs49")
        if (!host.contains(".")) {
            return "https://" + host + ".onrender.com";
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        return "https://" + trimmed;
    }
}
